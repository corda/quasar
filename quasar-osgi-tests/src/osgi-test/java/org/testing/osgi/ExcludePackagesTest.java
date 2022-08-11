package org.testing.osgi;

import co.paralleluniverse.fibers.instrument.QuasarPermission;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.testing.osgi.security.SecurityConfig;

import java.io.InputStream;
import java.util.concurrent.Callable;

import static co.paralleluniverse.fibers.instrument.QuasarPermission.CONFIGURATION;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.Helpers.CALL_METHOD_NAME;
import static org.testing.osgi.Helpers.QUASAR_LOG_TAG;
import static org.testing.osgi.Helpers.assertCallable;
import static org.testing.osgi.Helpers.assertInstrumented;
import static org.testing.osgi.Helpers.assertNotInstrumented;
import static org.testing.osgi.Helpers.captureStdErr;
import static org.testing.osgi.Helpers.getJar;
import static org.testing.osgi.Helpers.loadCallableFrom;
import static org.testing.osgi.security.SecurityConfig.ALL_PERMISSIONS;

@ExtendWith({ ServiceExtension.class, BundleContextExtension.class })
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(PER_CLASS)
class ExcludePackagesTest {
    private static final String MESSAGE = "Hello Quasar!";

    private static final String UNINSTRUMENTED_RESOURCE_NAME = "META-INF/osgi-uninstrumented.jar";
    private static final String HAS_SUSPENDABLE_NAME = "org.testing.osgi.uninstrumented.HasSuspendable";
    private static final String INTERNAL_HAS_SUSPENDABLE_NAME = HAS_SUSPENDABLE_NAME.replace('.', '/');

    private static final String MULTI_UNINSTRUMENTED_RESOURCE_NAME = "META-INF/osgi-multi-uninstrumented.jar";
    private static final String EXAMPLE_CALLABLE_NAME = "org.testing.multi.ExampleCallable";
    private static final String INTERNAL_EXAMPLE_CALLABLE_NAME = EXAMPLE_CALLABLE_NAME.replace('.', '/');
    private static final String INTERNAL_UNINSTRUMENTABLE_NAME = "org/testing/multi/uninstrumented/UninstrumentableType";

    @InjectBundleContext
    BundleContext bundleContext;

    @BeforeAll
    void setup(
        @InjectService(timeout = 1000)
        SecurityConfig securityConfig
    ) {
        securityConfig.setSecurityPolicy(
            // Prevent bundles in the "UNPRIVILEGED/*" location from configuring Quasar.
            securityConfig.deny("UNPRIVILEGED/*", singleton(new QuasarPermission(CONFIGURATION))),

            // Everyone else has all permissions.
            securityConfig.allow("*", ALL_PERMISSIONS)
        );
    }

    // Requires Quasar not to have been already configured to ignore the test package.
    @Order(0)
    @Test
    void testUnprivilegedLoading() throws Exception {
        final String[] lines = captureStdErr(() ->
            assertForBundle("UNPRIVILEGED/osgi-uninstrumented", getJar(UNINSTRUMENTED_RESOURCE_NAME), bundle -> {
                final Class<? extends Callable<?>> callableClass = loadCallableFrom(bundle, HAS_SUSPENDABLE_NAME);
                assertCallable(callableClass, MESSAGE);
                assertInstrumented(callableClass.getMethod(CALL_METHOD_NAME));
            })
        );
        assertThat(lines)
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG))
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.contains(" Bundle [osgi-uninstrumented]") && line.endsWith(" is not allowed to configure Quasar"))
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_HAS_SUSPENDABLE_NAME))
            .noneMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("Ignoring packages: org.testing.osgi.uninstrumented"));
    }

    // Should configure Quasar to ignore the test package. This configuration cannot be undone!
    @Order(1)
    @Test
    void testPrivilegedLoading() throws Exception {
        final String[] lines = captureStdErr(() ->
            assertForBundle("PRIVILEGED/osgi-uninstrumented", getJar(UNINSTRUMENTED_RESOURCE_NAME), bundle -> {
                final Class<? extends Callable<?>> callableClass = loadCallableFrom(bundle, HAS_SUSPENDABLE_NAME);
                assertCallable(callableClass, MESSAGE);
                assertNotInstrumented(callableClass.getMethod(CALL_METHOD_NAME));
            })
        );
        assertThat(lines)
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("Ignoring packages: org.testing.osgi.uninstrumented"))
            .noneMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_HAS_SUSPENDABLE_NAME));
    }

    // Requires Quasar not to have been already configured to ignore the test packages.
    @Order(2)
    @Test
    void testMultiUnprivilegedLoading() throws Exception {
        final String[] lines = captureStdErr(() ->
            assertForBundle("UNPRIVILEGED/osgi-multi-uninstrumented", getJar(MULTI_UNINSTRUMENTED_RESOURCE_NAME), bundle -> {
                final Class<? extends Callable<?>> callableClass = loadCallableFrom(bundle, EXAMPLE_CALLABLE_NAME);
                assertCallable(callableClass, "[UNINSTRUMENTED]");
                assertInstrumented(callableClass.getMethod(CALL_METHOD_NAME));
            })
        );
        assertThat(lines)
            .noneMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("Ignoring packages: org.testing.multi.uninstrumented"))
            .noneMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("Ignoring packages: org.testing.multi.uninstrumented.**"))
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_EXAMPLE_CALLABLE_NAME))
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_UNINSTRUMENTABLE_NAME));
    }

    // Should configure Quasar to ignore the test packages. This configuration cannot be undone!
    @Order(3)
    @Test
    void testMultiPrivilegedLoading() throws Exception {
        final String[] lines = captureStdErr(() ->
            assertForBundle("PRIVILEGED/osgi-multi-uninstrumented", getJar(MULTI_UNINSTRUMENTED_RESOURCE_NAME), bundle -> {
                final Class<? extends Callable<?>> callableClass = loadCallableFrom(bundle, EXAMPLE_CALLABLE_NAME);
                assertCallable(callableClass, "[UNINSTRUMENTED]");
                assertInstrumented(callableClass.getMethod(CALL_METHOD_NAME));
            })
        );
        assertThat(lines)
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("Ignoring packages: org.testing.multi.uninstrumented"))
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("Ignoring packages: org.testing.multi.uninstrumented.**"))
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_EXAMPLE_CALLABLE_NAME))
            .noneMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_UNINSTRUMENTABLE_NAME));
    }

    private void assertForBundle(String location, InputStream bundleData, ThrowingConsumer<Bundle> assertion) throws Exception {
        final Bundle bundle = bundleContext.installBundle(location, bundleData);
        try {
            // Bundle has no BundleContext until we start it.
            bundle.start();
            assertion.throwingAccept(bundle);
        } finally {
            bundle.uninstall();
        }
    }
}
