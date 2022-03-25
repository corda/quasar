package co.paralleluniverse.fibers.instrument;

import java.net.URL;
import java.security.PrivilegedAction;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.getBestClassLoader;

final class GetBestClassLoaderAction implements PrivilegedAction<ClassLoader> {
    private final ClassLoader classLoader;
    private final String resourceName;
    private final URL resource;

    GetBestClassLoaderAction(ClassLoader classLoader, String resourceName, URL resource) {
        this.classLoader = classLoader;
        this.resourceName = resourceName;
        this.resource = resource;
    }

    @Override
    public ClassLoader run() {
        return getBestClassLoader(classLoader, resourceName, resource);
    }
}
