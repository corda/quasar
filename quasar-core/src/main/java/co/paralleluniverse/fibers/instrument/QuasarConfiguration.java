package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.instrument.function.ResourceLocator;

import java.net.URL;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.getBestClassLoader;
import static java.security.AccessController.doPrivileged;

final class QuasarConfiguration {
    private static final String BUNDLE_LOCATION_MATCHER_CLASS_NAME = "co.paralleluniverse.fibers.osgi.BundleLocationMatcher";
    private static final String GET_MATCHER_METHOD_NAME = "getMatcherMethod";
    private static final String BUNDLE_LOCATOR_CLASS_NAME = "co.paralleluniverse.fibers.osgi.BundleLocator";
    private static final String FIND_RESOURCE_OWNER_METHOD_NAME = "getFindResourceOwnerMethod";
    private static final BiPredicate<ClassLoader, Collection<Pattern>> FALSE = (a, b) -> false;

    private static BiPredicate<ClassLoader, Collection<Pattern>> bundleLocationMatcher = FALSE;
    private static ResourceLocator findResourceOwner = QuasarConfiguration::fallbackFindResourceOwner;

    private static ClassLoader fallbackFindResourceOwner(ClassLoader loader, String resourceName, URL resource) {
        return resource == null ? loader : getBestClassLoader(loader, resourceName, resource);
    }

    @SuppressWarnings("unchecked")
    private static BiPredicate<ClassLoader, Collection<Pattern>> createBundleLocationMatcher() {
        try {
            final Class<?> matcherClass = Class.forName(BUNDLE_LOCATION_MATCHER_CLASS_NAME, false, QuasarConfiguration.class.getClassLoader());
            return (BiPredicate<ClassLoader, Collection<Pattern>>) matcherClass.getMethod(GET_MATCHER_METHOD_NAME).invoke(null);
        } catch(ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + BUNDLE_LOCATION_MATCHER_CLASS_NAME, e);
        }
    }

    private static ResourceLocator createFindResourceOwner() {
        try {
            final Class<?> locatorClass = Class.forName(BUNDLE_LOCATOR_CLASS_NAME, false, QuasarConfiguration.class.getClassLoader());
            return (ResourceLocator) locatorClass.getMethod(FIND_RESOURCE_OWNER_METHOD_NAME).invoke(null);
        } catch(ReflectiveOperationException e) {
            throw new InternalError("Failed to initialise " + BUNDLE_LOCATOR_CLASS_NAME, e);
        }
    }

    static BiPredicate<ClassLoader, Collection<Pattern>> bundleLocationMatcher() {
        return bundleLocationMatcher;
    }

    static ResourceLocator findResourceOwner() {
        return findResourceOwner;
    }

    /**
     * Enable the OSGi-aware logic for handling {@link ClassLoader} relationships.
     */
    static void enableOSGi() {
        bundleLocationMatcher = doPrivileged(
            (PrivilegedAction<? extends BiPredicate<ClassLoader, Collection<Pattern>>>) QuasarConfiguration::createBundleLocationMatcher
        );

        findResourceOwner = doPrivileged(
            (PrivilegedAction<? extends ResourceLocator>) QuasarConfiguration::createFindResourceOwner
        );
    }
}
