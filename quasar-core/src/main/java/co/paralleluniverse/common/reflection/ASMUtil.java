/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.common.reflection;

import static co.paralleluniverse.asm.ASMUtil.getClassNode;
import static co.paralleluniverse.common.reflection.ClassLoaderUtil.classToResource;
import static co.paralleluniverse.common.reflection.ClassLoaderUtil.classToSlashed;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 *
 * @author pron
 */
public final class ASMUtil {
    public static InputStream getClassInputStream(String className, ClassLoader cl) {
        final String resource = classToResource(className);
        return cl != null ? cl.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource);
    }

    public static InputStream getClassInputStream(Class<?> clazz) {
        final InputStream is = getClassInputStream(clazz.getName(), clazz.getClassLoader());
        if (is == null)
            throw new UnsupportedOperationException("Class file " + clazz.getName() + " could not be loaded by the class's classloader " + clazz.getClassLoader());
        return is;
    }

    public static boolean isAssignableFrom(Class<?> supertype, String className, ClassLoader cl) {
        return isAssignableFrom0(classToSlashed(supertype), classToSlashed(className), cl);
    }

    public static boolean isAssignableFrom(String supertypeName, String className, ClassLoader cl) {
        return isAssignableFrom0(classToSlashed(supertypeName), classToSlashed(className), cl);
    }

    private static boolean isAssignableFrom0(String supertypeName, String className, ClassLoader cl) {
        try {
            if (className == null)
                return false;
            if (supertypeName.equals(className))
                return true;
            ClassNode cn = getClassNode(className, cl, true);

            if (supertypeName.equals(cn.superName))
                return true;
            if (isAssignableFrom0(supertypeName, cn.superName, cl))
                return true;

            if (cn.interfaces != null) {
                for (String iface : (List<String>) cn.interfaces) {
                    if (supertypeName.equals(iface))
                        return true;
                    if (isAssignableFrom0(supertypeName, iface, cl))
                        return true;
                }
            }
            return false;
        } catch (IOException e) {
            // e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    public static String getDescriptor(Member m) {
        if (m instanceof Method)
            return Type.getMethodDescriptor((Method) m);
        if (m instanceof Constructor)
            return Type.getConstructorDescriptor((Constructor) m);
        throw new IllegalArgumentException("Not an executable: " + m);
    }
    
    public static String getReadableDescriptor(String descriptor) {
        Type[] types = Type.getArgumentTypes(descriptor);
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(types[i].getClassName());
        }
        sb.append(")");
        return sb.toString();
    }
    
    private ASMUtil() {
    }
}
