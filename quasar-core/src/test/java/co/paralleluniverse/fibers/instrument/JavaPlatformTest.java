package co.paralleluniverse.fibers.instrument;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToSlashed;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class JavaPlatformTest {
    private static final String JRT_MODULES = "modules";
    private static final String JRT_FS = "jrt:/";

    @Test
    public void testLoadingJavaPlatformClasses() throws IOException {
        final FileSystem jrt = FileSystems.getFileSystem(URI.create(JRT_FS));
        final Set<String> classNames = Files.walk(jrt.getPath(JRT_MODULES))
            .filter(p -> p.getNameCount() > 2)
            .filter(JavaPlatformTest::checkModuleExists)
            .map(JavaPlatformTest::toFilePath)
            .filter(p -> p.endsWith(".class"))
            .map(p -> p.substring(0, p.length() - ".class".length()))
            .filter(p -> !"module-info".equals(p))
            .collect(Collectors.toUnmodifiableSet());

        // Check that we correctly identify these classes as belonging to the JDK.
        assertThat(classNames)
            .allMatch(JavaPlatformTest::isJDKClass)
            .isNotEmpty();

        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        final PrintStream oldStderr = System.err;
        try (PrintStream stderr = new PrintStream(errors, true, UTF_8)) {
            System.setErr(stderr);

            // Check that Quasar can load each of these classes without error.
            for (String className : classNames) {
                try {
                    Class.forName(className, false, ClassLoader.getSystemClassLoader());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace(stderr);
                    fail("Failed to load " + className + ": " + e.getMessage());
                }
            }
        } finally {
            System.setErr(oldStderr);

            // Copy the output back to stderr for people to see.
            System.err.write(errors.toByteArray());
        }

        final String[] lines = errors.toString(UTF_8).split(System.lineSeparator());
        assertThat(lines).noneMatch(line -> line.startsWith("[quasar]"));
    }

    private static String toFilePath(Path p) {
        final StringJoiner joiner = new StringJoiner(".");
        for (int i = 2; i < p.getNameCount(); ++i) {
            joiner.add(p.getName(i).toString());
        }
        return joiner.toString();
    }

    private static boolean checkModuleExists(Path p) {
        final String moduleName = p.getName(1).toString();
        return ModuleLayer.boot().findModule(moduleName).isPresent();
    }

    private static boolean isJDKClass(String className) {
        return MethodDatabase.isJDK(classToSlashed(className));
    }
}
