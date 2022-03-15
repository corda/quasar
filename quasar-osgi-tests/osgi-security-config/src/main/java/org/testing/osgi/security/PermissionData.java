package org.testing.osgi.security;

import org.osgi.service.condpermadmin.BundleLocationCondition;
import org.osgi.service.condpermadmin.ConditionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.permissionadmin.PermissionInfo;

import java.security.Permission;
import java.util.Collection;

public class PermissionData {
    private final String type;
    private final String filter;
    private final Collection<Permission> permissions;

    public PermissionData(String type, String filter, Collection<Permission> permissions) {
        this.type = type;
        this.filter = filter;
        this.permissions = permissions;
    }

    public ConditionalPermissionInfo toPermissionInfo(ConditionalPermissionAdmin permissionsAdmin) {
        ConditionInfo condition = new ConditionInfo(BundleLocationCondition.class.getName(), new String[] { filter });
        PermissionInfo[] permissionInfos = permissions.stream().map(p ->
                new PermissionInfo(p.getClass().getName(), p.getName(), p.getActions())
        ).toArray(PermissionInfo[]::new);
        return permissionsAdmin.newConditionalPermissionInfo(null, new ConditionInfo[] { condition }, permissionInfos, type);
    }
}
