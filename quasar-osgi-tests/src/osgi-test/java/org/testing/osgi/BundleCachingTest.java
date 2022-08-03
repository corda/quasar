package org.testing.osgi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.testing.osgi.security.SecurityConfig;
import org.testing.osgi.unprivileged.Unprivileged;

import java.io.InputStream;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.Helpers.CALL_METHOD_NAME;
import static org.testing.osgi.Helpers.QUASAR_LOG_TAG;
import static org.testing.osgi.Helpers.assertCallable;
import static org.testing.osgi.Helpers.assertInstrumented;
import static org.testing.osgi.Helpers.captureStdErr;
import static org.testing.osgi.Helpers.getJar;
import static org.testing.osgi.Helpers.loadCallableFrom;
import static org.testing.osgi.security.SecurityConfig.ADMIN_PERMISSIONS;
import static org.testing.osgi.security.SecurityConfig.ALL_PERMISSIONS;

@ExtendWith({ BundleContextExtension.class, ServiceExtension.class })
@TestInstance(PER_CLASS)
class BundleCachingTest {
    private static final String SUSPENDABLE_CLASS_NAME = "org.testing.osgi.suspendable.ExampleCallable";
    private static final String INTERNAL_SUSPENDABLE_NAME = SUSPENDABLE_CLASS_NAME.replace('.', '/');

    private static final String SUSPENDABLE_RESOURCE_NAME = "META-INF/osgi-suspendable.jar";
    private static final String SUSPENDABLE_MESSAGE = "Hello Quasar!";

    private static final String CACHEABLE_CLASS_NAME = "org.testing.osgi.cacheable.ExampleCallable";
    private static final String INTERNAL_CACHEABLE_NAME = CACHEABLE_CLASS_NAME.replace('.', '/');

    private static final String CACHEABLE_ONE_RESOURCE_NAME = "META-INF/osgi-cacheable-one.jar";
    private static final String CACHEABLE_ONE_MESSAGE = "Hello Quasar Cache!";

    private static final String CACHEABLE_TWO_RESOURCE_NAME = "META-INF/osgi-cacheable-two.jar";
    private static final String CACHEABLE_TWO_MESSAGE = "Hello OSGi Cache!";

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

    @Test
    void testByteCodeIsCached() throws Exception {
        final String[] lines1 = captureStdErr(() ->
            // The class is instrumented the first time, and then cached.
            assertForBundle("CACHED/osgi-suspendable-A", getJar(SUSPENDABLE_RESOURCE_NAME), bundle -> {
                Class<? extends Callable<?>> callable = Unprivileged.doUnprivileged(() -> loadCallableFrom(bundle, SUSPENDABLE_CLASS_NAME));
                assertInstrumented(callable.getMethod(CALL_METHOD_NAME));
                assertCallable(callable, SUSPENDABLE_MESSAGE);
            })
        );
        assertThat(lines1)
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("TRANSFORM: " + INTERNAL_SUSPENDABLE_NAME));

        final String[] lines2 = captureStdErr(() ->
                // Next time, we just use the instrumented byte-code from the cache.
            assertForBundle("CACHED/osgi-suspendable-B", getJar(SUSPENDABLE_RESOURCE_NAME), bundle -> {
                Class<? extends Callable<?>> callable = Unprivileged.doUnprivileged(() -> loadCallableFrom(bundle, SUSPENDABLE_CLASS_NAME));
                assertInstrumented(callable.getMethod(CALL_METHOD_NAME));
                assertCallable(callable, SUSPENDABLE_MESSAGE);
            })
        );
        assertThat(lines2)
            .noneMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("TRANSFORM: " + INTERNAL_SUSPENDABLE_NAME));
    }

    @Test
    void testClassesWithSameNameAreDistinct() throws Exception {
        final String[] lines1 = captureStdErr(() ->
            // The class is instrumented the first time, and then cached.
            assertForBundle("CACHED/osgi-cacheable-one", getJar(CACHEABLE_ONE_RESOURCE_NAME), bundle -> {
                Class<? extends Callable<?>> callable = Unprivileged.doUnprivileged(() -> loadCallableFrom(bundle, CACHEABLE_CLASS_NAME));
                assertInstrumented(callable.getMethod(CALL_METHOD_NAME));
                assertCallable(callable, CACHEABLE_ONE_MESSAGE);
            })
        );
        assertThat(lines1)
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("TRANSFORM: " + INTERNAL_CACHEABLE_NAME));

        final String[] lines2 = captureStdErr(() ->
            // This is a different class with the same name as the first; it cannot yet exist in the cache.
            assertForBundle("CACHED/osgi-cacheable-two", getJar(CACHEABLE_TWO_RESOURCE_NAME), bundle -> {
                Class<? extends Callable<?>> callable = Unprivileged.doUnprivileged(() -> loadCallableFrom(bundle, CACHEABLE_CLASS_NAME));
                assertInstrumented(callable.getMethod(CALL_METHOD_NAME));
                assertCallable(callable, CACHEABLE_TWO_MESSAGE);
            })
        );
        assertThat(lines2)
            .anyMatch(line -> line.startsWith(QUASAR_LOG_TAG) && line.endsWith("TRANSFORM: " + INTERNAL_CACHEABLE_NAME));
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
