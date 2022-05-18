package co.paralleluniverse.common.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Objects;

import static co.paralleluniverse.common.asm.ASMUtil.ASMAPI;
import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToResource;
import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToSlashed;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

public final class ClassFactory {
    private ClassFactory() {
    }

    // Create a brand new class by renaming an existing class.
    @SuppressWarnings("SameParameterValue")
    public static Class<?> renameTo(Class<?> template, String className, ClassLoader parent) throws IOException {
        final String templateResourceName = classToResource(template);
        final URL templateResource = template.getClassLoader().getResource(templateResourceName);
        if (templateResource == null) {
            throw new FileNotFoundException(templateResourceName + " not found");
        }

        final byte[] byteCode = templateResource.openStream().readAllBytes();
        final ClassWriter writer = new ClassWriter(COMPUTE_MAXS);
        new ClassReader(byteCode).accept(new RenameVisitor(classToSlashed(className), writer), SKIP_DEBUG | SKIP_FRAMES);
        return new ByteCodeClassLoader(className, writer.toByteArray(), template.getProtectionDomain(), parent).createClass();
    }

    private static class RenameVisitor extends ClassVisitor {
        private final String newClassName;

        RenameVisitor(String newClassName, ClassVisitor visitor) {
            super(ASMAPI, visitor);
            this.newClassName = newClassName;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, newClassName, signature, superName, interfaces);
        }
    }

    private static final class ByteCodeClassLoader extends ClassLoader {
        private final String resourceName;
        private final String className;
        private final byte[] byteCode;
        private final ProtectionDomain pd;

        ByteCodeClassLoader(String className, byte[] byteCode, ProtectionDomain pd, ClassLoader parent) {
            super(parent);
            this.resourceName = classToResource(className);
            this.className = className;
            this.byteCode = byteCode;
            this.pd = pd;
        }

        Class<?> createClass() {
            Class<?> clazz = defineClass(className, byteCode, 0, byteCode.length, pd);
            resolveClass(clazz);
            return clazz;
        }

        @Override
        protected URL findResource(String name) {
            if (resourceName.equals(name)) {
                try {
                    return new URL("file", "bytecode", resourceName);
                } catch (MalformedURLException ignored) {
                }
            }
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            Objects.requireNonNull(name);
            final URL resource = getResource(name);
            if (resource != null) {
                if ("file".equals(resource.getProtocol())
                        && "bytecode".equals(resource.getHost())
                        && resourceName.equals(resource.getFile())) {
                    return new ByteArrayInputStream(byteCode);
                } else {
                    try {
                        return resource.openStream();
                    } catch (IOException ignored) {
                    }
                }
            }
            return null;
        }
    }
}
