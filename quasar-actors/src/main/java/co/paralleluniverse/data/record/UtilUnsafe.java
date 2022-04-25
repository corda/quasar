package co.paralleluniverse.data.record;

import java.lang.reflect.Field;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import sun.misc.Unsafe;

import static java.security.AccessController.doPrivileged;

/**
 * Simple class to obtain access to the {@link Unsafe} object. {@link Unsafe}
 * is required to allow efficient CAS operations on arrays. Note that the
 * versions in {@code java.util.concurrent.atomic}, such as {@link
 * java.util.concurrent.atomic.AtomicLongArray}, require extra memory ordering
 * guarantees which are generally not needed in these algorithms and are also
 * expensive on most processors.
 *
 * DO NOT MAKE THIS CLASS PUBLIC!
 */
final class UtilUnsafe {
    private UtilUnsafe() {
    }

    static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return (Unsafe) doPrivileged((PrivilegedExceptionAction<?>) () -> {
                    final Field f = Unsafe.class.getDeclaredField("theUnsafe");
                    f.setAccessible(true);
                    return f.get(null);
                });
            } catch (PrivilegedActionException e) {
                throw new SecurityException("Could not initialize intrinsics", e.getCause());
            }
        }
    }
}
