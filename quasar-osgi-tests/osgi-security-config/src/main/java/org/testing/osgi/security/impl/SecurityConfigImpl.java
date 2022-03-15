package org.testing.osgi.security.impl;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;
import org.testing.osgi.security.PermissionData;
import org.testing.osgi.security.SecurityConfig;

import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static java.security.AccessController.doPrivileged;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW;
import static org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY;

@SuppressWarnings("unused")
@Component
public class SecurityConfigImpl implements SecurityConfig {
    private final BundleContext bundleContext;

    @Activate
    public SecurityConfigImpl(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Deactivate
    void shutdown() {
        System.err.println(">> Deactivating SecurityConfig");
        allowAll();
    }

    @Override
    public PermissionData allow(String locationFilter, Collection<Permission> permissions) {
        return new PermissionData(ALLOW, locationFilter, permissions);
    }

    @Override
    public PermissionData allowFor(Class<?> clazz, Collection<Permission> permissions) {
        Bundle bundle = FrameworkUtil.getBundle(clazz);
        assertNotNull(bundle, "Class " + clazz.getName() + " does not belong to an OSGi bundle.");
        return new PermissionData(ALLOW, bundle.getLocation(), permissions);
    }

    @Override
    public PermissionData deny(String locationFilter, Collection<Permission> permissions) {
        return new PermissionData(DENY, locationFilter, permissions);
    }

    @Override
    public PermissionData denyAllFor(Class<?> clazz) {
        Bundle bundle = FrameworkUtil.getBundle(clazz);
        assertNotNull(bundle, "Class " + clazz.getName() + " does not belong to an OSGi bundle.");
        return new PermissionData(DENY, bundle.getLocation(), ALL_PERMISSIONS);
    }

    @Override
    public void allowAll() {
        setSecurityPolicy(allow("*", ALL_PERMISSIONS));
    }

    @Override
    public void setSecurityPolicy(PermissionData... policy) {
        doPrivileged((PrivilegedAction<?>) () -> {
            modifyPermissions(permissionsAdmin -> {
                ConditionalPermissionUpdate permissionsUpdate = permissionsAdmin.newConditionalPermissionUpdate();
                List<ConditionalPermissionInfo> permissions = permissionsUpdate.getConditionalPermissionInfos();
                permissions.clear();
                permissions.addAll(Arrays.stream(policy).map(p -> p.toPermissionInfo(permissionsAdmin)).collect(toUnmodifiableList()));
                assertTrue(permissionsUpdate.commit(), "Failed to update security policy.");
            });
            return null;
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
}
