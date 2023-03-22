package org.testing.osgi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.junit5.context.BundleContextExtension;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.Helpers.APPLY_METHOD_NAME;
import static org.testing.osgi.Helpers.CALL_METHOD_NAME;
import static org.testing.osgi.Helpers.assertInstrumented;
import static org.testing.osgi.Helpers.assertNotInstrumented;
import static org.testing.osgi.Helpers.captureStdErr;
import static org.testing.osgi.Helpers.getJar;
import static org.testing.osgi.Helpers.loadFunctionFrom;

@ExtendWith(BundleContextExtension.class)
@TestInstance(PER_CLASS)
class DoNotInstrumentTest {
    private static final String DO_NOT_INSTRUMENT_RESOURCE_NAME = "META-INF/osgi-do-not-instrument.jar";
    private static final String FORBID_INSTRUMENTATION_NAME = "org.testing.donotinstrument.ForbidInstrumentation";
    private static final String PARTIAL_INSTRUMENTATION_NAME = "org.testing.donotinstrument.PartialInstrumentation";

    private static final String INTERNAL_FORBID_INSTRUMENTATION_NAME = FORBID_INSTRUMENTATION_NAME.replace('.', '/');
    private static final String INTERNAL_PARTIAL_INSTRUMENTATION_NAME = PARTIAL_INSTRUMENTATION_NAME.replace('.', '/');

    @InjectBundleContext
    BundleContext bundleContext;

    private static String getApplyMessage() {
        return "Apply me, Quasar!";
    }

    private static String getConstructMessage() {
        return "Construct me, Quasar!";
    }

    @Test
    void testDoesNotInstrument() throws Exception {
        final MethodHandle applyArg = MethodHandles.lookup().findStatic(getClass(), "getApplyMessage", methodType(String.class));
        final MethodHandle constructArg = MethodHandles.lookup().findStatic(getClass(), "getConstructMessage", methodType(String.class));
        final String[] lines = captureStdErr(() ->
            assertForBundle("DO-NOT-INSTRUMENT/osgi-do-not-instrument", getJar(DO_NOT_INSTRUMENT_RESOURCE_NAME), bundle -> {
                final Class<? extends Function<MethodHandle, Throwable>> forbidClass = loadFunctionFrom(bundle, FORBID_INSTRUMENTATION_NAME);
                assertNotInstrumented(forbidClass);
                assertNotInstrumented(forbidClass.getMethod(APPLY_METHOD_NAME, MethodHandle.class));
                assertThat(forbidClass.getConstructor().newInstance().apply(applyArg))
                    .hasMessage("Apply me, Quasar!")
                    .isExactlyInstanceOf(Exception.class);

                final Class<? extends Function<MethodHandle, Throwable>> partialClass = loadFunctionFrom(bundle, PARTIAL_INSTRUMENTATION_NAME);
                assertInstrumented(partialClass);
                assertInstrumented(partialClass.getMethod(CALL_METHOD_NAME));
                assertNotInstrumented(partialClass.getMethod(APPLY_METHOD_NAME, MethodHandle.class));

                final Function<MethodHandle, Throwable> partial = partialClass.getConstructor(MethodHandle.class).newInstance(constructArg);
                assertThat(partial.apply(applyArg))
                    .hasMessage("Apply me, Quasar!")
                    .isExactlyInstanceOf(Exception.class);

                @SuppressWarnings("unchecked")
                final Throwable called = ((Callable<Throwable>) partial).call();
                assertThat(called)
                    .hasMessage("Construct me, Quasar!")
                    .isExactlyInstanceOf(Exception.class);
            })
        );
        assertThat(lines)
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_PARTIAL_INSTRUMENTATION_NAME.replace('.', '/')))
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_FORBID_INSTRUMENTATION_NAME.replace('.', '/')))
            .anyMatch(line -> line.endsWith("Already instrumented and not forcing, so not touching method "
                + INTERNAL_FORBID_INSTRUMENTATION_NAME +"#apply(Ljava/lang/invoke/MethodHandle;)Ljava/lang/Throwable;"))
            .anyMatch(line -> line.endsWith("Instrumenting method PartialInstrumentation.java:"
                + INTERNAL_PARTIAL_INSTRUMENTATION_NAME + "#call()Ljava/lang/Throwable;"))
            .noneMatch(line -> line.endsWith("Instrumenting method PartialInstrumentation.java:"
                + INTERNAL_PARTIAL_INSTRUMENTATION_NAME + "#apply(Ljava/lang/invoke/MethodHandle;)Ljava/lang/Throwable;"));
    }

    @SuppressWarnings("SameParameterValue")
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
