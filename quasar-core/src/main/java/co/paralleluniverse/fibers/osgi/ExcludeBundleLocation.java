package co.paralleluniverse.fibers.osgi;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static java.security.AccessController.doPrivileged;

/**
 * This class is not used directly because {@link ClassLoader#getSystemClassLoader()}
 * might not contain the OSGi Framework classes. We will define this class as required
 * inside a dynamic classloader, if/when we discover which {@link ClassLoader} the OSGi
 * framework does exist inside.
 */
@SuppressWarnings("unused")
public final class ExcludeBundleLocation {
    public static BiPredicate<ClassLoader, Collection<Pattern>> getExcluderMethod() {
        return (loader, excludedLocations) ->
            loader instanceof BundleReference && matchesLocation((BundleReference) loader, excludedLocations);
    }

    private static boolean matchesLocation(BundleReference ref, Collection<Pattern> locations) {
        Bundle bundle = ref.getBundle();
        return (bundle != null) && matches(doPrivileged((PrivilegedAction<String>) bundle::getLocation), locations);
    }

    private static boolean matches(String bundleLocation, Collection<Pattern> locations) {
        for (Pattern location : locations) {
            if (location.matcher(bundleLocation).matches()) {
                return true;
            }
        }
        return false;
    }

    private ExcludeBundleLocation() {
    }
}
