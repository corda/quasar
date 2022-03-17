package org.testing.osgi.suspendable;

import co.paralleluniverse.fibers.Suspendable;

import java.util.concurrent.Callable;

@SuppressWarnings("unused")
public class ExampleCallable implements Callable<String> {
    @Suspendable
    @Override
    public String call() {
        return "Hello Quasar!";
    }
}
