package co.paralleluniverse.fibers.instrument;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static co.paralleluniverse.fibers.instrument.ByteCodeFileCacheUnixTest.getAllFiles;
import static co.paralleluniverse.fibers.instrument.ByteCodeFileCacheUnixTest.toHex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteCodeFileCacheWindowsTest {
    private static final Log LOG = new TestLogger(LoggerFactory.getLogger(ByteCodeFileCacheUnixTest.class));
    private static final String TEST_CLASS_NAME = "org.testing.WindowsExample";
    private static final String ALGORITHM_NAME = "SHA-256";
    private static final Random RANDOM = new Random();
    private static final String CACHE_ROOT = "C:\\cache";
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

    private static DosFileAttributes getDosFileAttributes(DosFileAttributeView view) {
        assertThat(view).isNotNull();
        try {
            return view.readAttributes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static Condition<DosFileAttributes> readOnly(boolean expected) {
        return new Condition<>(actual -> actual.isReadOnly() == expected, "read-only=%b", expected);
    }

    private static String absoluteWindowsPathOf(byte[] source) throws NoSuchAlgorithmException {
        assertThat(source).hasSizeGreaterThanOrEqualTo(2);
        final byte[] bytes = hashOf(source);
        return CACHE_ROOT + '\\'
            + toHex(bytes[0]) + '\\'
            + toHex(bytes[1]) + '\\'
            + toHex(Arrays.copyOfRange(bytes, 2, bytes.length))
            + '.' + Integer.toHexString(source.length);
    }

    @Before
    public void setup() throws NoSuchAlgorithmException, IOException {
        Configuration dos = Configuration.windows().toBuilder()
            .setAttributeViews("basic", "dos")
            .build();
        fileSystem = Jimfs.newFileSystem("dos", dos);
        cacheDirectory = Files.createDirectory(fileSystem.getPath(CACHE_ROOT));
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
        assertThat(Files.getFileAttributeView(cacheFile, DosFileAttributeView.class))
            .extracting(ByteCodeFileCacheWindowsTest::getDosFileAttributes)
            .has(readOnly(true));
        assertEquals(absoluteWindowsPathOf(source), cacheFile.toString());

        // And we can retrieve this entry from the cache.
        final byte[] cachedResult = cache.computeIfAbsent(TEST_CLASS_NAME, source, () -> fail("Not allowed"));
        assertArrayEquals(target, cachedResult);
    }
}
