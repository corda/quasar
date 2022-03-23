package co.paralleluniverse.fibers.instrument.function;

import java.net.URL;

@FunctionalInterface
public interface ResourceLocator {
    ClassLoader locate(ClassLoader cl, String resourceName, URL target);
}
