package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.instrument.function.BiFunction;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToResource;
import static java.security.AccessController.doPrivileged;

/**
 * This classloader contains the Quasar agent's OSGI-specific logic.
 * We cannot instantiate this classloader unless we locate the OSGi
 * system bundle that hosts the framework classes.
 */
final class OSGiClassLoader extends ClassLoader {
    private static final String SUPER_CLASS_EXTRACTOR_CLASS_NAME = "co.paralleluniverse.fibers.osgi.ExtractSuperClasses";
    private static final String GET_EXTRACTOR_METHOD_NAME = "getExtractorMethod";
    private static final String BUNDLE_EXCLUDER_CLASS_NAME = "co.paralleluniverse.fibers.osgi.ExcludeBundleLocation";
    private static final String GET_EXCLUDER_METHOD_NAME = "getExcluderMethod";
    private static final String BUNDLE_CLASS_NAME = "org.osgi.framework.Bundle";

    private static BiFunction<String, ClassLoader, Map<String, String>> superClassExtractor;
    private static BiPredicate<ClassLoader, Collection<Pattern>> bundleLocationExcluder;
    private static ClassLoader osgiLoader;

    static {
        OSGiClassLoader.registerAsParallelCapable();

        // Disable this classloader unless it's part of the OSGi Quasar Java agent.
        final String resourceName = getOSGiResourceName(SUPER_CLASS_EXTRACTOR_CLASS_NAME);
        final URL osgiResource = doPrivileged((PrivilegedAction<URL>) () ->
            OSGiClassLoader.class.getClassLoader().getResource(resourceName)
        );
        if (osgiResource == null) {
            disable();
        }
    }

    private static String getOSGiResourceName(String className) {
        return "META-INF/" + classToResource(className);
    }

    private OSGiClassLoader(ClassLoader parent) {
        super("Quasar-OSGI", parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.startsWith("co.paralleluniverse.fibers.osgi.")) {
            throw new ClassNotFoundException(name);
        }

        final String resourceName = getOSGiResourceName(name);
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
    private static BiFunction<String, ClassLoader, Map<String, String>> createSuperClassExtractor(ClassLoader cl) {
        final ClassLoader loader = getOSGiLoaderFrom(cl);
        if (loader == null) {
            return null;
        }

        try {
            Class<?> extractorClass = Class.forName(SUPER_CLASS_EXTRACTOR_CLASS_NAME, false, loader);
            return (BiFunction<String, ClassLoader, Map<String, String>>) extractorClass.getMethod(GET_EXTRACTOR_METHOD_NAME).invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + SUPER_CLASS_EXTRACTOR_CLASS_NAME, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static BiPredicate<ClassLoader, Collection<Pattern>> createBundleLocationExcluder(ClassLoader cl) {
        final ClassLoader loader = getOSGiLoaderFrom(cl);
        if (loader == null) {
            return null;
        }

        try {
            Class<?> extractorClass = Class.forName(BUNDLE_EXCLUDER_CLASS_NAME, false, loader);
            return (BiPredicate<ClassLoader, Collection<Pattern>>) extractorClass.getMethod(GET_EXCLUDER_METHOD_NAME).invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + BUNDLE_EXCLUDER_CLASS_NAME, e);
        }
    }

    static synchronized BiFunction<String, ClassLoader, Map<String, String>> fetchSuperClassExtractor(ClassLoader cl) {
        if (superClassExtractor == null) {
            superClassExtractor = doPrivileged(
                (PrivilegedAction<? extends BiFunction<String, ClassLoader, Map<String, String>>>) () ->
                    createSuperClassExtractor(cl)
            );
        }
        return superClassExtractor;
    }

    static synchronized BiPredicate<ClassLoader, Collection<Pattern>> fetchBundleLocationExcluder(ClassLoader cl) {
        if (bundleLocationExcluder == null) {
            bundleLocationExcluder = doPrivileged(
                (PrivilegedAction<? extends BiPredicate<ClassLoader, Collection<Pattern>>>) () ->
                    createBundleLocationExcluder(cl)
            );
        }
        return bundleLocationExcluder;
    }

    static synchronized void disable() {
        if (osgiLoader == null) {
            // Only disable OSGi support if it hasn't been activated yet.
            superClassExtractor = (a, b) -> null;
            bundleLocationExcluder = (a, b) -> false;
        }
    }
}
