package org.testing.osgi.supers;

import org.testing.osgi.annotation.Suspendable;
import java.lang.invoke.MethodHandle;
import java.util.function.Function;

@SuppressWarnings("unused")
public class SuperClassAction implements Function<MethodHandle, Throwable> {
    @Suspendable
    @Override
    public Throwable apply(MethodHandle message) {
        try {
            // Invoking a MethodHandle is always suspendable.
            throw new FourthException((String) message.invoke());
        } catch (FourthException | RuntimeException e) {
            System.err.println("Caught: " + e.getMessage());
            return e;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            return t;
        }
    }
}
