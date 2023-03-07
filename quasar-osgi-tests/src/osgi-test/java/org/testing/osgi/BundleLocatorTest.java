package org.testing.osgi;

import co.paralleluniverse.fibers.instrument.MethodDatabase;
import co.paralleluniverse.fibers.instrument.QuasarInstrumentor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testing.osgi.base.BaseException;
import org.testing.osgi.base.OsgiException;
import org.testing.osgi.exception.first.FirstException;
import org.testing.osgi.exception.second.SecondException;
import org.testing.osgi.security.SecurityConfig;
import org.testing.osgi.unprivileged.Unprivileged;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.security.SecurityConfig.ADMIN_PERMISSIONS;
import static org.testing.osgi.security.SecurityConfig.ALL_PERMISSIONS;

@ExtendWith({ ServiceExtension.class, BundleContextExtension.class })
@TestInstance(PER_CLASS)
class BundleLocatorTest {
    private static final String SUPER_CLASS_ACTION_CLASS_NAME = "org.testing.osgi.supers.SuperClassAction";
    private static final Logger LOGGER = LoggerFactory.getLogger(BundleLocatorTest.class);

    private QuasarInstrumentor instrumentor;

    @BeforeAll
    void setup(
        @InjectService(timeout = 1000)
        SecurityConfig securityConfig,

        @InjectService(timeout = 1000)
        QuasarInstrumentor instrumentor
    ) {
        securityConfig.setSecurityPolicy(
            // Any call-stack containing Unprivileged has only these permissions.
            securityConfig.allowFor(Unprivileged.class, ADMIN_PERMISSIONS),
            securityConfig.denyAllFor(Unprivileged.class),

            // Everyone else has all permissions.
            securityConfig.allow("*", ALL_PERMISSIONS)
        );
        this.instrumentor = instrumentor;
    }

    @ParameterizedTest
    @ValueSource(classes = {
        BundleException.class,
        SecondException.class,
        FirstException.class,
        OsgiException.class,
        BaseException.class,
        Exception.class,
        Throwable.class
    })
    void testBundleForClass(Class<?> clazz) {
        final ClassLoader testLoader = getClass().getClassLoader();
        assertNotEquals(testLoader, clazz.getClassLoader());

        final ClassLoader cl = getClassLoaderFor(clazz);
        final MethodDatabase db = instrumentor.getMethodDatabase(cl);
        assertClassesBelongToClassLoader(db.getClassNames(), cl);
    }

    @Test
    void testSuperClassForResolvedBundle(@InjectBundleContext BundleContext bundleContext) throws Exception {
        final Bundle supers = bundleContext.installBundle("FLOW/osgi-super-classes", getSuperClassesJar());
        try {
            // A RESOLVED bundle has no BundleContext.
            assertSuperClassesFor(supers);
        } finally {
            supers.uninstall();
        }
    }

    @Test
    void testSuperClassForActiveBundle(@InjectBundleContext BundleContext bundleContext) throws Exception {
        final Bundle supers = bundleContext.installBundle("FLOW/osgi-super-classes", getSuperClassesJar());
        try {
            // Bundle has no BundleContext until we start it.
            supers.start();

            assertSuperClassesFor(supers);
        } finally {
            supers.uninstall();
        }
    }

    private void assertSuperClassesFor(final Bundle supers) throws Exception {
        @SuppressWarnings("unchecked")
        final Class<? extends Function<MethodHandle, Throwable>> testClass = Unprivileged.doUnprivileged(() ->
            (Class<? extends Function<MethodHandle, Throwable>>) supers.loadClass(SUPER_CLASS_ACTION_CLASS_NAME)
        );

        assertAll("Superclass mapping allocations",
            () -> assertSuperClassesForClassLoader(getClassLoaderFor(testClass)),
            // Locating classes inside the framework classloader requires:
            //     org.osgi.framework.bundle.parent=framework
            () -> assertSuperClassesForClassLoader(getClassLoaderFor(Bundle.class)),
            () -> assertSuperClassesForClassLoader(ClassLoader.getPlatformClassLoader())
        );
    }

    private void assertSuperClassesForClassLoader(ClassLoader cl) {
        final MethodDatabase db = instrumentor.getMethodDatabase(cl);
        assertClassesBelongToClassLoader(db.getClassNamesForSuperClasses(), cl);
    }

    private void assertClassesBelongToClassLoader(Collection<String> classNames, ClassLoader actual) {
        LOGGER.info("Checking classes {} for classloader {}", classNames, actual);
        assertFalse(classNames.isEmpty(), "MethodDatabase for " + actual + " should not be empty.");
        assertAll("Mapping for " + actual, classNames.stream().map(className -> {
            final String actualClassName = className.replace('/', '.');
            return () -> {
                try {
                    final Class<?> dbClass = loadClass(actualClassName, actual);
                    final ClassLoader expected = getClassLoaderFor(dbClass);
                    assertEquals(expected, actual,
                        "Instrumented class " + actualClassName + " found in " + actual + " but belongs to " + expected);
                } catch (ClassNotFoundException e) {
                    fail("Failed to load class " + actualClassName);
                }
            };
        }));
    }

    private static Class<?> loadClass(String className, ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            return Class.forName(className, false, ClassLoader.getPlatformClassLoader());
        }
    }

    private static ClassLoader getClassLoaderFor(Class<?> clazz) {
        final ClassLoader cl = clazz.getClassLoader();
        return (cl != null) ? cl : ClassLoader.getPlatformClassLoader();
    }

    private InputStream getSuperClassesJar() {
        InputStream input = getClass().getClassLoader().getResourceAsStream("META-INF/osgi-super-classes.jar");
        assertNotNull(input, "Bundle resource not found?!");
        return input;
    }
}
