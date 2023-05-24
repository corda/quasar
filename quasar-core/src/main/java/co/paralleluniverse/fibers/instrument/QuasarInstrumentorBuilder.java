package co.paralleluniverse.fibers.instrument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import static co.paralleluniverse.fibers.instrument.LogLevel.DEBUG;
import static co.paralleluniverse.fibers.instrument.LogLevel.INFO;
import static co.paralleluniverse.fibers.instrument.LogLevel.WARNING;
import static co.paralleluniverse.fibers.instrument.QuasarInstrumentor.isEmptyOrTrue;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

final class QuasarInstrumentorBuilder {
    private static final String BYTE_CODE_HASH_ALGORITHM = "SHA-256";

    private final QuasarInstrumentor instrumentor;

    QuasarInstrumentorBuilder(String cacheDirectoryName, Log log) {
        instrumentor = new QuasarInstrumentor(createByteCodeCache(cacheDirectoryName, log), log);
    }

    QuasarInstrumentorBuilder setDoNotInstrumentAnnotations(String annotationNames) {
        if (annotationNames != null) {
            instrumentor.addTypeNames("DO_NOT_INSTRUMENT", annotationNames.split(",", 0));
        }
        return this;
    }

    QuasarInstrumentorBuilder setSuspendableAnnotations(String annotationNames) {
        if (annotationNames != null) {
            instrumentor.addTypeNames("SUSPENDABLE", annotationNames.split(",", 0));
        }
        return this;
    }

    QuasarInstrumentorBuilder setAllowBlocking(String allowBlocking) {
        instrumentor.setAllowBlocking(isEmptyOrTrue(allowBlocking));
        return this;
    }

    QuasarInstrumentorBuilder setAllowMonitors(String allowMonitors) {
        instrumentor.setAllowMonitors(isEmptyOrTrue(allowMonitors));
        return this;
    }

    QuasarInstrumentorBuilder setCheck(String check) {
        instrumentor.setCheck(isEmptyOrTrue(check));
        return this;
    }

    QuasarInstrumentorBuilder setVerbose(String verbose) {
        instrumentor.setVerbose(isEmptyOrTrue(verbose));
        return this;
    }

    QuasarInstrumentorBuilder setDebug(String debug) {
        instrumentor.setDebug(isEmptyOrTrue(debug));
        return this;
    }

    QuasarInstrumentor build() {
        // CORDA-3666: Access Classes now so we don't deadlock while loading it later.
        //
        // We are calling isJDK(THROWABLE_NAME) for the side effect of the JVM
        // running both MethodDatabase.<clinit> and Classes.<clinit>. We expect
        // this call to return true.
        // Prints "MethodDatabase, Classes ready: true"
        instrumentor.log(DEBUG, "MethodDatabase, Classes ready: %s", MethodDatabase.isJDK(Classes.THROWABLE_NAME));

        return instrumentor;
    }

    private static ByteCodeCache createByteCodeCache(String cacheDirectoryName, Log log) {
        final ByteCodeCache.CacheKeyFactory keyFactory;
        try {
            keyFactory = ByteCodeCache.createKeyFactory(BYTE_CODE_HASH_ALGORITHM);
        } catch(NoSuchAlgorithmException e) {
            throw new InternalError(e.getMessage(), e);
        }

        final Path cacheDirectory = getCacheDirectory(cacheDirectoryName, log);
        return (cacheDirectory != null)
            ? new ByteCodeFileCache(keyFactory, cacheDirectory, log)
            : new ByteCodeMemoryCache(keyFactory);
    }

    private static Path getCacheDirectory(String cacheDirectoryName, Log log) {
        if (cacheDirectoryName != null) {
            final Path cacheDirectory = Paths.get(cacheDirectoryName).toAbsolutePath();
            try {
                final Set<PosixFilePermission> requiredPermissions = Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
                if (Files.isDirectory(cacheDirectory) && Files.getPosixFilePermissions(cacheDirectory).containsAll(requiredPermissions)) {
                    log.log(INFO, "Cache directory: %s", cacheDirectory.toAbsolutePath());
                    return cacheDirectory;
                } else {
                    log.log(WARNING, "Invalid cache directory '%s'", cacheDirectoryName);
                }
            } catch(IOException e) {
                log.error("Cannot determine permissions for " + cacheDirectoryName, e);
            }
        }
        return null;
    }
}
