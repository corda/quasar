package co.paralleluniverse.fibers.instrument;

import java.net.URL;
import java.security.PrivilegedAction;

final class GetResourceAction implements PrivilegedAction<URL> {
    private final ClassLoader classLoader;
    private final String resourceName;

    GetResourceAction(ClassLoader classLoader, String resourceName) {
        this.classLoader = classLoader;
        this.resourceName = resourceName;
    }

    @Override
    public URL run() {
        return classLoader.getResource(resourceName);
    }
}
