package org.testing.osgi.suspendable;

import org.testing.osgi.annotation.Suspendable;
import java.util.concurrent.Callable;
import java.util.function.Function;

@SuppressWarnings("unused")
public class ExampleCallable implements Callable<String>, Function<String, String> {
    @Suspendable
    @Override
    public String call() {
        System.out.println("1>> ExampleCallable.call() invoked");
        return "Hello Quasar!";
    }

    @Suspendable
    @Override
    public String apply(String tag) {
        return tag + ':' + call();
    }
}
