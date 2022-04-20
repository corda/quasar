/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2018, Parallel Universe Software Co. All rights reserved.
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
 /*
 * Copyright (c) 2008-2013, Matthias Mann
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package co.paralleluniverse.fibers.instrument;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.getBestClassLoader;
import static co.paralleluniverse.fibers.instrument.Classes.isYieldMethod;
import static java.security.AccessController.doPrivileged;

/**
 * <p>
 * Collects information about classes and their suspendable methods.</p>
 * <p>
 * Provides access to configuration parameters and to logging</p>
 *
 * @author Matthias Mann
 * @author pron
 */
public final class MethodDatabase {
    public static final String JRT_PROTOCOL = "jrt";

    private static final String JAVA_OBJECT = "java/lang/Object";

    private final WeakReference<ClassLoader> clRef;
    private final SuspendableClassifier classifier;
    private final NavigableMap<String, ClassEntry> classes;
    private final Map<String, String> superClasses;
    private final QuasarInstrumentor instrumentor;

    MethodDatabase(QuasarInstrumentor instrumentor, ClassLoader classloader, SuspendableClassifier classifier) {
        if (classloader == null) {
            throw new IllegalArgumentException("classloader cannot be null");
        }

        this.instrumentor = instrumentor;
        this.clRef = new WeakReference<>(classloader);
        this.classifier = classifier;

        classes = new TreeMap<>();
        superClasses = new HashMap<>();
    }

    boolean isAllowMonitors() {
        return instrumentor.isAllowMonitors();
    }

    boolean isAllowBlocking() {
        return instrumentor.isAllowBlocking();
    }

    public SuspendableClassifier getClassifier() {
        return classifier;
    }

    public void log(LogLevel level, String msg, Object... args) {
        instrumentor.log(level, msg, args);
    }

    public void error(String msg, Throwable ex) {
        instrumentor.error(msg, ex);
    }

    public boolean isDebug() {
        return instrumentor.isDebug();
    }

    public boolean isVerbose() {
        return instrumentor.isVerbose();
    }

    public Log getLog() {
        return instrumentor.getLog();
    }

    public Set<String> getClassNames() {
        synchronized(classes) {
            // Create a "snapshot" of the current set of class names.
            return new HashSet<>(classes.keySet());
        }
    }

    public Set<String> getClassNamesForSuperClasses() {
        synchronized(superClasses) {
            // Create a "snapshot" of the current set of class names.
            return new HashSet<>(superClasses.keySet());
        }
    }

    public String checkClass(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            final CheckInstrumentationVisitor civ = checkFileAndClose(fis);

            recordSuspendableMethods(civ.getName(), civ.getClassEntry());

            if (civ.needsInstrumentation()) {
                if (civ.isAlreadyInstrumented()) {
                    log(LogLevel.INFO, "Found instrumented class: %s", f.getPath());
                    if (JavaAgent.isActive()) {
                        throw new AssertionError();
                    }
                } else {
                    log(LogLevel.INFO, "Found class: %s", f.getPath());
                    return civ.getName();
                }
            }
            return null;
        } catch (UnableToInstrumentException ex) {
            throw ex;
        } catch (Exception ex) {
            error(f.getPath(), ex);
            return null;
        }
    }

    private static final int UNKNOWN = 0;
    private static final int JDK = 1;
    private static final int NONSUSPENDABLE = 2;
    private static final int SUSPENDABLE_ABSTRACT = 3;
    private static final int SUSPENDABLE = 4;

    public SuspendableType isMethodSuspendable(String className, String methodName, String methodDesc, int opcode) {
        if (className.startsWith("org/netbeans/lib/")) {
            log(LogLevel.INFO, "Method: %s#%s marked non-suspendable because it is Netbeans library", className, methodName);
            return SuspendableType.NON_SUSPENDABLE;
        }

        final int res = isMethodSuspendable0(className, methodName, methodDesc, opcode);
        switch (res) {
            case UNKNOWN:
                return null;
            case JDK:
                if (!className.startsWith("java/")) {
                    log(LogLevel.INFO, "Method: %s#%s not in 'java' package but marked non-suspendable anyway because it is a probably part of the JDK", className, methodName);
                }
            // fallthrough
            case NONSUSPENDABLE:
                return SuspendableType.NON_SUSPENDABLE;
            case SUSPENDABLE_ABSTRACT:
                return SuspendableType.SUSPENDABLE_SUPER;
            case SUSPENDABLE:
                return SuspendableType.SUSPENDABLE;
            default:
                throw new AssertionError();
        }
    }

    Pair<MethodDatabase, ClassEntry> getOrLoadClassEntry(String className) {
        if (className.startsWith("[")) {
            // Don't try looking for an "array" class.
            return null;
        }

        final ClassEntry entry = getClassEntry(className);
        if (entry != null) {
            return new Pair<>(this, entry);
        } else {
            final ClassLookup lookup = getLookupFor(className);
            if (lookup == null || lookup.getResource() == null) {
                log(LogLevel.INFO, "Class not found: %s", className);
                return null;
            } else {
                final MethodDatabase ownerDB = lookup.toOwnerDB(this);
                return new Pair<>(ownerDB, ownerDB.fetchClassEntry(className, lookup.getResource()));
            }
        }
    }

    private int isMethodSuspendable0(String className, String methodName, String methodDesc, int opcode) {
        if (methodName.charAt(0) == '<') {
            return NONSUSPENDABLE;   // special methods are never suspendable
        }

        if (isYieldMethod(className, methodName)) {
            return SUSPENDABLE;
        }

        final Pair<MethodDatabase, ClassEntry> dbEntry = getOrLoadClassEntry(className);
        if (dbEntry == null) {
            if (isJDK(className)) {
                return JDK;
            }
            return UNKNOWN;
        }

        final MethodDatabase ownerDB = dbEntry.getFirst();
        final ClassEntry entry = dbEntry.getSecond();
        final SuspendableType susp1 = entry.check(methodName, methodDesc);

        int suspendable = UNKNOWN;
        if (susp1 == SuspendableType.SUSPENDABLE) {
            suspendable = SUSPENDABLE;
        } else if (susp1 == SuspendableType.SUSPENDABLE_SUPER) {
            suspendable = SUSPENDABLE_ABSTRACT;
        } else if (susp1 == SuspendableType.NON_SUSPENDABLE) {
            suspendable = NONSUSPENDABLE;
        }

        if (suspendable == UNKNOWN) {
            if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL) {
                if (entry.getSuperName() != null) {
                    suspendable = ownerDB.isMethodSuspendable0(entry.getSuperName(), methodName, methodDesc, opcode);
                }
            }
            if (opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL) { // can be INVOKEVIRTUAL on an abstract class implementing the interface
                for (final String iface : entry.getInterfaces()) {
                    int s = ownerDB.isMethodSuspendable0(iface, methodName, methodDesc, opcode);
                    if (s > suspendable) {
                        suspendable = s;
                    }
                    if (suspendable > JDK) {
                        break;
                    }
                }
            }
        }

        return suspendable;
    }

    public ClassEntry getClassEntry(String className) {
        synchronized(classes) {
            return classes.get(className);
        }
    }

    public ClassEntry getOrCreateClassEntry(String className, String superType) {
        synchronized(classes) {
            ClassEntry ce = classes.get(className);
            if (ce == null) {
                ce = new ClassEntry(superType);
                classes.put(className, ce);
            }
            return ce;
        }
    }

    // this method is used by Pulsar
    public Map<String, ClassEntry> getInnerClassesEntries(String className) {
        synchronized(classes) {
            final Map<String, ClassEntry> tailMap = classes.tailMap(className, true);
            final Map<String, ClassEntry> map = new HashMap<>();
            for (Map.Entry<String, ClassEntry> entry : tailMap.entrySet()) {
                if (entry.getKey().equals(className) || entry.getKey().startsWith(className + '$')) {
                    map.put(entry.getKey(), entry.getValue());
                }
            }
            return Collections.unmodifiableMap(map);
        }
    }

    void recordSuspendableMethods(String className, ClassEntry entry) {
        ClassEntry oldEntry;
        synchronized(classes) {
            oldEntry = classes.put(className, entry);
        }
        if (oldEntry != null && oldEntry != entry) {
            if (!oldEntry.equals(entry)) {
                log(LogLevel.WARNING, "Duplicate class entries with different data for class: %s", className);
            }
        }
    }

    String getCommonSuperClass(String classA, String classB) {
        if (JAVA_OBJECT.equals(classA) || JAVA_OBJECT.equals(classB)) {
            // If one of these two classes is java.lang.Object
            // then we know that their common super class must
            // also be java.lang.Object. No need to examine any
            // byte-code!
            return JAVA_OBJECT;
        }
        final List<String> listA = getSuperClasses(classA);
        final List<String> listB = getSuperClasses(classB);
        if (listA == null || listB == null) {
            return null;
        }
        final int num = Math.min(listA.size(), listB.size());
        int idx = 0;
        for (; idx < num; idx++) {
            final String superClassA = listA.get(idx);
            final String superClassB = listB.get(idx);
            if (!superClassA.equals(superClassB)) {
                break;
            }
        }
        if (idx > 0) {
            return listA.get(idx - 1);
        }
        return null;
    }

    public boolean isException(final String className) {
        String currentClassName = className;
        MethodDatabase currentDB = this;
        for (;;) {
            if ("java/lang/Throwable".equals(currentClassName)) {
                return true;
            }

            if (JAVA_OBJECT.equals(currentClassName)) {
                return false;
            }

            String superClass = null;
            final ClassLookup lookup = currentDB.getLookupFor(currentClassName);
            if (lookup != null) {
                final URL resource = lookup.getResource();
                if (resource != null) {
                    currentDB = lookup.toOwnerDB(currentDB);
                    superClass = currentDB.getDirectSuperClass(currentClassName, resource);
                }
            }
            if (superClass == null) {
                log(isProblematicClass(currentClassName) ? LogLevel.INFO : LogLevel.WARNING,
                    "Can't determine super class of %s (this is usually related to classloading)", currentClassName);
                return false;
            }
            currentClassName = superClass;
        }
    }

    private ClassEntry fetchClassEntry(String className, URL resource) {
        ClassEntry entry = getClassEntry(className);
        if (entry == null) {
            log(LogLevel.INFO, "Reading class: %s", className);
            try (final InputStream is = privilegedOpenInputStream(resource)) {
                entry = checkFileAndClose(is).getClassEntry();
                recordSuspendableMethods(className, entry);
            } catch(IOException e) {
                throw new UncheckedIOException("While opening " + className, e);
            }
        }
        return entry;
    }

    private InputStream privilegedOpenInputStream(URL resource) {
        return doPrivileged((PrivilegedAction<InputStream>)() -> {
            try {
                final URLConnection uc = resource.openConnection();
                uc.setUseCaches(false);
                return uc.getInputStream();
            } catch(IOException e) {
                final String message = "While opening " + resource;
                error(message, e);
                throw new UncheckedIOException(message, e);
            }
        });
    }

    private CheckInstrumentationVisitor checkFileAndClose(InputStream is) throws IOException {
        try (is) {
            final ClassReader r = new ClassReader(is);

            final CheckInstrumentationVisitor civ = new CheckInstrumentationVisitor(this);
            r.accept(civ, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

            return civ;
        }
    }

    private List<String> getSuperClasses(final String className) {
        final LinkedList<String> result = new LinkedList<>();
        String currentClassName = className;
        MethodDatabase currentDB = this;
        for (;;) {
            result.addFirst(currentClassName);
            if (JAVA_OBJECT.equals(currentClassName)) {
                return result;
            }

            String superClass = null;
            final ClassLookup lookup = currentDB.getLookupFor(currentClassName);
            if (lookup != null) {
                final URL resource = lookup.getResource();
                if (resource != null) {
                    currentDB = lookup.toOwnerDB(currentDB);
                    superClass = currentDB.getDirectSuperClass(currentClassName, resource);
                }
            }
            if (superClass == null) {
                log(isProblematicClass(currentClassName) ? LogLevel.INFO : LogLevel.WARNING,
                    "Can't determine super class of %s", currentClassName);
                return null;
            }
            currentClassName = superClass;
        }
    }

    private ClassLookup getLookupFor(String className) {
        final ClassLoader cl = clRef.get();
        return (cl == null) ? null : new ClassLookup(cl, className);
    }

    private String getDirectSuperClass(String className, URL resource) {
        final ClassEntry entry = getClassEntry(className);
        if (entry != null) {
            return entry.getSuperName();
        }

        String superClass;
        synchronized(superClasses) {
            superClass = superClasses.get(className);
        }
        if (superClass == null) {
            try (final InputStream is = privilegedOpenInputStream(resource)) {
                superClass = ExtractSuperClass.extractFrom(is);
                if (superClass != null) {
                    final String oldSuperClass;
                    synchronized(superClasses) {
                        oldSuperClass = superClasses.put(className, superClass);
                    }
                    if (oldSuperClass != null && !oldSuperClass.equals(superClass)) {
                        log(LogLevel.WARNING, "Duplicate super class entry with different value: %s vs %s", oldSuperClass, superClass);
                    }
                }
            } catch(IOException ex) {
                error(className, ex);
            }
        }
        return superClass;
    }

    private static final class ClassLookup {
        private final ClassLoader classloader;
        private final String resourceName;
        private final URL resource;

        ClassLookup(ClassLoader cl, String internalClassName) {
            classloader = cl;
            resourceName = internalClassName + ".class";
            resource = doPrivileged((PrivilegedAction<URL>)() -> cl.getResource(resourceName));
        }

        URL getResource() {
            return resource;
        }

        MethodDatabase toOwnerDB(MethodDatabase db) {
            // Identify which classloader actually contains the byte-code for this class,
            // because its ClassEntry should belong to that classloader's MethodDatabase.
            final ClassLoader ownerCl = doPrivileged((PrivilegedAction<? extends ClassLoader>) () ->
                OSGiClassLoader.findResourceOwner(classloader).locate(classloader, resourceName, resource)
            );
            return (ownerCl == classloader) ? db : db.instrumentor.getMethodDatabase(ownerCl);
        }
    }

    public static boolean isReflectInvocation(String className, String methodName) {
        return "java/lang/reflect/Method".equals(className) && "invoke".equals(methodName);
    }

    public static boolean isSyntheticAccess(String className, String methodName) {
        return methodName.startsWith("access$");
    }

    public static boolean isInvocationHandlerInvocation(String className, String methodName) {
        return className.equals("java/lang/reflect/InvocationHandler") && methodName.equals("invoke");
    }

    public static boolean isMethodHandleInvocation(String className, String methodName) {
        return className.equals("java/lang/invoke/MethodHandle") && methodName.startsWith("invoke");
    }

    public static boolean isJDK(String className) {
        return className.startsWith("java/")
               || isJavaxInternal(className)
               || className.startsWith("sun/")
               || className.startsWith("jdk/")
               || (className.startsWith("com/sun/") && !className.startsWith("com/sun/jersey"));
    }

    private static boolean isJavaxInternal(String className) {
        if (!className.startsWith("javax/")) {
            return false;
        }
        final String classSubName = className.substring("javax/".length());
        return classSubName.startsWith("crypto/")
                || classSubName.startsWith("management/")
                || classSubName.startsWith("net/");
    }

    public static boolean isProblematicClass(String className) {
        return className == null
               || className.startsWith("org/gradle/")
               || className.startsWith("javax/jms/")
               || className.startsWith("ch/qos/logback/")
               || className.startsWith("org/apache/logging/log4j/")
               || className.startsWith("org/apache/log4j/");
    }

    static ClassLoader getBestClassLoaderFor(ClassLoader cl, String resourceName, URL resource) {
        if (resource != null) {
            return doPrivileged((PrivilegedAction<? extends ClassLoader>)() -> {
                if (JRT_PROTOCOL.equals(resource.getProtocol())) {
                    // This resource is from the Java runtime base image.
                    return ClassLoader.getPlatformClassLoader();
                } else {
                    // Get the first classloader in the hierarchy that can provide this resource.
                    return getBestClassLoader(cl, resourceName, resource);
                }
            });
        }
        return cl;
    }

    public enum SuspendableType {
        NON_SUSPENDABLE, SUSPENDABLE_SUPER, SUSPENDABLE
    }

    public static final class ClassEntry {
        private final Map<String, SuspendableType> methods;
        private String sourceName;
        private String sourceDebugInfo;
        private boolean isInterface;
        private String[] interfaces;
        private final String superName;
        private boolean instrumented;
        private volatile boolean requiresInstrumentation;

        ClassEntry(String superName) {
            this.superName = superName;
            this.methods = new HashMap<>();
        }

        public void set(String name, String desc, SuspendableType suspendable) {
            String nameAndDesc = key(name, desc);
            methods.put(nameAndDesc, suspendable);
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = sourceName;
        }

        public String getSourceDebugInfo() {
            return sourceDebugInfo;
        }

        public void setSourceDebugInfo(String sourceDebugInfo) {
            this.sourceDebugInfo = sourceDebugInfo;
        }

        public boolean isInterface() {
            return isInterface;
        }

        public void setIsInterface(boolean isInterface) {
            this.isInterface = isInterface;
        }

        public String getSuperName() {
            return superName;
        }

        public void setAll(SuspendableType suspendable) {
            for (Map.Entry<String, SuspendableType> entry : methods.entrySet())
                entry.setValue(suspendable);
        }

        public String[] getInterfaces() {
            return interfaces;
        }

        public void setInterfaces(String[] interfaces) {
            this.interfaces = interfaces;
        }

        public SuspendableType check(String name, String desc) {
            return methods.get(key(name, desc));
        }

        // only for instrumentation verification
        public boolean isSuspendable(String name) {
            for (Map.Entry<String, SuspendableType> entry : methods.entrySet()) {
                String key = entry.getKey();
                if (key.substring(0, key.indexOf('(')).equals(name) && entry.getValue() != SuspendableType.NON_SUSPENDABLE)
                    return true;
            }
            return false;
        }

        public boolean requiresInstrumentation() {
            return requiresInstrumentation;
        }

        public void setRequiresInstrumentation(boolean requiresInstrumentation) {
            this.requiresInstrumentation = requiresInstrumentation;
        }

        @Override
        public int hashCode() {
            return superName.hashCode() * 67 + methods.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ClassEntry)) {
                return false;
            }
            final ClassEntry other = (ClassEntry) obj;
            // CORDA-3756 names can be null.
            return Objects.equals(superName, other.superName) && methods.equals(other.methods);
        }

        private static String key(String methodName, String methodDesc) {
            return methodName.concat(methodDesc);
        }

        public boolean isInstrumented() {
            return instrumented;
        }

        public void setInstrumented(boolean instrumented) {
            this.instrumented = instrumented;
        }
    }
}
