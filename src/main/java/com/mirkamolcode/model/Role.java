package com.mirkamolcode.model;

import java.util.EnumSet;
import java.util.Set;

public enum Role {
    USER(EnumSet.of(
            Permission.SUBSCRIPTION_CREATE,
            Permission.SUBSCRIPTION_READ_OWN,
            Permission.SUBSCRIPTION_UPDATE_OWN,
            Permission.SUBSCRIPTION_DELETE_OWN,
            Permission.STATISTICS_READ_OWN
    )),
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
