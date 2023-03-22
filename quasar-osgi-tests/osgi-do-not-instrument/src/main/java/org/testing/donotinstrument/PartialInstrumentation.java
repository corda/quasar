package org.testing.donotinstrument;

import co.paralleluniverse.quasar.annotations.DoNotInstrument;
import org.testing.osgi.annotation.Suspendable;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.Callable;
import java.util.function.Function;

@SuppressWarnings("unused")
public class PartialInstrumentation implements Function<MethodHandle, Throwable>, Callable<Throwable> {
    private final MethodHandle handle;

    public PartialInstrumentation(MethodHandle handle) {
        this.handle = handle;
    }

    @Suspendable
    @Override
    public Throwable call() {
        try {
            // Invoking a MethodHandle is always suspendable.
            throw new Exception((String) handle.invoke());
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            return t;
        }
    }

    @DoNotInstrument
    @Override
    public Throwable apply(MethodHandle message) {
        try {
            // Invoking a MethodHandle is always suspendable.
            throw new Exception((String) message.invoke());
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            return t;
        }
    }
}
