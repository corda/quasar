package org.testing.osgi;

import co.paralleluniverse.fibers.suspend.Instrumented;
import org.osgi.framework.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.LogManager;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Helpers {
    static final String QUASAR_LOG_TAG = "[quasar]";
    static final String CALL_METHOD_NAME = "call";
    static final String APPLY_METHOD_NAME = "apply";

    private Helpers() {
    }

    static void assertInstrumented(AnnotatedElement element) {
        assertTrue(element.isAnnotationPresent(Instrumented.class), element + " should be instrumented");
    }

    static void assertNotInstrumented(AnnotatedElement element) {
        assertFalse(element.isAnnotationPresent(Instrumented.class), element + " should not be instrumented");
    }

    static void assertCallable(Class<? extends Callable<?>> callable, String message) throws Exception {
        assertEquals(message, callable.getConstructor().newInstance().call());
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Callable<?>> loadCallableFrom(Bundle bundle, String className) throws ClassNotFoundException {
        // Loading a class requires OSGi AdminPermission("class").
        Class<?> callable = bundle.loadClass(className);
        assertTrue(Callable.class.isAssignableFrom(callable));
        return (Class<? extends Callable<?>>) callable;
    }

    @SuppressWarnings("unchecked")
    static <T, R> Class<? extends Function<T, R>> loadFunctionFrom(Bundle bundle, String className) throws ClassNotFoundException {
        // Loading a class requires OSGi AdminPermission("class").
        Class<?> function = bundle.loadClass(className);
        assertTrue(Function.class.isAssignableFrom(function));
        return (Class<? extends Function<T, R>>) function;
    }

    static InputStream getJar(String resourceName) {
        final InputStream input = Helpers.class.getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(input, "Bundle resource '" + resourceName + "' not found?!");
        return input;
    }

    static String[] captureStdErr(ThrowingRunnable runnable) throws Exception {
        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        final PrintStream oldStderr = System.err;
        try (PrintStream stderr = new PrintStream(errors, true, UTF_8)) {
            setStandardError(stderr);
            runnable.throwingRun();
        } finally {
            setStandardError(oldStderr);

            // Copy the output back to stderr for people to see.
            System.err.write(errors.toByteArray());
        }
        return errors.toString(UTF_8).split(System.lineSeparator());
    }

    static void setStandardError(PrintStream stderr) throws IOException {
        System.setErr(stderr);
        LogManager.getLogManager().readConfiguration();
    }
}
