package co.paralleluniverse.fibers.instrument;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteCodeFileCacheUnixTest {
    private static final Log LOG = new TestLogger(LoggerFactory.getLogger(ByteCodeFileCacheUnixTest.class));
    private static final FileAttribute<?> OWNER_ONLY = asFileAttribute(Set.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE));
    private static final String TEST_CLASS_NAME = "org.testing.UnixExample";
    private static final String ALGORITHM_NAME = "SHA-256";
    private static final Random RANDOM = new Random();
    private static final String CACHE_ROOT = "cache";
    private static final int ARRAY_SIZE = 1024;

    private FileSystem fileSystem;
    private ByteCodeCache cache;
    private Path cacheDirectory;

    private static byte[] createRandomBytes() {
        final byte[] bytes = new byte[ARRAY_SIZE];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] hashOf(byte[] bytes) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(ALGORITHM_NAME).digest(bytes);
    }

    static String toHex(byte b) {
        return String.format("%02x", Byte.toUnsignedInt(b));
    }

    static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(toHex(b));
        }
        return builder.toString();
    }

    static List<Path> getAllFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).collect(toUnmodifiableList());
        }
    }

    private static String absoluteUnixPathOf(byte[] source) throws NoSuchAlgorithmException {
        assertThat(source).hasSizeGreaterThanOrEqualTo(2);
        final byte[] bytes = hashOf(source);
        return CACHE_ROOT + '/'
            + toHex(bytes[0]) + '/'
            + toHex(bytes[1]) + '/'
            + toHex(Arrays.copyOfRange(bytes, 2, bytes.length))
            + '.' + Integer.toHexString(source.length);
    }

    @Before
    public void setup() throws IOException, NoSuchAlgorithmException {
        Configuration posix = Configuration.unix().toBuilder()
            .setAttributeViews("basic", "posix")
            .build();
        fileSystem = Jimfs.newFileSystem("posix", posix);

        cacheDirectory = Files.createDirectory(fileSystem.getPath(CACHE_ROOT), OWNER_ONLY);
        cache = new ByteCodeFileCache(ByteCodeCache.createKeyFactory(ALGORITHM_NAME), cacheDirectory, LOG);
    }

    @After
    public void done() throws IOException {
        fileSystem.close();
    }

    @Test
    public void testNewFileIsCached() throws Exception {
        final byte[] source = createRandomBytes();
        final byte[] target = new byte[ARRAY_SIZE];
        Arrays.fill(target, (byte) 0x7b);
        final byte[] result = cache.computeIfAbsent(TEST_CLASS_NAME, source, () -> target);
        assertArrayEquals(target, result);

        // One file has been created in the cache.
        final List<Path> classes = getAllFiles(cacheDirectory);
        assertThat(classes).hasSize(1);

        // This file has the correct contents and properties.
        final Path cacheFile = classes.get(0);
        assertArrayEquals(target, Files.readAllBytes(cacheFile));
        assertThat(Files.getPosixFilePermissions(cacheFile)).containsExactlyInAnyOrder(OWNER_READ);
        assertEquals(absoluteUnixPathOf(source), cacheFile.toString());

        // And its parent directories have the correct permissions.
        assertThat(Files.getPosixFilePermissions(cacheFile.getParent()))
            .containsExactlyInAnyOrder(OWNER_READ, OWNER_EXECUTE, OWNER_WRITE, GROUP_READ, GROUP_EXECUTE, OTHERS_EXECUTE, OTHERS_READ);
        assertThat(Files.getPosixFilePermissions(cacheFile.getParent().getParent()))
            .containsExactlyInAnyOrder(OWNER_READ, OWNER_EXECUTE, OWNER_WRITE, GROUP_READ, GROUP_EXECUTE, OTHERS_EXECUTE, OTHERS_READ);

        // And we can retrieve this entry from the cache.
        final byte[] cachedResult = cache.computeIfAbsent(TEST_CLASS_NAME, source, () -> fail("Not allowed."));
        assertArrayEquals(target, cachedResult);
    }
}
