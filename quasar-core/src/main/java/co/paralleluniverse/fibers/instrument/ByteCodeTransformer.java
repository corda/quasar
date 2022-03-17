package co.paralleluniverse.fibers.instrument;

@FunctionalInterface
interface ByteCodeTransformer {
    byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, byte[] byteCode);
}
