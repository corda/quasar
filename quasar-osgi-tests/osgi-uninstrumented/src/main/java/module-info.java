module org.testing.osgi.uninstrumented {
    requires static osgi.annotation;
    requires static co.paralleluniverse.quasar.osgi.annotations;
    requires static co.paralleluniverse.quasar.core.osgi;

    exports org.testing.osgi.uninstrumented;
}
