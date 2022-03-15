package org.testing.osgi.security;

import org.osgi.framework.AdminPermission;

import java.security.AllPermission;
import java.security.Permission;
import java.util.Collection;

import static java.util.Collections.singletonList;

@SuppressWarnings("unused")
public interface SecurityConfig {
    void setSecurityPolicy(PermissionData... policy);
    void allowAll();

    PermissionData allow(String locationFilter, Collection<Permission> permissions);
    PermissionData allowFor(Class<?> clazz, Collection<Permission> permissions);
    PermissionData deny(String locationFilter, Collection<Permission> permissions);
    PermissionData denyAllFor(Class<?> clazz);

    Collection<Permission> ALL_PERMISSIONS = singletonList(new AllPermission());
    Collection<Permission> ADMIN_PERMISSIONS = singletonList(new AdminPermission());
}
