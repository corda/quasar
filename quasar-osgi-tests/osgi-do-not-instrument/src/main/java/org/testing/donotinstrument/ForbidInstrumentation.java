package org.testing.donotinstrument;

import org.testing.osgi.annotation.DoNotInstrument;
import org.testing.osgi.annotation.Suspendable;

import java.lang.invoke.MethodHandle;
import java.util.function.Function;

@SuppressWarnings("unused")
@DoNotInstrument
public class ForbidInstrumentation implements Function<MethodHandle, Throwable> {
    @Suspendable
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
