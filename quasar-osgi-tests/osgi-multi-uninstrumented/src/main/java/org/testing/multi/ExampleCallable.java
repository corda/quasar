package org.testing.multi;

import org.testing.osgi.annotation.Suspendable;
import org.testing.multi.uninstrumented.UninstrumentableType;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class ExampleCallable implements Callable<String> {
    private final Supplier<String> uninstrumentable = new UninstrumentableType();

    @Suspendable
    @Override
    public String call() {
        System.out.println(">> ExampleCallable.call() invoked");
        return String.format("[%s]", uninstrumentable.get());
    }
}
