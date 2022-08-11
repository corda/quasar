module co.paralleluniverse.quasar.core.osgi {
    requires java.management;
    requires jdk.unsupported; // needed for ThreadAccess and ExtendedStackTraceHotSpot

    requires co.paralleluniverse.quasar.core.framework.extension;

    // These are "automatic" module names, so keep them hidden!
    requires static kryo;
    requires static kryo.serializers;
    requires static objenesis;

    exports co.paralleluniverse.fibers;
    exports co.paralleluniverse.fibers.futures;
    exports co.paralleluniverse.fibers.io;
    exports co.paralleluniverse.remote;
    exports co.paralleluniverse.strands;
    exports co.paralleluniverse.strands.channels;
    exports co.paralleluniverse.strands.channels.transfer;
    exports co.paralleluniverse.strands.concurrent;
    exports co.paralleluniverse.strands.dataflow;
}

