package co.paralleluniverse.fibers.instrument;

import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.util.tracker.BundleTracker;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static co.paralleluniverse.fibers.instrument.QuasarInstrumentor.isEmptyOrTrue;
import static java.util.Collections.unmodifiableMap;
import static java.util.logging.Level.SEVERE;
import static org.osgi.framework.Bundle.INSTALLED;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE;
import static org.osgi.framework.Constants.BUNDLE_VERSION_ATTRIBUTE;
import static org.osgi.framework.Constants.EFFECTIVE_ACTIVE;
import static org.osgi.framework.Constants.EXTENSION_BUNDLE_ACTIVATOR;
import static org.osgi.framework.Constants.EXTENSION_DIRECTIVE;
import static org.osgi.framework.Constants.EXTENSION_FRAMEWORK;
import static org.osgi.framework.Constants.FRAGMENT_HOST;
import static org.osgi.framework.Constants.OBJECTCLASS;
import static org.osgi.framework.Constants.SERVICE_RANKING;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.VERSION_ATTRIBUTE;
import static org.osgi.framework.wiring.BundleRevision.PACKAGE_NAMESPACE;

@Header(name = FRAGMENT_HOST, value = SYSTEM_BUNDLE_SYMBOLICNAME + ';' + EXTENSION_DIRECTIVE + ":=" + EXTENSION_FRAMEWORK)
@Header(name = EXTENSION_BUNDLE_ACTIVATOR, value = "${@class}")
@Capability(
    namespace = "osgi.service",
    attribute = OBJECTCLASS + ":List<String>='co.paralleluniverse.fibers.instrument.QuasarInstrumentor'",
    effective = EFFECTIVE_ACTIVE
)
public final class QuasarActivator implements BundleActivator {
    private static final String SUSPENDABLE_ANNOTATION_PROPERTY_NAME = "co.paralleluniverse.quasar.suspendableAnnotation";
    private static final String EXCLUDE_LOCATIONS_PROPERTY_NAME = "co.paralleluniverse.quasar.excludeLocations";
    private static final String EXCLUDE_PACKAGES_PROPERTY_NAME = "co.paralleluniverse.quasar.excludePackages";
    private static final String CACHE_DIRECTORY_PROPERTY_NAME = "co.paralleluniverse.quasar.cacheDirectory";
    private static final String CACHE_LOCATIONS_PROPERTY_NAME = "co.paralleluniverse.quasar.cacheLocations";
    private static final String ALLOW_MONITORS_PROPERTY_NAME = "co.paralleluniverse.quasar.allowMonitors";
    private static final String ALLOW_BLOCKING_PROPERTY_NAME = "co.paralleluniverse.quasar.allowBlocking";
    private static final String SERVICE_PROPERTY_NAME = "co.paralleluniverse.quasar.service";
    private static final String VERBOSE_PROPERTY_NAME = "co.paralleluniverse.quasar.verbose";
    private static final String DEBUG_PROPERTY_NAME = "co.paralleluniverse.quasar.debug";
    private static final String CHECK_PROPERTY_NAME = "co.paralleluniverse.quasar.check";

    private static final String SUSPEND_PACKAGE_NAME = "co.paralleluniverse.fibers.suspend";
    private static final String REFLECTASM_SYMBOLIC_NAME = "com.esotericsoftware.reflectasm";
    private static final String FRAMEWORK_EXTENSION_PROPERTIES = "framework-extension.properties";

    private static final Logger LOGGER = Logger.getLogger("[quasar]");
    private static final Map<LogLevel, Level> LEVELS;

    static {
        Map<LogLevel, Level> levels = new EnumMap<>(LogLevel.class);
        levels.put(LogLevel.DEBUG, Level.FINE);
        levels.put(LogLevel.INFO, Level.INFO);
        levels.put(LogLevel.WARNING, Level.WARNING);
        LEVELS = unmodifiableMap(levels);

        // The JVM can only load QuasarActivator if the OSGi
        // framework classes already exist on the classpath.
        QuasarConfiguration.enableOSGi();
    }

    private ServiceRegistration<WeavingHook> weaver;
    private BundleTracker<?> bundleTracker;

    private Properties loadFrameworkExtensionProperties() {
        final URL resource = getClass().getResource(FRAMEWORK_EXTENSION_PROPERTIES);
        if (resource == null) {
            throw new IllegalStateException("Resource " + FRAMEWORK_EXTENSION_PROPERTIES + " not found");
        }

        try (InputStream input = resource.openStream()) {
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static String valueOf(Map<String, Object> attrs, String key) {
        return key + '=' + attrs.get(key);
    }

    private Set<String> computeDynamicImports(Bundle bundle) {
        final Set<String> dynamicImports = new HashSet<>();

        // Compute the precise instruction to dynamically import one of this bundle's own packages.
        for (BundleCapability capability : bundle.adapt(BundleWiring.class).getCapabilities(PACKAGE_NAMESPACE)) {
            final Map<String, Object> attrs = capability.getAttributes();
            final String packageName = attrs.get(PACKAGE_NAMESPACE).toString();
            if (SUSPEND_PACKAGE_NAME.equals(packageName)) {
                dynamicImports.add(packageName + ';'
                    + valueOf(attrs, VERSION_ATTRIBUTE) + ';'
                    + valueOf(attrs, BUNDLE_SYMBOLICNAME_ATTRIBUTE) + ';'
                    + valueOf(attrs, BUNDLE_VERSION_ATTRIBUTE)
                );
                break;
            }
        }

        if (dynamicImports.isEmpty()) {
            throw new IllegalStateException(SUSPEND_PACKAGE_NAME + " not found inside " + bundle);
        }

        // Load the properties file we created when we built this artifact.
        // This contains the version of the ReflectASM bundle to use.
        final Properties frameworkExtensionProperties = loadFrameworkExtensionProperties();
        final String reflectAsmVersion = frameworkExtensionProperties.getProperty(REFLECTASM_SYMBOLIC_NAME);
        if (reflectAsmVersion == null) {
            throw new IllegalStateException("Property '" + REFLECTASM_SYMBOLIC_NAME + "' is missing");
        }

        dynamicImports.add("com.esotericsoftware.reflectasm;"
            + BUNDLE_SYMBOLICNAME_ATTRIBUTE + '=' + REFLECTASM_SYMBOLIC_NAME + ';'
            + BUNDLE_VERSION_ATTRIBUTE + '=' + reflectAsmVersion
        );

        return dynamicImports;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        final Log log = new Log() {
            @Override
            public void log(LogLevel level, String msg, Object... args) {
                LOGGER.log(LEVELS.get(level), () -> String.format(msg, args));
            }

            @Override
            public void error(String msg, Throwable throwable) {
                LOGGER.log(SEVERE, msg, throwable);
            }
        };

        final String cacheDirectoryName = context.getProperty(CACHE_DIRECTORY_PROPERTY_NAME);
        final QuasarInstrumentor instrumentor = new QuasarInstrumentorBuilder(cacheDirectoryName, log)
            .setSuspendableAnnotations(context.getProperty(SUSPENDABLE_ANNOTATION_PROPERTY_NAME))
            .setAllowMonitors(context.getProperty(ALLOW_MONITORS_PROPERTY_NAME))
            .setAllowBlocking(context.getProperty(ALLOW_BLOCKING_PROPERTY_NAME))
            .setCheck(context.getProperty(CHECK_PROPERTY_NAME))
            .setVerbose(context.getProperty(VERBOSE_PROPERTY_NAME))
            .setDebug(context.getProperty(DEBUG_PROPERTY_NAME))
            .build();

        final String cacheLocations = context.getProperty(CACHE_LOCATIONS_PROPERTY_NAME);
        instrumentor.addCachedBundleLocations(cacheLocations);

        final String excludeLocations = context.getProperty(EXCLUDE_LOCATIONS_PROPERTY_NAME);
        instrumentor.addExcludedBundleLocations(excludeLocations);

        final String excludePackages = context.getProperty(EXCLUDE_PACKAGES_PROPERTY_NAME);
        instrumentor.addExcludedPackages(excludePackages);

        // Tell Quasar that we have an instrumentor.
        SuspendableHelper.javaAgent = true;

        final WeavingHook weavingHook = new QuasarWeavingHook(instrumentor, computeDynamicImports(context.getBundle()));
        final Hashtable<String, Object> serviceProperties = new Hashtable<>();
        serviceProperties.put(SERVICE_RANKING, Integer.MIN_VALUE);
        weaver = context.registerService(WeavingHook.class, weavingHook, serviceProperties);

        final QuasarBundleTracker quasarTracker = new QuasarBundleTracker(instrumentor);
        bundleTracker = new BundleTracker<>(context, INSTALLED, quasarTracker);
        bundleTracker.open();

        if (isEmptyOrTrue(context.getProperty(SERVICE_PROPERTY_NAME))) {
            // Available for testing.
            context.registerService(QuasarInstrumentor.class, instrumentor, null);
        }
    }

    @Override
    public void stop(BundleContext context) {
        bundleTracker.close();
        weaver.unregister();
    }
}
