package co.paralleluniverse.fibers.osgi;

import co.paralleluniverse.common.resource.ClassLoaderUtil;
import co.paralleluniverse.fibers.instrument.ExtractSuperClass;
import co.paralleluniverse.fibers.instrument.function.BiFunction;
import org.osgi.framework.BundleReference;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.security.AccessController.doPrivileged;

/**
 * This class is not used directly because {@link ClassLoader#getSystemClassLoader()}
 * might not contain the OSGi Framework classes. We will redefine this class manually
 * inside a dynamic classloader, if/when we discover which {@link ClassLoader} the OSGi
 * framework does exist inside.
 */
@SuppressWarnings("unused")
public final class ExtractSuperClasses {
    private static final String PACKAGE_WIRING = "osgi.wiring.package";
    private static final String JAVA_OBJECT = "java/lang/Object";

    public static BiFunction<String, ClassLoader, Map<String, String>> getExtractorMethod() {
        return ExtractSuperClasses::extractSuperClasses;
    }

    public static Map<String, String> extractSuperClasses(String className, ClassLoader cl) throws Exception {
        try {
            return (cl instanceof BundleReference) ? doPrivileged((PrivilegedExceptionAction<Map<String, String>>) () ->
               new Extractor((BundleReference) cl).extractFor(className)
            ) : null;
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }
    }

    private ExtractSuperClasses() {
    }

    private static final class Extractor {
        private final BundleWiring initialWiring;

        Extractor(BundleReference bundleReference) {
            initialWiring = bundleReference.getBundle().adapt(BundleWiring.class);
        }

        Map<String, String> extractFor(String className) throws Exception {
            final Map<String, String> superClasses = new HashMap<>();
            BundleWiring bundleWiring = initialWiring;
            String packageName = "";
            while (!JAVA_OBJECT.equals(className)) {
                final String nextPackageName = getPackageName(className);
                if (!packageName.equals(nextPackageName)) {
                    // Either the package has changed, or we don't know that it
                    // hasn't changed. Check whether this bundle wiring supports
                    // our package or must be switched for one that does.
                    packageName = nextPackageName;
                    bundleWiring = getBundleWiringFor(bundleWiring, packageName);
                }

                final String superClassName = extractSuperClass(bundleWiring, className);
                if (superClassName == null) {
                    // Only java.lang.Object should have a null super class.
                    break;
                }
                superClasses.put(className, superClassName);
                className = superClassName;
            }
            return superClasses;
        }

        private String extractSuperClass(BundleWiring bundleWiring, String className) throws Exception {
            final ClassLoader cl = bundleWiring == null ? null : bundleWiring.getClassLoader();
            try (InputStream is = ClassLoaderUtil.getResourceAsStream(cl, className + ".class")) {
                return ExtractSuperClass.extractFrom(is);
            } catch (IOException e) {
                throw new FileNotFoundException(className);
            }
        }

        private BundleWiring getBundleWiringFor(BundleWiring bundleWiring, String packageName) {
            List<BundleWire> requiredWires = bundleWiring.getRequiredWires(PACKAGE_WIRING);
            for (BundleWire requiredWire : requiredWires) {
                BundleCapability capability = requiredWire.getCapability();
                if (capability != null) {
                    Map<String, Object> attributes = capability.getAttributes();
                    Object wirePackage = attributes.get(PACKAGE_WIRING);
                    if (packageName.equals(wirePackage)) {
                        return requiredWire.getProviderWiring();
                    }
                }
            }
            // We haven't gone anywhere, so keep this wiring.
            return bundleWiring;
        }

        private static String getPackageName(String className) {
            int idx = className.lastIndexOf('/');
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid class: " + className);
            }
            return className.substring(0, idx).replace('/', '.');
        }
    }
}
