package co.paralleluniverse.fibers.instrument;

import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.util.tracker.BundleTracker;

import java.util.EnumMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static co.paralleluniverse.fibers.instrument.QuasarInstrumentor.isEmptyOrTrue;
import static java.util.Collections.unmodifiableMap;
import static java.util.logging.Level.SEVERE;
import static org.osgi.framework.Bundle.ACTIVE;
import static org.osgi.framework.Bundle.INSTALLED;
import static org.osgi.framework.Bundle.RESOLVED;
import static org.osgi.framework.Bundle.STARTING;
import static org.osgi.framework.Constants.EFFECTIVE_ACTIVE;
import static org.osgi.framework.Constants.EXTENSION_BUNDLE_ACTIVATOR;
import static org.osgi.framework.Constants.EXTENSION_DIRECTIVE;
import static org.osgi.framework.Constants.EXTENSION_FRAMEWORK;
import static org.osgi.framework.Constants.FRAGMENT_HOST;
import static org.osgi.framework.Constants.OBJECTCLASS;
import static org.osgi.framework.Constants.SERVICE_RANKING;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;

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

    private static final Logger LOGGER = Logger.getLogger("[quasar]");
    private static final Map<LogLevel, Level> LEVELS;

    static {
        Map<LogLevel, Level> levels = new EnumMap<>(LogLevel.class);
        levels.put(LogLevel.DEBUG, Level.FINE);
        levels.put(LogLevel.INFO, Level.INFO);
        levels.put(LogLevel.WARNING, Level.WARNING);
        LEVELS = unmodifiableMap(levels);
    }

    private ServiceRegistration<WeavingHook> weaver;
    private BundleTracker<?> bundleTracker;

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

        final WeavingHook weavingHook = new QuasarWeavingHook(instrumentor);
        final Hashtable<String, Object> serviceProperties = new Hashtable<>();
        serviceProperties.put(SERVICE_RANKING, Integer.MIN_VALUE);
        weaver = context.registerService(WeavingHook.class, weavingHook, serviceProperties);

        final QuasarBundleTracker quasarTracker = new QuasarBundleTracker(instrumentor);
        bundleTracker = new BundleTracker<>(context, INSTALLED | RESOLVED | STARTING | ACTIVE, quasarTracker);
        bundleTracker.open();

        // Register the bundles that have already been added to the framework.
        for (Bundle bundle: context.getBundles()) {
            quasarTracker.addingBundle(bundle, null);
        }

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
