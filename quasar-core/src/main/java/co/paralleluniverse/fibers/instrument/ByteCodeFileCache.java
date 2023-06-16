package co.paralleluniverse.fibers.instrument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;
import java.util.function.Supplier;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;
import static java.security.AccessController.doPrivileged;
import static java.util.Collections.singleton;

@SuppressWarnings("removal")
final class ByteCodeFileCache implements ByteCodeCache {
    private static final FileAttribute<?> RWX_RX_RX_ATTR = asFileAttribute(Set.of(
        OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
        GROUP_READ, GROUP_EXECUTE,
        OTHERS_READ, OTHERS_EXECUTE
    ));
    private static final FileAttribute<?> RW_ATTR = asFileAttribute(Set.of(OWNER_READ, OWNER_WRITE));
    private static final Set<PosixFilePermission> READ_ONLY = singleton(OWNER_READ);

    private final CacheKeyFactory keyFactory;
    private final Path cacheDirectory;
    private final Log log;

    ByteCodeFileCache(CacheKeyFactory keyFactory, Path cacheDirectory, Log log) {
        this.cacheDirectory = cacheDirectory;
        this.keyFactory = keyFactory;
        this.log = log;
    }

    private boolean exists(Path file) {
        return doPrivileged((PrivilegedAction<Boolean>)() -> Files.isRegularFile(file));
    }

    private Path createCacheFile(CacheKey key) {
        final byte[] byteCodeHash = key.getByteCodeHash();
        final StringBuilder builder = new StringBuilder();
        for (int i = 2; i < byteCodeHash.length; ++i) {
            builder.append(toHex(byteCodeHash[i]));
        }
        builder.append('.').append(Integer.toHexString(key.getByteCodeLength()));
        return cacheDirectory
            .resolve(toHex(byteCodeHash[0]))
            .resolve(toHex(byteCodeHash[1]))
            .resolve(builder.toString());
    }

    private static FileAttribute<?>[] posixOptional(Path path, FileAttribute<?> attr) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null
            ? new FileAttribute<?>[] { attr } : new FileAttribute<?>[0];
    }

    private Path writeToCache(Path cacheFile, byte[] byteCode) throws IOException {
        final Path tempFile = Files.createTempFile(
            Files.createDirectories(cacheFile.getParent(), posixOptional(cacheFile, RWX_RX_RX_ATTR)),
            ".quasar",
            ".class",
            posixOptional(cacheFile, RW_ATTR)
        );
        try {
            final PosixFileAttributeView posix = Files.getFileAttributeView(Files.write(tempFile, byteCode), PosixFileAttributeView.class);
            if (posix != null) {
                posix.setPermissions(READ_ONLY);
            } else {
                final DosFileAttributeView dos = Files.getFileAttributeView(tempFile, DosFileAttributeView.class);
                if (dos != null) {
                    dos.setReadOnly(true);
                }
            }
            return Files.move(tempFile, cacheFile, ATOMIC_MOVE);
        } catch(IOException e) {
            Files.delete(tempFile);
            throw e;
        }
    }

    @Override
    public byte[] computeIfAbsent(String className, byte[] byteCode, Supplier<byte[]> computer) {
        if (byteCode == null) {
            return null;
        }

        final CacheKey cacheKey = keyFactory.createKey(className, byteCode);
        final Path cacheFile = createCacheFile(cacheKey);
        if (exists(cacheFile)) {
            try {
                return doPrivileged((PrivilegedExceptionAction<byte[]>) () ->
                    Files.readAllBytes(cacheFile)
                );
            } catch(PrivilegedActionException e) {
                log.error("Failed to read cached byte-code for " + className, e.getException());
                return computer.get();
            }
        } else {
            final byte[] instrumentedCode = computer.get();
            try {
                doPrivileged((PrivilegedExceptionAction<Path>) () ->
                    writeToCache(cacheFile, instrumentedCode)
                );
            } catch(PrivilegedActionException e) {
                log.error("Failed to write cache file for " + className, e.getException());
            }
            return instrumentedCode;
        }
    }

    private static String toHex(byte b) {
        // Ensure we generate 2 hex digits, i.e. including any leading zero.
        return Integer.toHexString(0x100 + Byte.toUnsignedInt(b)).substring(1);
    }
}
