package org.testing.osgi;

import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.suspend.Instrumented;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class BundleExclusionTest {
    private static final String CALLABLE_CLASS_NAME = "org.testing.osgi.suspendable.ExampleCallable";
    private static final String MESSAGE = "Hello Quasar!";

    private BundleContext bundleContext;

    @BeforeAll
    void setup() {
        Bundle testBundle = FrameworkUtil.getBundle(getClass());
        assertNotNull(testBundle, "Test not running inside an OSGi framework");
        bundleContext = testBundle.getBundleContext();
        assertNotNull(bundleContext, "Bundle context is missing");
    }

    @ParameterizedTest
    @ValueSource(strings = { "IGNORE/", "VERIFY/" })
    void testBundleNotInstrumented(String locationPrefix) throws Exception {
        Bundle excluded = bundleContext.installBundle(withLocationPrefix(locationPrefix), getSuspendableJar());
        try {
            Class<? extends Callable<?>> callable = loadCallableFrom(excluded);
            assertCallable(callable);

            Method call = callable.getMethod("call");
            assertTrue(call.isAnnotationPresent(Suspendable.class));
            assertFalse(call.isAnnotationPresent(Instrumented.class));
        } finally {
            excluded.uninstall();
        }
    }

    @Test
    void testBundleIsInstrumented() throws Exception {
        Bundle included = bundleContext.installBundle(withLocationPrefix("FLOW/"), getSuspendableJar());
        try {
            Class<? extends Callable<?>> callable = loadCallableFrom(included);
            assertCallable(callable);

            Method call = callable.getMethod("call");
            assertTrue(call.isAnnotationPresent(Suspendable.class));
            assertTrue(call.isAnnotationPresent(Instrumented.class));
        } finally {
            included.uninstall();
        }
    }

    private void assertCallable(Class<? extends Callable<?>> callable) throws Exception {
        assertEquals(MESSAGE, callable.getConstructor().newInstance().call());
    }

    private String withLocationPrefix(String prefix) {
        return prefix + "osgi-suspendable";
    }

    private InputStream getSuspendableJar() {
        InputStream input = getClass().getClassLoader().getResourceAsStream("META-INF/osgi-suspendable.jar");
        assertNotNull(input, "Bundle resource not found?!");
        return input;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Callable<?>> loadCallableFrom(Bundle bundle) throws ClassNotFoundException {
        Class<?> callable = bundle.loadClass(CALLABLE_CLASS_NAME);
        assertTrue(Callable.class.isAssignableFrom(callable));
        return (Class<? extends Callable<?>>) callable;
    }
}
