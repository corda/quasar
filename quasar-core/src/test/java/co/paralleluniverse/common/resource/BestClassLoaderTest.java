package co.paralleluniverse.common.resource;

import co.paralleluniverse.common.test.ClassFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToResource;
import static co.paralleluniverse.common.resource.ClassLoaderUtil.getBestClassLoader;
import static co.paralleluniverse.common.resource.ClassLoaderUtil.isClassFile;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BestClassLoaderTest {
    private static final ClassLoader PLATFORM_CLASSLOADER = ClassLoader.getPlatformClassLoader();
    private static final String FILE_PROTOCOL = "file";
    private static final String JRT_MODULES = "modules";
    private static final String JRT_PROTOCOL = "jrt";
    private static final String JRT_FS = "jrt:/";

    @BeforeClass
    public static void setup() {
        assertNull("non-null bootstrap classloader", PLATFORM_CLASSLOADER.getParent());
    }

    @Test
    public void testWithBootstrapClassLoaderAsParent() throws Exception {
        final Class<?> testClass = ClassFactory.renameTo(
            TemplateInterface.class,
            "org.testing.TestInterface1",
            null
        );
        final ClassLoader classLoader = testClass.getClassLoader();

        final String testClassResourceName = classToResource(testClass);
        final URL testClassResource = classLoader.getResource(testClassResourceName);
        assertNotNull(testClassResource);
        assertEquals(classLoader, getBestClassLoader(classLoader, testClassResourceName, testClassResource));

        final String javaResourceName = classToResource(List.class);
        final URL javaResource = classLoader.getResource(javaResourceName);
        assertNotNull(javaResource);
        assertEquals(PLATFORM_CLASSLOADER, getBestClassLoader(classLoader, javaResourceName, javaResource));
    }

    @Test
    public void testWithApplicationClassLoaderAsParent() throws Exception {
        final Class<?> testClass = ClassFactory.renameTo(
            NestedTemplate.class,
            "org.testing.TestInterface2",
            ClassLoader.getSystemClassLoader()
        );
        final ClassLoader classLoader = testClass.getClassLoader();

        final String testClassResourceName = classToResource(testClass);
        final URL testClassResource = classLoader.getResource(testClassResourceName);
        assertNotNull(testClassResource);
        assertEquals(classLoader, getBestClassLoader(classLoader, testClassResourceName, testClassResource));

        final String javaResourceName = classToResource(List.class);
        final URL javaResource = classLoader.getResource(javaResourceName);
        assertNotNull(javaResource);
        assertEquals(PLATFORM_CLASSLOADER, getBestClassLoader(classLoader, javaResourceName, javaResource));

        final String junitResourceName = classToResource(Test.class);
        final URL junitResource = classLoader.getResource(junitResourceName);
        assertNotNull(junitResource);
        assertEquals(ClassLoader.getSystemClassLoader(), getBestClassLoader(classLoader, junitResourceName, junitResource));
    }

    @Test
    public void testBootstrapClasspathExtension() throws Exception {
        final Class<?> testClass = Class.forName("co.paralleluniverse.vtime.Clock", false, ClassLoader.getSystemClassLoader());
        assertNull(testClass.getClassLoader());

        final String testResourceName = classToResource(testClass);
        final URL testResource = ClassLoader.getSystemClassLoader().getResource(testResourceName);
        assertNotNull(testResource);
        assertThat(testResource.toString())
            .startsWith("jar:file:")
            .endsWith("!/" + testResourceName);
        assertEquals(PLATFORM_CLASSLOADER, getBestClassLoader(ClassLoader.getSystemClassLoader(), testResourceName, testResource));
    }

    @Test
    public void testClassLoaderForFileResource() {
        final ClassLoader classLoader = getClass().getClassLoader();

        final String testClassResourceName = classToResource(getClass());
        final URL testClassResource = classLoader.getResource(testClassResourceName);
        assertNotNull(testClassResource);
        assertEquals(FILE_PROTOCOL, testClassResource.getProtocol());
        assertEquals(classLoader, getBestClassLoader(classLoader, testClassResourceName, testClassResource));
    }

    @Test
    public void testBestClassLoaderForJavaRuntimeImage() throws IOException {
        final FileSystem jrt = FileSystems.getFileSystem(URI.create(JRT_FS));
        final Set<String> moduleNames = Files.walk(jrt.getPath(JRT_MODULES))
            .filter(p -> p.getNameCount() > 2)
            .filter(p -> isClassFile(p.toString()))
            .map(p -> {
                // Path has format /modules/<module-name>/<resource-item-path>
                final String moduleName = p.getName(1).toString();
                final Optional<Module> module = ModuleLayer.boot().findModule(moduleName);
                if (module.isPresent()) {
                    final String path = p.toString();
                    final String resourceName = path.substring(2 + JRT_MODULES.length() + moduleName.length());
                    final URL resource = ClassLoader.getSystemResource(resourceName);
                    assertNotNull(resourceName + " not found", resource);
                    assertEquals(JRT_PROTOCOL, resource.getProtocol());

                    final ClassLoader best = getBestClassLoader(ClassLoader.getSystemClassLoader(), resourceName, resource);
                    assertEquals(path + " has incorrect best classloader", getClassLoaderForModule(moduleName), best);
                    assertEquals(resource, best.getResource(resourceName));
                    return moduleName;
                } else {
                    return null;
                }
            }).filter(Objects::nonNull)
            .collect(toUnmodifiableSet());

        // Check we tested what we were expecting to test.
        assertThat(moduleNames)
            .hasSizeGreaterThan(2)
            .anySatisfy(name ->
                assertTrue(name.startsWith("java.")))
            .anySatisfy(name ->
                assertTrue(name.startsWith("jdk.")));
    }

    private static ClassLoader getClassLoaderForModule(String moduleName) {
        final ClassLoader cl = ModuleLayer.boot().findLoader(moduleName);
        return cl == null ? PLATFORM_CLASSLOADER : cl;
    }

    public interface NestedTemplate {
        @Test
        void test();
    }
}
