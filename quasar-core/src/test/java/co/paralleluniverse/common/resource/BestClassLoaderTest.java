package co.paralleluniverse.common.resource;

import org.junit.Test;

import java.net.URL;
import java.util.List;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToResource;
import static co.paralleluniverse.common.resource.ClassLoaderUtil.getBestClassLoader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BestClassLoaderTest {
    private static final ClassLoader EXTENSION_CLASSLOADER = ClassLoader.getSystemClassLoader().getParent();

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

    public interface NestedTemplate {
        @Test
        void test();
    }
}
