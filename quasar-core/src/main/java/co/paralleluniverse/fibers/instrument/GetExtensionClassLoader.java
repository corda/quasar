package co.paralleluniverse.fibers.instrument;

import java.security.PrivilegedAction;

final class GetExtensionClassLoader implements PrivilegedAction<ClassLoader> {
    @Override
    public ClassLoader run() {
        return ClassLoader.getSystemClassLoader().getParent();
    }
}
