package org.testing.osgi;

import co.paralleluniverse.fibers.instrument.MethodDatabase;
import co.paralleluniverse.fibers.instrument.QuasarInstrumentor;
import co.paralleluniverse.fibers.instrument.Retransform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.service.ServiceExtension;
import org.testing.osgi.base.BaseException;
import org.testing.osgi.base.OsgiException;
import org.testing.osgi.exception.first.FirstException;
import org.testing.osgi.exception.second.SecondException;
import org.testing.osgi.security.SecurityConfig;
import org.testing.osgi.unprivileged.Unprivileged;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.security.SecurityConfig.ADMIN_PERMISSIONS;
import static org.testing.osgi.security.SecurityConfig.ALL_PERMISSIONS;

@ExtendWith(ServiceExtension.class)
@TestInstance(PER_CLASS)
class BundleLocatorTest {
    private final QuasarInstrumentor instrumentor = Retransform.getInstrumentor();

    @BeforeAll
    void setup(
        @InjectService(timeout = 1000)
        SecurityConfig securityConfig
    ) {
        securityConfig.setSecurityPolicy(
            // Any call-stack containing Unprivileged has only these permissions.
            securityConfig.allowFor(Unprivileged.class, ADMIN_PERMISSIONS),
            securityConfig.denyAllFor(Unprivileged.class),

            // Everyone else has all permissions.
            securityConfig.allow("*", ALL_PERMISSIONS)
        );
    }

    @ParameterizedTest
    @ValueSource(classes = { SecondException.class, FirstException.class, OsgiException.class, BaseException.class })
    void testBundleForClass(Class<?> clazz) {
        ClassLoader cl = getClass().getClassLoader();
        assertNotEquals(cl, clazz.getClassLoader());

        MethodDatabase db = instrumentor.getMethodDatabase(clazz.getClassLoader());
        assertClassesBelongToClassLoader(db.getClassNames(), clazz.getClassLoader());
    }

    @Test
    void testClassesInBootstrapDB() {
        MethodDatabase db = instrumentor.getMethodDatabase(null);
        assertClassesBelongToClassLoader(db.getClassNames(), null);
    }

    private void assertClassesBelongToClassLoader(Collection<String> classNames, ClassLoader actual) {
        assertFalse(classNames.isEmpty(), "MethodDatabase for " + actual + " should not be empty.");

        final ClassLoader thisLoader = getClass().getClassLoader();
        classNames.forEach(className -> {
            final String actualClassName = className.replace('/', '.');
            try {
                final Class<?> dbClass = loadClass(actualClassName, thisLoader);
                final ClassLoader expected = dbClass.getClassLoader();
                assertEquals(expected, actual,
                    "Instrumented class " + actualClassName + " found in " + actual + " but belongs to " + expected);
            } catch (ClassNotFoundException e) {
                fail("Failed to load class " + actualClassName);
            }
        });
    }

    private static Class<?> loadClass(String className, ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            return Class.forName(className, false, ClassLoader.getSystemClassLoader());
        }
    }
}
