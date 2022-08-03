package co.paralleluniverse.fibers.instrument;

import java.security.BasicPermission;

public final class QuasarPermission extends BasicPermission {
    public static final String CONFIGURATION = "configuration";

    // OSGi requires this constructor.
    public QuasarPermission(String name, String actions) {
        super(name, actions);
    }

    public QuasarPermission(String name) {
        this(name, "");
    }
}
