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
package co.paralleluniverse.fibers.instrument;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static co.paralleluniverse.common.resource.ClassLoaderUtil.classToSlashed;
import static co.paralleluniverse.fibers.instrument.LogLevel.DEBUG;
import static co.paralleluniverse.fibers.instrument.LogLevel.INFO;
import static co.paralleluniverse.fibers.instrument.LogLevel.WARNING;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * @author pron
 */
public final class QuasarInstrumentor {
    private static final String THIS_PACKAGE_NAME = "co.paralleluniverse.fibers.instrument.";
    private static final List<String> BUILT_IN_PACKAGES = List.of(
        "co/paralleluniverse/asm/",
        "co/paralleluniverse/common/asm/",
        "co/paralleluniverse/common/resource/",
        "co/paralleluniverse/fibers/suspend/",
        "org/osgi/framework/",
        "org/objectweb/asm/", // For testing
        "org/netbeans/lib/"
    );

    // Regex to exclude /
    private static final String DOT = "[^/]";

    private static boolean isBuiltInPackage(String className) {
        for (String packageName: BUILT_IN_PACKAGES) {
            if (className.startsWith(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static final String EXAMINED_CLASS = System.getProperty("co.paralleluniverse.fibers.writeInstrumentedClasses");
    private static final boolean allowJdkInstrumentation = isEmptyOrTrue("co.paralleluniverse.fibers.allowJdkInstrumentation");
    private final WeakHashMap<ClassLoader, MethodDatabase> dbForClassloader = new WeakHashMap<>();
    private boolean check;
    private boolean allowMonitors;
    private boolean allowBlocking;
    private final Collection<Pattern> exclusions = ConcurrentHashMap.newKeySet();
    private final Collection<Pattern> excludedClassLoaders = new ArrayList<>();
    private final Collection<Pattern> excludedBundleLocations = new ArrayList<>();
    private final Collection<Pattern> cachedBundleLocations = new ArrayList<>();
    private final ByteCodeCache byteCodeCache;
    private final Log log;
    private boolean verbose;
    private boolean debug;
    private int logLevelMask;

    static boolean isEmptyOrTrue(String value) {
        return (value != null) && (value.isEmpty() || Boolean.parseBoolean(value));
    }

    QuasarInstrumentor() {
        this(null, null);
    }

    QuasarInstrumentor(Log log) {
        this(null, log);
    }

    QuasarInstrumentor(ByteCodeCache byteCodeCache, Log log) {
        setLogLevelMask();
        this.byteCodeCache = byteCodeCache;
        this.log = log;
    }

    boolean shouldInstrument(ClassLoader loader) {
        return (loader == null) ||
            (!isExcludedClassLoader(loader.getClass().getName()) && !isExcludedClassLoader(loader));
    }

    boolean shouldInstrument(String className) {
        if (className != null) {
            className = className.replace('.', '/');
            if (className.startsWith("co/paralleluniverse/fibers/instrument/") && !Debug.isUnitTest()) {
                return false;
            } else if (className.equals(Classes.FIBER_CLASS_NAME) || className.startsWith(Classes.FIBER_CLASS_NAME + '$')) {
                return false;
            } else if (className.equals(Classes.STACK_NAME)) {
                return false;
            } else if (className.equals(Classes.FIBER_HELPER_NAME) || className.startsWith(Classes.FIBER_HELPER_NAME + '$')) {
                return false;
            } else if (isBuiltInPackage(className)) {
                return false;
            } else if (className.startsWith("java/lang/") || (!allowJdkInstrumentation && MethodDatabase.isJDK(className))) {
                return false;
            } else if (isExcluded(className)) {
                return false;
            }
        }
        return true;
    }

    byte[] instrumentClass(ClassLoader loader, String className, byte[] data) throws IOException {
        return instrumentClass(loader, className, new ByteArrayInputStream(data), false);
    }

    byte[] instrumentClass(ClassLoader loader, String className, InputStream is) throws IOException {
        return instrumentClass(loader, className, is, false);
    }

    byte[] instrumentClass(ClassLoader loader, String className, InputStream is, boolean forceInstrumentation) throws IOException {
        final String internalClassName = classToSlashed(className);

        byte[] cb = is.readAllBytes();

        final MethodDatabase db = getMethodDatabase(loader);

        if (internalClassName != null) {
            final MethodDatabase.ClassEntry classEntry = db.getClassEntry(internalClassName);
            log(INFO, "TRANSFORM: %s%s", className,
                (classEntry != null && classEntry.requiresInstrumentation()) ? " request" : "");

            examine(internalClassName, "quasar-1-preinstr", cb);
        } else {
            log(INFO, "TRANSFORM: null className");
        }

        // Phase 1, add a label before any suspendable calls, event API is enough
        final ClassReader r1 = new ClassReader(cb);
        final ClassWriter cw1 = new ClassWriter(r1, 0);
        final LabelSuspendableCallSitesClassVisitor ic1 = new LabelSuspendableCallSitesClassVisitor(cw1, db);
        r1.accept(ic1, 0);
        cb = cw1.toByteArray();

        examine(internalClassName, "quasar-2", cb);

        // Phase 2, instrument, tree API
        final ClassReader r2 = new ClassReader(cb);
        final ClassWriter cw2 = new DBClassWriter(db, r2);
        final ClassVisitor cv2 = (check && EXAMINED_CLASS == null) ? new CheckClassAdapter(cw2) : cw2;
        final InstrumentClass ic2 = new InstrumentClass(cv2, db, forceInstrumentation);
        try {
            r2.accept(ic2, ClassReader.SKIP_FRAMES);
            cb = cw2.toByteArray();
        } catch (final Exception e) {
            if (ic2.hasSuspendableMethods()) {
                error("Unable to instrument class " + className, e);
                throw e;
            } else {
                if (!MethodDatabase.isProblematicClass(internalClassName)) {
                    log(DEBUG, "Unable to instrument class %s", className);
                }
                return null;
            }
        }

        examine(internalClassName, "quasar-4", cb);

        // Phase 4, fill suspendable call offsets, event API is enough
        final OffsetClassReader r3 = new OffsetClassReader(cb);
        final ClassWriter cw3 = new ClassWriter(r3, 0);
        final SuspOffsetsAfterInstrClassVisitor ic3 = new SuspOffsetsAfterInstrClassVisitor(cw3, db);
        r3.accept(ic3, 0);
        cb = cw3.toByteArray();

        // DEBUG
        if (EXAMINED_CLASS != null) {
            examine(internalClassName, "quasar-5-final", cb);

            if (check) {
                ClassReader r4 = new ClassReader(cb);
                ClassVisitor cv4 = new CheckClassAdapter(new TraceClassVisitor(null), true);
                r4.accept(cv4, 0);
            }
        }

        return cb;
    }

    private void examine(String className, String suffix, byte[] data) {
        if (EXAMINED_CLASS != null && className != null && className.contains(EXAMINED_CLASS)) {
            final String filename = className.replace('/', '.') + "-" + new Date().getTime() + "-" + suffix + ".class";
            writeToFile(filename, data);
//            return new TraceClassVisitor(cv, new PrintWriter(new File(filename)));
        }
    }

    ByteCodeTransformer adapt(ByteCodeTransformer transformer, ClassLoader loader) {
        return isCachedClassLoader(loader) && byteCodeCache != null
            ? (classLoader, className, classBeingRedefined, byteCode) ->
                byteCodeCache.computeIfAbsent(className, byteCode, () ->
                    transformer.transform(classLoader, className, classBeingRedefined, byteCode)
                )
            : transformer;
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized MethodDatabase getMethodDatabase(ClassLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("classloader cannot be null");
        }
        MethodDatabase db = dbForClassloader.get(loader);
        if (db == null) {
            db = new MethodDatabase(this, loader, new DefaultSuspendableClassifier(loader));
            dbForClassloader.put(loader, db);
        }
        return db;
    }

    public QuasarInstrumentor setCheck(boolean check) {
        this.check = check;
        return this;
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized boolean isAllowMonitors() {
        return allowMonitors;
    }

    public synchronized QuasarInstrumentor setAllowMonitors(boolean allowMonitors) {
        this.allowMonitors = allowMonitors;
        return this;
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized boolean isAllowBlocking() {
        return allowBlocking;
    }

    public synchronized QuasarInstrumentor setAllowBlocking(boolean allowBlocking) {
        this.allowBlocking = allowBlocking;
        return this;
    }

    public Log getLog() {
        return log;
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized boolean isVerbose() {
        return verbose;
    }

    public synchronized void setVerbose(boolean verbose) {
        this.verbose = verbose;
        setLogLevelMask();
    }

    public synchronized boolean isDebug() {
        return debug;
    }

    public synchronized void setDebug(boolean debug) {
        this.debug = debug;
        setLogLevelMask();
    }

    public void addExcludedPackage(String packageGlob) {
        if (exclusions.add(packagePattern(packageGlob))) {
            log(INFO, "Ignoring packages: %s", packageGlob);
        }
    }

    // For use from OSGi, since it assumes comma-separated.
    void addExcludedPackages(String packagesGlobs) {
        for (String packageGlob : parseOSGiGlobs(packagesGlobs)) {
            addExcludedPackage(packageGlob);
        }
    }

    public boolean isExcluded(String className) {
        if (className != null) {
            className = className.replace('.', '/');

            final int i = className.lastIndexOf('/');
            if (i < 0)
                return false;
            final String packageName = className.substring(0, i);

            for (Pattern p : exclusions) {
                if (p.matcher(packageName).matches())
                    return true;
            }
        }
        return false;
    }

    boolean isExcludedClassLoader(String classLoaderName) {
        synchronized(excludedClassLoaders) {
            for (Pattern pattern : excludedClassLoaders) {
                if (pattern.matcher(classLoaderName).matches()) {
                    return true;
                }
            }
        }
        return classLoaderName.startsWith(THIS_PACKAGE_NAME);
    }

    void addExcludedClassLoader(String glob) {
        synchronized(excludedClassLoaders) {
            excludedClassLoaders.add(classLoaderPattern(glob));
        }
    }

    boolean isExcludedClassLoader(ClassLoader loader) {
        if (excludedBundleLocations.isEmpty()) {
            return false;
        }
        final BiPredicate<ClassLoader, Collection<Pattern>> bundleMatcher = OSGiClassLoader.fetchBundleLocationMatcher(loader);
        return bundleMatcher.test(loader, excludedBundleLocations);
    }

    boolean isExcludedBundleLocation(String bundleLocation) {
        if (!excludedBundleLocations.isEmpty()) {
            for (Pattern location : excludedBundleLocations) {
                if (location.matcher(bundleLocation).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    void addExcludedBundleLocation(String glob) {
        excludedBundleLocations.add(bundleLocationPattern(glob));
    }

    // For use from OSGi, since it assumes comma-separated.
    void addExcludedBundleLocations(String locationGlobs) {
        for (String locationGlob: parseOSGiGlobs(locationGlobs)) {
            addExcludedBundleLocation(locationGlob);
        }
    }

    boolean isCachedClassLoader(ClassLoader loader) {
        if (cachedBundleLocations.isEmpty()) {
            return false;
        }
        final BiPredicate<ClassLoader, Collection<Pattern>> bundleMatcher = OSGiClassLoader.fetchBundleLocationMatcher(loader);
        return bundleMatcher.test(loader, cachedBundleLocations);
    }

    void addCachedBundleLocation(String glob) {
        cachedBundleLocations.add(bundleLocationPattern(glob));
    }

    // For use from OSGi, since it assumes comma-separated.
    void addCachedBundleLocations(String locationGlobs) {
        for (String locationGlob: parseOSGiGlobs(locationGlobs)) {
            addCachedBundleLocation(locationGlob);
        }
    }

    private static List<String> parseOSGiGlobs(String value) {
        return (value != null)
            ? Arrays.stream(value.split(",", 0)).map(String::trim).collect(toUnmodifiableList())
            : emptyList();
    }

    private static Pattern classLoaderPattern(String glob) {
        final StringBuilder out = new StringBuilder(glob.length() + 5).append('^');
        int i = 0;
        while (i < glob.length()) {
            final char c = glob.charAt(i);
            switch (c) {
                case '.':
                    out.append("\\.");
                    break;
                case '?':
                    out.append('.');
                    break;
                case '*':
                    final int j = i + 1;
                    if (j < glob.length()) {
                        final char next = glob.charAt(j);
                        if (next == '*') {
                            out.append(".*");
                            i = j;
                            break;
                        }
                    }
                    out.append("[^.]+");
                    break;
                case '$':
                    out.append("\\$");
                    break;
                default:
                    out.append(c);
            }
            ++i;
        }
        out.append('$');
        return Pattern.compile(out.toString());
    }

    private static Pattern bundleLocationPattern(String glob) {
        final StringBuilder out = new StringBuilder(glob.length() + 5).append('^');
        int i = 0;
        while (i < glob.length()) {
            final char c = glob.charAt(i);
            switch (c) {
                case '.':
                    out.append("\\.");
                    break;
                case '?':
                    out.append('.');
                    break;
                case '*':
                    final int j = i + 1;
                    if (j < glob.length()) {
                        final char next = glob.charAt(j);
                        if (next == '*') {
                            i = j;
                        }
                    }
                    out.append(".*");
                    break;
                case '$':
                    out.append("\\$");
                    break;
                default:
                    out.append(c);
            }
            ++i;
        }
        out.append('$');
        return Pattern.compile(out.toString());
    }

    private synchronized void setLogLevelMask() {
        logLevelMask = (1 << WARNING.ordinal());
        if (verbose || debug)
            logLevelMask |= (1 << INFO.ordinal());
        if (debug)
            logLevelMask |= (1 << DEBUG.ordinal());
    }

    public void log(LogLevel level, String msg, Object... args) {
        if (log != null && (logLevelMask & (1 << level.ordinal())) != 0)
            log.log(level, msg, args);
    }

    public void error(String msg, Throwable ex) {
        if (log != null)
            log.error(msg, ex);
    }

    String checkClass(ClassLoader cl, File f) {
        return getMethodDatabase(cl).checkClass(f);
    }

    private static void writeToFile(String name, byte[] data) {
        try (OutputStream os = Files.newOutputStream(Paths.get(name), StandardOpenOption.CREATE_NEW)) {
            os.write(data);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Pattern packagePattern(String packageGlob) {
        final String glob = packageGlob.replace('.', '/');
        
        final StringBuilder out = new StringBuilder(glob.length() + 5).append('^');
        for (int i = 0; i < glob.length(); ++i) {
            final char c = glob.charAt(i);
            switch (c) {
                case '*':
                    out.append(DOT + "*");
                    break;
                case '?':
                    out.append(DOT);
                    break;
                case '.':
                    out.append("\\.");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    out.append(c);
            }
        }
        if (glob.endsWith("**"))
            out.append(".*");
        
        out.append('$');
        
        return Pattern.compile(out.toString());
    }

    public synchronized void addTypeDesc(String id, Iterable<String> descriptors) {
        for (String s : descriptors) {
            if (!Classes.getTypeDescs().add(id, s)) {
                log(WARNING, "Failed to add type desc '%s' = '%s'", id, s);
            } else {
                log(INFO, "Added type desc '%s' = '%s'", id, s);
            }
        }
    }

    void addTypeNames(String id, String[] names) {
        addTypeDesc(id, toTypeDescriptors(names));
    }

    private static Iterable<String> toTypeDescriptors(String[] names) {
        return Arrays.stream(names).map(s ->
            Type.getObjectType(classToSlashed(s)).getDescriptor()
        ).collect(toUnmodifiableList());
    }
}
