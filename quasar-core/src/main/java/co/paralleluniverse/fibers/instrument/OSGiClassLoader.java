package co.paralleluniverse.fibers.instrument;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToResource;
import static java.security.AccessController.doPrivileged;

/**
 * This classloader contains the Quasar agent's OSGI-specific logic.
 * We cannot instantiate this classloaser unless we locate the OSGi
 * system bundle that hosts the framework classes.
 */
final class OSGiClassLoader extends ClassLoader {
    private static final String SUPER_CLASS_EXTRACTOR_CLASS_NAME = "co.paralleluniverse.fibers.osgi.ExtractSuperClasses";
    private static final String BUNDLE_CLASS_NAME = "org.osgi.framework.Bundle";

    private static Constructor<PrivilegedExceptionAction<Map<String, String>>> superClassExtractorConstructor;
    private static ClassLoader osgiLoader;

    static {
        OSGiClassLoader.registerAsParallelCapable();
    }

    private OSGiClassLoader(ClassLoader parent) {
        super("Quasar-OSGI", parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.startsWith("co.paralleluniverse.fibers.osgi.")) {
            throw new ClassNotFoundException(name);
        }

        final String resourceName = "META-INF/" + classToResource(name);
        final URL resource = OSGiClassLoader.class.getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }

        try (InputStream input = resource.openStream()) {
            byte[] bytecode = input.readAllBytes();
            return defineClass(name, bytecode, 0, bytecode.length, OSGiClassLoader.class.getProtectionDomain());
        } catch (IOException e) {
            throw new InternalError("Error reading resource " + resourceName, e);
        }
    }

    private static ClassLoader getOSGiLoaderFrom(ClassLoader cl) {
        if (osgiLoader == null) {
            Class<?> bundleClass;
            try {
                bundleClass = Class.forName(BUNDLE_CLASS_NAME, false, cl);
            } catch (ClassNotFoundException e) {
                // The Bundle class is not visible from this classloader.
                return null;
            }
            osgiLoader = new OSGiClassLoader(bundleClass.getClassLoader());
        }
        return osgiLoader;
    }

    @SuppressWarnings("unchecked")
    private static Constructor<PrivilegedExceptionAction<Map<String, String>>> createSuperClassExtractorConstructor(ClassLoader cl) {
        final ClassLoader loader = getOSGiLoaderFrom(cl);
        if (loader == null) {
            return null;
        }

        try {
            return ((Class<PrivilegedExceptionAction<Map<String, String>>>) Class.forName(SUPER_CLASS_EXTRACTOR_CLASS_NAME, false, loader))
                .getDeclaredConstructor(String.class, ClassLoader.class);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Constructor for " + SUPER_CLASS_EXTRACTOR_CLASS_NAME + " not found", e);
        }
    }

    static synchronized Constructor<PrivilegedExceptionAction<Map<String, String>>> fetchSuperClassExtractorConstructor(ClassLoader cl) {
        if (superClassExtractorConstructor == null) {
            superClassExtractorConstructor = doPrivileged(
                (PrivilegedAction<Constructor<PrivilegedExceptionAction<Map<String, String>>>>) () ->
                    createSuperClassExtractorConstructor(cl)
            );
        }
        return superClassExtractorConstructor;
    }
}
