package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.common.test.*;
import co.paralleluniverse.fibers.*;
import co.paralleluniverse.strands.*;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static co.paralleluniverse.fibers.TestsHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClassLoaderTest {
    private static final String DYNAMIC_SUSPENDABLE_CLASS_NAME = "co.paralleluniverse.fibers.dynamic.DynamicallyLoadedSuspendable";
    private static final String DYNAMIC_FIBER_CLASS_NAME = "co.paralleluniverse.fibers.dynamic.DynamicallyLoadedFiber";

    private URL getTestClassesURL() throws Exception {
        URI currentURL = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        System.out.println(currentURL);
        return currentURL.resolve("../classloadertest/").toURL();
    }

    private URLClassLoader createClassLoaderFor(URL... urls) {
        return new URLClassLoader(urls);
    }

    /**
     * Test instrumentation of @Suspendable classes loaded dynamically in a custom classloader
     */
    @Test
    public void testSuspendableMethodsLoadedDynamically() {
        final ArrayList<String> results = new ArrayList<>();
        try {
            final URL testClassesURL = getTestClassesURL();
            System.out.println(testClassesURL);
            final ClassLoader cl = createClassLoaderFor(testClassesURL);
            Class<?> testClass = Class.forName(DYNAMIC_SUSPENDABLE_CLASS_NAME, false, cl);
            Constructor<?> constructor = testClass.getConstructor();
            final TestInterface testInstance = (TestInterface) constructor.newInstance();

            assertEquals(cl, testInstance.getClass().getClassLoader());
            assertEquals(ClassLoader.getSystemClassLoader(), TestInterface.class.getClassLoader());

            Fiber<?> co = new Fiber<Object>((String) null, null, (SuspendableCallable<Object>) null) {
                @SuppressWarnings("RedundantThrows")
                @Override
                protected Object run() throws SuspendExecution, InterruptedException {
                    testInstance.test(results);
                    return null;
                }
            };
            for (int i = 0; i < 6; i++) {
                exec(co);
            }
        } catch (Exception ex) {
            throw new AssertionError(ex);
        } finally {
            System.out.println(results);
        }

        assertEquals(17, results.size());
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "d1", "d2", "b1", "b2", "f", "o1", "d1", "d2", "b1", "b2", "o2", "b1"), results);
    }

    /**
     * Test instrumentation of @Suspendable class loaded twice in distinct classloaders to ensure it is instrumented properly each time
     */
    @Test
    public void testSuspendableClassLoadedTwice() {
        final ArrayList<String> results1 = new ArrayList<>();
        final ArrayList<String> results2 = new ArrayList<>();
        try {
            final URL testClassesURL = getTestClassesURL();
            System.out.println(testClassesURL);
            final ClassLoader cl1 = createClassLoaderFor(testClassesURL);
            final ClassLoader cl2 = createClassLoaderFor(testClassesURL);
            Class<?> testClass1 = Class.forName(DYNAMIC_SUSPENDABLE_CLASS_NAME, false, cl1);
            Class<?> testClass2 = Class.forName(DYNAMIC_SUSPENDABLE_CLASS_NAME, false, cl2);
            Constructor<?> constructor1 = testClass1.getConstructor();
            Constructor<?> constructor2 = testClass2.getConstructor();
            final TestInterface testInstance1 = (TestInterface) constructor1.newInstance();
            final TestInterface testInstance2 = (TestInterface) constructor2.newInstance();

            assertEquals(cl1, testInstance1.getClass().getClassLoader());
            assertEquals(cl2, testInstance2.getClass().getClassLoader());
            assertEquals(ClassLoader.getSystemClassLoader(), TestInterface.class.getClassLoader());

            Fiber<?> co1 = new Fiber<Object>((String) null, null, (SuspendableCallable<Object>) null) {
                @SuppressWarnings("RedundantThrows")
                @Override
                protected Object run() throws SuspendExecution, InterruptedException {
                    testInstance1.test(results1);
                    return null;
                }
            };

            Fiber<?> co2 = new Fiber<Object>((String) null, null, (SuspendableCallable<Object>) null) {
                @SuppressWarnings("RedundantThrows")
                @Override
                protected Object run() throws SuspendExecution, InterruptedException {
                    testInstance2.test(results2);
                    return null;
                }
            };
            exec(co2);
            for (int i = 0; i < 6; i++) {
                exec(co1);
            }
            exec(co2);
            exec(co2);
            exec(co2);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        } finally {
            System.out.println(results1);
            System.out.println(results2);
        }

        assertEquals(17, results1.size());
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "d1", "d2", "b1", "b2", "f", "o1", "d1", "d2", "b1", "b2", "o2", "b1"), results1);
        assertEquals(12, results2.size());
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "d1", "d2", "b1", "b2", "f", "o1", "d1"), results2);
    }

    /**
     * Test instrumentation of a fiber implementation class that is loaded dynamically.
     */
    @Test
    public void testDynamicallyLoadedFiber() {
        ArrayList<String> results = null;
        try {
            final URL testClassesURL = getTestClassesURL();
            System.out.println(testClassesURL);
            final ClassLoader cl = createClassLoaderFor(testClassesURL);
            Class<?> testClass = Class.forName(DYNAMIC_FIBER_CLASS_NAME, false, cl);
            Constructor<?> constructor = testClass.getConstructor();
            @SuppressWarnings("unchecked")
            final Fiber<ArrayList<String>> testInstance = (Fiber<ArrayList<String>>) constructor.newInstance();

            assertEquals(cl, testInstance.getClass().getClassLoader());
            assertEquals(ClassLoader.getSystemClassLoader(), TestInterface.class.getClassLoader());

            for (int i = 0; i < 4; i++) {
                assertFalse(testInstance.isDone());
                exec(testInstance);
            }
            assertTrue(testInstance.isDone());
            results = testInstance.get();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        } finally {
            System.out.println(results);
        }

        assertEquals(8, results.size());
        assertEquals(Arrays.asList("a", "b", "o1", "o2", "base1", "base2", "o3", "c"), results);
    }

    @Test
    public void testClassesForCorrectMethodDatabase() throws Exception {
        // Load a class from a boot classpath extension.
        Class.forName("co.paralleluniverse.vtime.Clock", false, ClassLoader.getSystemClassLoader());
        try (URLClassLoader cl = createClassLoaderFor(getTestClassesURL())) {
            Class.forName(DYNAMIC_SUSPENDABLE_CLASS_NAME, false, cl);
            Class.forName(DYNAMIC_FIBER_CLASS_NAME, false, cl);

            final QuasarInstrumentor instrumentor = Retransform.getInstrumentor();
            assertClassesBelongToClassLoader(ClassLoader.getSystemClassLoader(), instrumentor);
            assertClassesBelongToClassLoader(ClassLoader.getSystemClassLoader().getParent(), instrumentor);
            assertClassesBelongToClassLoader(cl, instrumentor);
        }
    }

    @Test
    public void testSuperClassesForCorrectMethodDatabase() throws Exception {
        try (URLClassLoader cl = createClassLoaderFor(getTestClassesURL())) {
            Class.forName(DYNAMIC_SUSPENDABLE_CLASS_NAME, false, cl);
            Class.forName(DYNAMIC_FIBER_CLASS_NAME, false, cl);

            final QuasarInstrumentor instrumentor = Retransform.getInstrumentor();
            assertSuperClassesBelongToClassLoader(ClassLoader.getSystemClassLoader(), instrumentor);
            assertSuperClassesBelongToClassLoader(ClassLoader.getSystemClassLoader().getParent(), instrumentor);
            assertSuperClassesBelongToClassLoader(cl, instrumentor);
        }
    }

    private void assertClassesBelongToClassLoader(ClassLoader cl, QuasarInstrumentor instrumentor) {
        assertClassesBelongToClassLoader(instrumentor.getMethodDatabase(cl).getClassNames(), cl);
    }

    private void assertSuperClassesBelongToClassLoader(ClassLoader cl, QuasarInstrumentor instrumentor) {
        assertClassesBelongToClassLoader(instrumentor.getMethodDatabase(cl).getClassNamesForSuperClasses(), cl);
    }

    private void assertClassesBelongToClassLoader(Collection<String> classNames, ClassLoader actual) {
        assertFalse("MethodDatabase for " + actual + " should not be empty.", classNames.isEmpty());

        classNames.stream().filter(ClassLoaderTest::excludeLambdas).forEach(className -> {
            final String actualClassName = className.replace('/', '.');
            try {
                final Class<?> dbClass = Class.forName(actualClassName, false, actual);
                final ClassLoader expected = getClassLoaderFor(dbClass);
                assertEquals("Instrumented class " + actualClassName + " belongs to wrong method database",
                        expected, actual);
            } catch (ClassNotFoundException e) {
                fail("Failed to load class " + actualClassName);
            }
        });
    }

    private static boolean excludeLambdas(String className) {
        return !className.contains("$$Lambda$") && !className.startsWith("java/lang/invoke/LambdaForm$");
    }

    private static ClassLoader getClassLoaderFor(Class<?> clazz) {
        final ClassLoader cl = clazz.getClassLoader();
        return (cl != null) ? cl : ClassLoader.getSystemClassLoader().getParent();
    }
}
