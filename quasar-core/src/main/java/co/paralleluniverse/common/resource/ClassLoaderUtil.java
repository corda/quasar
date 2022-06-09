/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2015, Parallel Universe Software Co. All rights reserved.
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
 * Based on Guava's com.google.common.reflect.ClassPath
 */
/*
 * Copyright (c) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.paralleluniverse.common.resource;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.security.AccessController.doPrivileged;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/**
 *
 * @author pron
 */
public final class ClassLoaderUtil {
    public interface Visitor {
        void visit(String resource, URL url, ClassLoader cl) throws IOException;
    }

    private static final String MODULE_INFO_CLASS = "module-info.class";
    private static final String CLASS_FILE_NAME_EXTENSION = ".class";
    private static final String JAR_PROTOCOL = "jar";

    public static boolean isClassFile(String resourceName) {
        return resourceName.endsWith(CLASS_FILE_NAME_EXTENSION) && !isModuleInfoClass(resourceName);
    }

    private static boolean isModuleInfoClass(String resourceName) {
        final int modInfoLength = MODULE_INFO_CLASS.length();
        final int resourceLength = resourceName.length();
        return resourceName.endsWith(MODULE_INFO_CLASS)
            && (resourceLength == modInfoLength || resourceName.charAt(resourceLength - modInfoLength - 1) == '/');
    }

    public static String classToResource(String className) {
        if (className == null)
            return null;
        return className.replace('.', '/') + CLASS_FILE_NAME_EXTENSION;
    }

    public static String classToResource(Class<?> clazz) {
        if (clazz == null)
            return null;
        return classToResource(clazz.getName());
    }

    public static String classToSlashed(String className) {
        if (className == null)
            return null;
        return className.replace('.', '/');
    }

    public static String classToSlashed(Class<?> clazz) {
        if (clazz == null)
            return null;
        return classToSlashed(clazz.getName());
    }

    public static String resourceToClass(String resourceName) {
        if (resourceName == null)
            return null;
        return resourceToSlashed(resourceName).replace('/', '.');
    }

    public static String resourceToSlashed(String resourceName) {
        if (resourceName == null)
            return null;
        if (!resourceName.endsWith(CLASS_FILE_NAME_EXTENSION))
            throw new IllegalArgumentException("Resource " + resourceName + " is not a class file");
        return resourceName.substring(0, resourceName.length() - CLASS_FILE_NAME_EXTENSION.length());
    }

    public static void accept(URLClassLoader ucl, Visitor visitor) throws IOException {
        accept(ucl, ucl.getURLs(), visitor);
    }

    public static void accept(ClassLoader cl, URL[] urls, Visitor visitor) throws IOException {
        try {
            final Set<URI> scannedUris = new HashSet<>();
            for (URL entry : urls) {
                URI uri = entry.toURI();
                if (uri.getScheme().equals("file") && scannedUris.add(uri))
                    scanFrom(new File(uri), cl, scannedUris, visitor);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static void scan(URI uri, ClassLoader classloader, Set<URI> scannedUris, Visitor visitor) throws IOException {
        if (uri.getScheme().equals("file") && scannedUris.add(uri))
            scanFrom(new File(uri), classloader, scannedUris, visitor);
    }

    private static void scanFrom(File file, ClassLoader classloader, Set<URI> scannedUris, Visitor visitor) throws IOException {
        if (!file.exists())
            return;

        if (file.isDirectory())
            scanDirectory(file, classloader, visitor);
        else
            scanJar(file, classloader, scannedUris, visitor);
    }

    private static void scanDirectory(File directory, ClassLoader classloader, Visitor visitor) throws IOException {
        scanDirectory(directory, classloader, "", new HashSet<>(), visitor);
    }

    private static void scanDirectory(File directory, ClassLoader classloader, String packagePrefix, Set<File> ancestors, Visitor visitor) throws IOException {
        File canonical = directory.getCanonicalFile();
        if (ancestors.contains(canonical)) {
            // A cycle in the filesystem, for example due to a symbolic link.
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            // logger.warning("Cannot read directory " + directory);
            // IO error, just skip the directory
            return;
        }
        Set<File> newAncestors = new HashSet<>(ancestors);
        newAncestors.add(canonical);
        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory()) {
                scanDirectory(f, classloader, packagePrefix + name + '/', newAncestors, visitor);
            } else {
                String resourceName = packagePrefix + name;
                if (!resourceName.equals(JarFile.MANIFEST_NAME))
                    visitor.visit(resourceName, f.toURI().toURL(), classloader);
            }
        }
    }

    private static void scanJar(File file, ClassLoader classloader, Set<URI> scannedUris, Visitor visitor) throws IOException {
        final JarFile jarFile;
        try {
            jarFile = new JarFile(file);
        } catch (IOException e) {
            // Not a jar file
            return;
        }
        try {
            for (URI uri : getClassPathFromManifest(file, jarFile.getManifest()))
                scan(uri, classloader, scannedUris, visitor);

            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().equals(JarFile.MANIFEST_NAME))
                    continue;
                visitor.visit(entry.getName(), new URL("jar:file:" + file.getCanonicalPath() + "!/" + entry.getName()), classloader);
            }
        } finally {
            try {
                jarFile.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Finds the first ClassLoader in the hierarchy that can provide the target resource.
     * @param loader A ClassLoader capable of finding the target resource.
     * @param resourceName The name of the resource that we are matching.
     * @param target The resource URL we are matching against.
     * @return {@code loader}'s remotest parent that can still find {@code target}.
     */
    public static ClassLoader getBestClassLoader(ClassLoader loader, String resourceName, URL target) {
        final ClassLoader bestCl = new BestLookup(resourceName, target).lookup(loader);
        return (bestCl == null) ? loader : bestCl;
    }

    private static String getUnderlyingURL(URL url) {
        if (JAR_PROTOCOL.equals(url.getProtocol())) {
            // This is probably (but not necessarily) a file:/ URL.
            final String file = url.getFile();
            return file.substring(0, file.indexOf("!/"));
        } else {
            return url.toString();
        }
    }

    /**
     * Worker class for {@link #getBestClassLoader(ClassLoader, String, URL)}.
     * We need to search from the bootstrap classloader upwards in order to
     * mirror how {@link ClassLoader#getResource(String)} works.
     *
     * We are expected already to be running with the correct security context.
     */
    private static final class BestLookup {
        private static final Set<String> BOOT_CLASS_PATH = doPrivileged(new GetBootClassPath());

        private final String resourceName;
        private final URL target;
        private final Predicate<String> underlyingMatcher;
        private final ClassLoader extensionClassLoader;
        private final ClassLoader bootstrapClassLoader;

        BestLookup(String resourceName, URL resource) {
            this.resourceName = resourceName;
            this.target = resource;
            String underlyingURL = getUnderlyingURL(target);
            this.underlyingMatcher = underlyingURL.endsWith(CLASS_FILE_NAME_EXTENSION)
                ? new IsEqualOrContains(underlyingURL) : new IsEqual(underlyingURL);
            this.extensionClassLoader = ClassLoader.getSystemClassLoader().getParent();
            this.bootstrapClassLoader = extensionClassLoader.getParent();
        }

        private static final class IsEqualOrContains implements Predicate<String> {
            private final String underlyingURL;

            IsEqualOrContains(String underlyingURL) {
                this.underlyingURL = underlyingURL;
            }

            @Override
            public boolean test(String url) {
                return underlyingURL.equals(url) || (url.endsWith("/") && underlyingURL.startsWith(url));
            }
        }

        private static final class IsEqual implements Predicate<String> {
            private final String underlyingURL;

            IsEqual(String underlyingURL) {
                this.underlyingURL = underlyingURL;
            }

            @Override
            public boolean test(String url) {
                return underlyingURL.equals(url);
            }
        }

        ClassLoader lookup(ClassLoader current) {
            if (current == bootstrapClassLoader) {
                if (BOOT_CLASS_PATH.isEmpty()) {
                    // Fall-back strategy if sun.boot.class.path property unavailable.
                    // This checks both the extension and bootstrap classloaders, but
                    // we are hoping never to reach here in practice.
                    final URL url = extensionClassLoader.getResource(resourceName);
                    if (target.equals(url)) {
                        return extensionClassLoader;
                    }
                } else if (isTargetFrom(BOOT_CLASS_PATH)) {
                    // We are conflating the bootstrap and extension
                    // classloaders as far as Quasar is concerned.
                    return extensionClassLoader;
                }
            } else {
                final ClassLoader candidate = lookup(current.getParent());
                if (candidate != null) {
                    return candidate;
                }

                if (current instanceof URLClassLoader) {
                    // Important optimisation, because invoking getResource() is not cheap!
                    // This also works for the application and extension classloaders on Java 8.
                    if (isTargetFrom(((URLClassLoader) current).getURLs())) {
                        return current;
                    }
                } else if (target.equals(current.getResource(resourceName))) {
                    return current;
                }
            }
            return null;
        }

        private boolean isTargetFrom(URL[] urls) {
            for (URL url : urls) {
                if (underlyingMatcher.test(url.toString())) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("SameParameterValue")
        private boolean isTargetFrom(Iterable<String> paths) {
            for (String path : paths) {
                if (underlyingMatcher.test(path)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns the class path URIs specified by the {@code Class-Path} manifest attribute, according
     * to <a href="http://docs.oracle.com/javase/6/docs/technotes/guides/jar/jar.html#Main%20Attributes">
     * JAR File Specification</a>. If {@code manifest} is null, it means the jar file has no
     * manifest, and an empty set will be returned.
     */
    private static Set<URI> getClassPathFromManifest(File jarFile, Manifest manifest) {
        if (manifest == null)
            return new HashSet<>();

        HashSet<URI> s = new HashSet<>();
        String classpathAttribute = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH.toString());
        if (classpathAttribute != null) {
            for (String path : classpathAttribute.split("\\s")) {
                if (path != null && path.trim().length() > 0) {
                    URI uri;
                    try {
                        uri = getClassPathEntry(jarFile, path.trim());
                    } catch (URISyntaxException e) {
                        // Ignore bad entry
                        // logger.warning("Invalid Class-Path entry: " + path);
                        continue;
                    }
                    s.add(uri);
                }
            }
        }
        return s;
    }

    /**
     * Returns the absolute uri of the Class-Path entry value as specified in
     * <a href="http://docs.oracle.com/javase/6/docs/technotes/guides/jar/jar.html#Main%20Attributes">
     * JAR File Specification</a>. Even though the specification only talks about relative urls,
     * absolute urls are actually supported too (for example, in Maven surefire plugin).
     */
    private static URI getClassPathEntry(File jarFile, String path) throws URISyntaxException {
        URI uri = new URI(path);
        if (uri.isAbsolute())
            return uri;
        else
            return new File(jarFile.getParentFile(), path.replace('/', File.separatorChar)).toURI();
    }

    /**
     * Computes the URLs that the bootstrap classloader would use, if it
     * were an instance of {@link URLClassLoader}. Note that the
     * sun.boot.class.path system property is technically an undocumented
     * implementation detail, and so we cannot be sure it will exist.
     */
    private static class GetBootClassPath implements PrivilegedAction<Set<String>> {
        @Override
        public Set<String> run() {
            final String bootClassPath = System.getProperty("sun.boot.class.path");
            if (bootClassPath == null || bootClassPath.isEmpty()) {
                return emptySet();
            }

            try {
                final String[] elements = bootClassPath.split(File.pathSeparator);
                final Set<String> bootPath = new LinkedHashSet<>();
                for (String element : elements) {
                    final Path path = Paths.get(element);
                    if (Files.exists(path)) {
                        bootPath.add(path.toUri().toURL().toString());
                    }
                }
                return unmodifiableSet(bootPath);
            } catch (MalformedURLException e) {
                System.err.println("[quasar] ERROR: Invalid sun.boot.class.path property - " + e.getMessage());
                return emptySet();
            }
        }
    }

    private ClassLoaderUtil() {
    }
}
