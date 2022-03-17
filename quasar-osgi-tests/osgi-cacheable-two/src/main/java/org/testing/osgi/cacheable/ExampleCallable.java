package org.testing.osgi.cacheable;

import co.paralleluniverse.fibers.Suspendable;

import java.util.concurrent.Callable;

@SuppressWarnings("unused")
public class ExampleCallable implements Callable<String> {
    @Suspendable
    @Override
    public String call() {
        System.out.println("2>> ExampleCallable.call() invoked");
        return "Hello OSGi Cache!";
    }
}
