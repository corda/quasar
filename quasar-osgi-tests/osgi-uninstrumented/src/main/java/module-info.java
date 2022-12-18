module org.testing.osgi.uninstrumented {
    requires static osgi.annotation;
    requires static co.paralleluniverse.quasar.osgi.annotations;
    requires static org.testing.osgi.annotation;

    exports org.testing.osgi.uninstrumented;
}
