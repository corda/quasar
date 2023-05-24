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
import java.util.function.Function;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.Helpers.APPLY_METHOD_NAME;
import static org.testing.osgi.Helpers.assertInstrumented;
import static org.testing.osgi.Helpers.captureStdErr;
import static org.testing.osgi.Helpers.getJar;
import static org.testing.osgi.Helpers.loadFunctionFrom;

@ExtendWith(BundleContextExtension.class)
@TestInstance(PER_CLASS)
class KotlinLambdaTest {
    private static final String KOTLIN_LAMBDA_RESOURCE_NAME = "META-INF/osgi-kotlin-lambda.jar";
    private static final String FORBID_INSTRUMENTATION_NAME = "org.testing.osgi.kotlin.ForbidLambdaInstrumentation";
    private static final String INTERNAL_FORBID_NAME = FORBID_INSTRUMENTATION_NAME.replace('.', '/') + "$apply$runnable$1";
    private static final String INTERNAL_FORBID_METHOD_NAME = INTERNAL_FORBID_NAME + "#invoke()Ljava/lang/Throwable;";

    @InjectBundleContext
    BundleContext bundleContext;

    private static String getApplyMessage() {
        return "Apply me, Quasar!";
    }

    @Test
    void testDoesNotInstrument() throws Exception {
        final MethodHandle applyArg = MethodHandles.lookup().findStatic(getClass(), "getApplyMessage", methodType(String.class));
        final String[] lines = captureStdErr(() ->
            assertForBundle("KOTLIN/osgi-kotlin-lambda", getJar(KOTLIN_LAMBDA_RESOURCE_NAME), bundle -> {
                final Class<? extends Function<MethodHandle, Throwable>> forbidLambdaClass = loadFunctionFrom(bundle, FORBID_INSTRUMENTATION_NAME);
                assertInstrumented(forbidLambdaClass);
                assertInstrumented(forbidLambdaClass.getMethod(APPLY_METHOD_NAME, MethodHandle.class));
                assertThat(forbidLambdaClass.getConstructor().newInstance().apply(applyArg))
                    .isExactlyInstanceOf(Exception.class)
                    .hasMessage("Apply me, Quasar!");
            })
        );

        assertThat(lines)
            .anyMatch(line -> line.endsWith("TRANSFORM: " + INTERNAL_FORBID_NAME))
            .noneMatch(line -> line.endsWith("WARNING: UnableToInstrumentException encountered when instrumenting " + INTERNAL_FORBID_METHOD_NAME + ": Unable to instrument " + INTERNAL_FORBID_METHOD_NAME + " because of synchronization"));
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
