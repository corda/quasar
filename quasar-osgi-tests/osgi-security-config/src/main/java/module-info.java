module org.testing.osgi.security {
    requires static org.osgi.service.component.annotations;
    requires static osgi.annotation;
    requires osgi.core;
    requires org.junit.jupiter.api;

    exports org.testing.osgi.security;
}
