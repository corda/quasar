module co.paralleluniverse.quasar.core.framework.extension {
    requires static org.objectweb.asm;
    requires java.logging;

    exports co.paralleluniverse.common.asm;
    exports co.paralleluniverse.common.resource;
    exports co.paralleluniverse.fibers.instrument;
    exports co.paralleluniverse.fibers.suspend;

    opens co.paralleluniverse.fibers.suspend to co.paralleluniverse.quasar.core.osgi;
}
