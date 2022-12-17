package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.instrument.MethodDatabase.ClassEntry;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import java.util.Set;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToSlashed;

final class QuasarWeavingHook implements WeavingHook {
    private static final Set<String> INSTRUMENTATION_PACKAGES = Set.of(
        "co.paralleluniverse.fibers.suspend",
        "com.esotericsoftware.reflectasm"
    );

    private final QuasarInstrumentor instrumentor;

    QuasarWeavingHook(QuasarInstrumentor instrumentor) {
        this.instrumentor = instrumentor;
    }

    @Override
    public void weave(WovenClass wovenClass) {
        final ClassLoader classLoader = wovenClass.getBundleWiring().getClassLoader();
        if (instrumentor.shouldInstrument(classLoader)) {
            final String className = classToSlashed(wovenClass.getClassName());
            if (instrumentor.shouldInstrument(className)) {
                final ByteCodeTransformer transformer = instrumentor.adapt(this::transformByteCode, classLoader);
                wovenClass.setBytes(transformer.transform(classLoader, className, null, wovenClass.getBytes()));
                if (isInstrumented(classLoader, className)) {
                    wovenClass.getDynamicImports().addAll(INSTRUMENTATION_PACKAGES);
                }
            }
        }
    }

    private byte[] transformByteCode(ClassLoader loader, String className, Class<?> classBeingRedefined, byte[] classByteCode) {
        try {
            return instrumentor.instrumentClass(loader, className, classByteCode);
        } catch (Exception e) {
            instrumentor.error("while transforming " + className + ": " + e.getMessage(), e);
            return classByteCode;
        }
    }

    private boolean isInstrumented(ClassLoader classLoader, String className) {
        final ClassEntry entry = instrumentor.getMethodDatabase(classLoader).getClassEntry(className);
        return (entry == null) || entry.isInstrumented();
    }
}
