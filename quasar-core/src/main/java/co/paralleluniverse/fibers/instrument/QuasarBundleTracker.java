package co.paralleluniverse.fibers.instrument;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static co.paralleluniverse.fibers.instrument.LogLevel.WARNING;
import static co.paralleluniverse.fibers.instrument.QuasarPermission.CONFIGURATION;

final class QuasarBundleTracker implements BundleTrackerCustomizer<Object> {
    private static final QuasarPermission QUASAR_CONFIG = new QuasarPermission(CONFIGURATION);

    private final QuasarInstrumentor instrumentor;
    private final Set<Bundle> bundles;

    QuasarBundleTracker(QuasarInstrumentor instrumentor) {
        this.instrumentor = instrumentor;
        this.bundles = ConcurrentHashMap.newKeySet();
    }

    @Override
    public Object addingBundle(Bundle bundle, BundleEvent bundleEvent) {
        if (bundles.add(bundle) && !instrumentor.isExcludedBundleLocation(bundle.getLocation())) {
            final String ignoredPackages = bundle.getHeaders().get("Quasar-Ignore-Package");
            if (ignoredPackages != null) {
                try {
                    final SecurityManager sm;
                    if ((sm = System.getSecurityManager()) != null) {
                        sm.checkPermission(QUASAR_CONFIG, bundle.adapt(AccessControlContext.class));
                    }

                    instrumentor.addExcludedPackages(ignoredPackages);
                } catch(AccessControlException e) {
                    instrumentor.log(WARNING, "Bundle [%s][%d] (location=%s) is not allowed to configure Quasar",
                            bundle.getSymbolicName(), bundle.getBundleId(), bundle.getLocation());
                }
            }
        }
        return null;
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent bundleEvent, Object obj) {
        removedBundle(bundle, bundleEvent, obj);
        addingBundle(bundle, bundleEvent);
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent bundleEvent, Object obj) {
        bundles.remove(bundle);
    }
}
