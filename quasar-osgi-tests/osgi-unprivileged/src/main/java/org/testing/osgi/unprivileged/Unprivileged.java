package org.testing.osgi.unprivileged;

import java.security.PrivilegedExceptionAction;

@SuppressWarnings("unused")
public final class Unprivileged {
    public static <T> T doUnprivileged(PrivilegedExceptionAction<T> action) throws Exception {
        return new UnprivilegedAction<>(action).run();
    }

    private Unprivileged() {
    }

    private static final class UnprivilegedAction<T> implements PrivilegedExceptionAction<T> {
        private final PrivilegedExceptionAction<T> action;

        UnprivilegedAction(PrivilegedExceptionAction<T> action) {
            this.action = action;
        }

        @Override
        public T run() throws Exception {
            return action.run();
        }
    }
}
