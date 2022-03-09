package org.testing.osgi;

import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.suspend.Instrumented;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.PackagePermission;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condpermadmin.BundleLocationCondition;
import org.osgi.service.condpermadmin.ConditionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;

import java.io.FilePermission;
import java.io.InputStream;
import java.lang.management.ManagementPermission;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectPermission;
import java.net.NetPermission;
import java.net.SocketPermission;
import java.nio.file.LinkPermission;
import java.security.AllPermission;
import java.security.Permission;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.PropertyPermission;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.osgi.framework.PackagePermission.IMPORT;
import static org.osgi.framework.ServicePermission.GET;
import static org.osgi.framework.ServicePermission.REGISTER;
import static org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW;
import static org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY;

@TestInstance(PER_CLASS)
class BundleExclusionTest {
    private static final String CALLABLE_CLASS_NAME = "org.testing.osgi.suspendable.ExampleCallable";
    private static final String MESSAGE = "Hello Quasar!";
    private static final Collection<Permission> DENIED_PERMISSIONS = asList(
        // OSGi permissions
        new ServicePermission(PermissionAdmin.class.getName(), REGISTER),
        new AdminPermission(),
        new ServicePermission("*", GET),
        new PackagePermission("org.osgi.framework", IMPORT),
        new PackagePermission("org.osgi.service.component", IMPORT),

        // Java permissions
        new RuntimePermission("*"),
        new ReflectPermission("*"),
        new NetPermission("*"),
        new LinkPermission("hard"),
        new LinkPermission("symbolic"),
        new ManagementPermission("control"),
        new ManagementPermission("monitor"),
        new PropertyPermission("*", "read,write"),
        new SocketPermission("*", "accept,connect,listen"),
        new FilePermission("<<ALL FILES>>", "read,write,execute,delete,readlink")
    );
    private static final Collection<Permission> ALL_PERMISSIONS = singletonList(
        new AllPermission("*", "*")
    );

    private BundleContext bundleContext;

    @BeforeAll
    void setup() {
        Bundle testBundle = FrameworkUtil.getBundle(getClass());
        assertNotNull(testBundle, "Test not running inside an OSGi framework");
        bundleContext = testBundle.getBundleContext();
        assertNotNull(bundleContext, "Bundle context is missing");
    }

    @ParameterizedTest
    @ValueSource(strings = { "IGNORE/", "VERIFY/" })
    void testBundleNotInstrumented(String locationPrefix) throws Exception {
        String locationFilter = locationPrefix + '*';
        setSecurityPolicy(
            allow(locationFilter, singleton(new ServicePermission("(location=" + locationFilter + ')', GET))),
            deny(locationFilter, DENIED_PERMISSIONS),
            allow("*", ALL_PERMISSIONS)
        );
        Bundle excluded = bundleContext.installBundle(withLocationPrefix(locationPrefix), getSuspendableJar());
        try {
            Class<? extends Callable<?>> callable = loadCallableFrom(excluded);
            assertCallable(callable);

            Method call = callable.getMethod("call");
            assertTrue(call.isAnnotationPresent(Suspendable.class));
            assertFalse(call.isAnnotationPresent(Instrumented.class));
        } finally {
            excluded.uninstall();
        }
    }

    @Test
    void testBundleIsInstrumented() throws Exception {
        String locationPrefix = "FLOW/";
        String locationFilter = locationPrefix + '*';
        setSecurityPolicy(
            allow(locationFilter, singleton(new ServicePermission("(location=" + locationFilter + ')', GET))),
            deny(locationFilter, DENIED_PERMISSIONS),
            allow("*", ALL_PERMISSIONS)
        );
        Bundle included = bundleContext.installBundle(withLocationPrefix(locationPrefix), getSuspendableJar());
        try {
            Class<? extends Callable<?>> callable = loadCallableFrom(included);
            assertCallable(callable);

            Method call = callable.getMethod("call");
            assertTrue(call.isAnnotationPresent(Suspendable.class));
            assertTrue(call.isAnnotationPresent(Instrumented.class));
        } finally {
            included.uninstall();
        }
    }

    private void assertCallable(Class<? extends Callable<?>> callable) throws Exception {
        assertEquals(MESSAGE, callable.getConstructor().newInstance().call());
    }

    private String withLocationPrefix(String prefix) {
        return prefix + "osgi-suspendable";
    }

    private InputStream getSuspendableJar() {
        InputStream input = getClass().getClassLoader().getResourceAsStream("META-INF/osgi-suspendable.jar");
        assertNotNull(input, "Bundle resource not found?!");
        return input;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Callable<?>> loadCallableFrom(Bundle bundle) throws ClassNotFoundException {
        Class<?> callable = bundle.loadClass(CALLABLE_CLASS_NAME);
        assertTrue(Callable.class.isAssignableFrom(callable));
        return (Class<? extends Callable<?>>) callable;
    }

    private PermissionData allow(String locationFilter, Collection<Permission> permissions) {
        return new PermissionData(ALLOW, locationFilter, permissions);
    }

    @SuppressWarnings("SameParameterValue")
    private PermissionData deny(String locationFilter, Collection<Permission> permissions) {
        return new PermissionData(DENY, locationFilter, permissions);
    }

    private void setSecurityPolicy(PermissionData... policy) {
        modifyPermissions(permissionsAdmin -> {
            ConditionalPermissionUpdate permissionsUpdate = permissionsAdmin.newConditionalPermissionUpdate();
            List<ConditionalPermissionInfo> permissions = permissionsUpdate.getConditionalPermissionInfos();
            permissions.clear();
            permissions.addAll(Arrays.stream(policy).map(p -> p.toPermissionInfo(permissionsAdmin)).collect(toUnmodifiableList()));
            assertTrue(permissionsUpdate.commit(), "Failed to update security policy.");
        });
    }

    private void modifyPermissions(Consumer<ConditionalPermissionAdmin> permissionsAction) {
        ServiceReference<ConditionalPermissionAdmin> reference = bundleContext.getServiceReference(ConditionalPermissionAdmin.class);
        assertNotNull(reference, "No ConditionalPermissionAdmin service declared.");
        ConditionalPermissionAdmin permissionsAdmin = bundleContext.getService(reference);
        assertNotNull(permissionsAdmin, "Cannot fetch ConditionalPermissionAdmin service.");
        try {
            permissionsAction.accept(permissionsAdmin);
        } finally {
            bundleContext.ungetService(reference);
        }
    }

    private static class PermissionData {
        private final String type;
        private final String filter;
        private final Collection<Permission> permissions;

        PermissionData(String type, String filter, Collection<Permission> permissions) {
            this.type = type;
            this.filter = filter;
            this.permissions = permissions;
        }

        ConditionalPermissionInfo toPermissionInfo(ConditionalPermissionAdmin permissionsAdmin) {
            ConditionInfo condition = new ConditionInfo(BundleLocationCondition.class.getName(), new String[] { filter });
            PermissionInfo[] permissionInfos = permissions.stream().map(p ->
                new PermissionInfo(p.getClass().getName(), p.getName(), p.getActions())
            ).toArray(PermissionInfo[]::new);
            return permissionsAdmin.newConditionalPermissionInfo(null, new ConditionInfo[] { condition }, permissionInfos, type);
        }
    }
}
