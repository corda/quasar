module org.testing.osgi.supers {
    requires static osgi.annotation;
    requires static org.testing.osgi.annotation;
    requires org.testing.osgi.base;

    exports org.testing.osgi.supers;
}
