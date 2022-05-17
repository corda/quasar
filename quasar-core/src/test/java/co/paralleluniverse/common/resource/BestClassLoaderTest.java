package co.paralleluniverse.common.resource;

import co.paralleluniverse.common.test.ClassFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.List;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToResource;
import static co.paralleluniverse.common.resource.ClassLoaderUtil.getBestClassLoader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BestClassLoaderTest {
    private static final ClassLoader EXTENSION_CLASSLOADER = ClassLoader.getSystemClassLoader().getParent();

    @BeforeClass
    public static void setup() {
        assertNull("non-null bootstrap classloader", EXTENSION_CLASSLOADER.getParent());
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
        assertEquals(EXTENSION_CLASSLOADER, getBestClassLoader(classLoader, javaResourceName, javaResource));
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
        assertEquals(EXTENSION_CLASSLOADER, getBestClassLoader(classLoader, javaResourceName, javaResource));

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
        assertEquals(EXTENSION_CLASSLOADER, getBestClassLoader(ClassLoader.getSystemClassLoader(), testResourceName, testResource));
    }

    @Test
    public void testClassLoaderForFileResource() {
        final ClassLoader classLoader = getClass().getClassLoader();

        final String testClassResourceName = classToResource(getClass());
        final URL testClassResource = classLoader.getResource(testClassResourceName);
        assertNotNull(testClassResource);
        assertEquals("file", testClassResource.getProtocol());
        assertEquals(classLoader, getBestClassLoader(classLoader, testClassResourceName, testClassResource));
    }

    public interface NestedTemplate {
        @Test
        void test();
    }
}
