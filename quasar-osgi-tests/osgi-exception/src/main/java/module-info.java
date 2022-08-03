module org.testing.osgi.exception {
    requires static osgi.annotation;
    requires org.testing.osgi.base;

    exports org.testing.osgi.exception.first;
    exports org.testing.osgi.exception.second;
}
