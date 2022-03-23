package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.instrument.function.ResourceLocator;
import co.paralleluniverse.fibers.instrument.function.ThrowingBiFunction;

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
    private static final String BUNDLE_LOCATION_MATCHER_CLASS_NAME = "co.paralleluniverse.fibers.osgi.BundleLocationMatcher";
    private static final String GET_MATCHER_METHOD_NAME = "getMatcherMethod";
    private static final String BUNDLE_LOCATOR_CLASS_NAME = "co.paralleluniverse.fibers.osgi.BundleLocator";
    private static final String FIND_RESOURCE_OWNER_METHOD_NAME = "getFindResourceOwnerMethod";
    private static final String BUNDLE_CLASS_NAME = "org.osgi.framework.Bundle";

    private static ThrowingBiFunction<String, ClassLoader, Map<String, String>> superClassExtractor;
    private static BiPredicate<ClassLoader, Collection<Pattern>> bundleLocationExcluder;
    private static ResourceLocator findResourceOwner;
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
        } catch(IOException e) {
            throw new InternalError("Error reading resource " + resourceName, e);
        }
    }

    private static ClassLoader getOSGiLoaderFrom(ClassLoader cl) {
        if (osgiLoader == null) {
            final Class<?> bundleClass;
            try {
                bundleClass = Class.forName(BUNDLE_CLASS_NAME, false, cl);
            } catch(ClassNotFoundException e) {
                // The Bundle class is not visible from this classloader.
                return null;
            }
            osgiLoader = new OSGiClassLoader(bundleClass.getClassLoader());
        }
        return osgiLoader;
    }

    @SuppressWarnings("unchecked")
    private static ThrowingBiFunction<String, ClassLoader, Map<String, String>> createSuperClassExtractor(ClassLoader cl) {
        final ClassLoader loader = getOSGiLoaderFrom(cl);
        if (loader == null) {
            return null;
        }

        try {
            final Class<?> extractorClass = Class.forName(SUPER_CLASS_EXTRACTOR_CLASS_NAME, false, loader);
            return (ThrowingBiFunction<String, ClassLoader, Map<String, String>>) extractorClass.getMethod(GET_EXTRACTOR_METHOD_NAME).invoke(null);
        } catch(ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + SUPER_CLASS_EXTRACTOR_CLASS_NAME, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static BiPredicate<ClassLoader, Collection<Pattern>> createBundleLocationMatcher(ClassLoader cl) {
        final ClassLoader loader = getOSGiLoaderFrom(cl);
        if (loader == null) {
            return null;
        }

        try {
            final Class<?> matcherClass = Class.forName(BUNDLE_LOCATION_MATCHER_CLASS_NAME, false, loader);
            return (BiPredicate<ClassLoader, Collection<Pattern>>) matcherClass.getMethod(GET_MATCHER_METHOD_NAME).invoke(null);
        } catch(ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + BUNDLE_LOCATION_MATCHER_CLASS_NAME, e);
        }
    }

    private static ResourceLocator createFindResourceOwner(ClassLoader cl) {
        final ClassLoader loader = getOSGiLoaderFrom(cl);
        if (loader == null) {
            return null;
        }

        try {
            final Class<?> locatorClass = Class.forName(BUNDLE_LOCATOR_CLASS_NAME, false, loader);
            return (ResourceLocator) locatorClass.getMethod(FIND_RESOURCE_OWNER_METHOD_NAME).invoke(null);
        } catch(ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + BUNDLE_LOCATOR_CLASS_NAME, e);
        }
    }

    static synchronized ThrowingBiFunction<String, ClassLoader, Map<String, String>> fetchSuperClassExtractor(ClassLoader cl) {
        if (superClassExtractor == null) {
            superClassExtractor = doPrivileged(
                (PrivilegedAction<? extends ThrowingBiFunction<String, ClassLoader, Map<String, String>>>) () ->
                    createSuperClassExtractor(cl)
            );
        }
        return superClassExtractor;
    }

    static synchronized BiPredicate<ClassLoader, Collection<Pattern>> fetchBundleLocationMatcher(ClassLoader cl) {
        if (bundleLocationExcluder == null) {
            bundleLocationExcluder = doPrivileged(
                (PrivilegedAction<? extends BiPredicate<ClassLoader, Collection<Pattern>>>) () ->
                    createBundleLocationMatcher(cl)
            );
        }
        return bundleLocationExcluder;
    }

    static synchronized ResourceLocator findResourceOwner(ClassLoader cl) {
        if (findResourceOwner == null) {
            findResourceOwner = doPrivileged((PrivilegedAction<? extends ResourceLocator>) () ->
                createFindResourceOwner(cl)
            );
        }
        return findResourceOwner;
    }

    static synchronized void disable() {
        if (osgiLoader == null) {
            // Only disable OSGi support if it hasn't been activated yet.
            superClassExtractor = (a, b) -> null;
            bundleLocationExcluder = (a, b) -> false;
            findResourceOwner = MethodDatabase::getBestClassLoaderFor;
        }
    }
}
