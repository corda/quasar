package org.testing.osgi;

import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.suspend.Instrumented;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.testing.osgi.security.SecurityConfig;
import org.testing.osgi.unprivileged.Unprivileged;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.security.SecurityConfig.ADMIN_PERMISSIONS;
import static org.testing.osgi.security.SecurityConfig.ALL_PERMISSIONS;

@ExtendWith({ BundleContextExtension.class, ServiceExtension.class })
@TestInstance(PER_CLASS)
class BundleExclusionTest {
    private static final String CALLABLE_CLASS_NAME = "org.testing.osgi.suspendable.ExampleCallable";
    private static final String MESSAGE = "Hello Quasar!";

    @InjectBundleContext
    BundleContext bundleContext;

    @BeforeAll
    void setup(
        @InjectService(timeout = 1000)
        SecurityConfig securityConfig
    ) {
        securityConfig.setSecurityPolicy(
            // Any call-stack containing Unprivileged has only these permissions.
            securityConfig.allowFor(Unprivileged.class, ADMIN_PERMISSIONS),
            securityConfig.denyAllFor(Unprivileged.class),

            // Everyone else has all permissions.
            securityConfig.allow("*", ALL_PERMISSIONS)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = { "IGNORE/", "VERIFY/" })
    void testBundleNotInstrumented(String locationPrefix) throws Exception {
        Bundle excluded = bundleContext.installBundle(withLocationPrefix(locationPrefix), getSuspendableJar());
        try {
            Class<? extends Callable<?>> callable = Unprivileged.doUnprivileged(() -> loadCallableFrom(excluded));
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
        String locationPrefix = "FLOW/";
        Bundle included = bundleContext.installBundle(withLocationPrefix(locationPrefix), getSuspendableJar());
        try {
            Class<? extends Callable<?>> callable = Unprivileged.doUnprivileged(() -> loadCallableFrom(included));
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
        // Loading a class requires OSGi AdminPermission("class").
        Class<?> callable = bundle.loadClass(CALLABLE_CLASS_NAME);
        assertTrue(Callable.class.isAssignableFrom(callable));
        return (Class<? extends Callable<?>>) callable;
    }
}
