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
package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.common.reflection.GetAccessDeclaredField;
import co.paralleluniverse.common.reflection.GetAccessDeclaredMethod;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.jar.Manifest;
import sun.misc.Resource;
import sun.misc.URLClassPath;

import static java.security.AccessController.doPrivileged;

/**
 *
 * @author pron
 */
public final class QuasarURLClassLoaderHelper {
    private final URLClassLoader cl;
    private final QuasarInstrumentor instrumentor;

    public QuasarURLClassLoaderHelper(URLClassLoader cl) {
        this.cl = cl;
        this.instrumentor = newInstrumentor();
    }

    private QuasarInstrumentor newInstrumentor() {
        QuasarInstrumentor inst = new QuasarInstrumentor(false);
        inst.setLog(new Log() {
            @Override
            public void log(LogLevel level, String msg, Object... args) {
                System.err.println("[quasar] " + level + ": " + String.format(msg, args));
            }

            @Override
            public void error(String msg, Throwable exc) {
                System.err.println("[quasar] ERROR: " + msg);
                exc.printStackTrace(System.err);
            }
        });
        return inst;
    }

    public void setLog(final boolean verbose, final boolean debug) {
        instrumentor.setVerbose(verbose);
        instrumentor.setDebug(debug);
    }

    public Class<?> findClass(final String name)
            throws ClassNotFoundException {
        try {
            return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Class>() {
                        @Override
                        public Class run() throws ClassNotFoundException {
                            String path = name.replace('.', '/').concat(".class");
                            Resource res = ucp().getResource(path, false);
                            if (res != null) {
                                try {
                                    return defineClass(name, instrument(name, res));
                                } catch (IOException e) {
                                    throw new ClassNotFoundException(name, e);
                                }
                            } else {
                                throw new ClassNotFoundException(name);
                            }
                        }
                    }, acc());
        } catch (java.security.PrivilegedActionException pae) {
            throw (ClassNotFoundException) pae.getException();
        }
    }

    public InputStream instrumentResourceStream(String resourceName, InputStream is) {
        if (is != null && resourceName.endsWith(".class")) {
            try {
                byte[] bytes = ByteStreams.toByteArray(is);
                byte[] instrumented = instrumentor.instrumentClass(cl, resourceName.substring(0, resourceName.length() - ".class".length()), bytes);
                return new ByteArrayInputStream(instrumented);
            } catch (final IOException e) {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException(e);
                    }
                };
            }
        } else
            return is;
    }

    private Resource instrument(final String className, final Resource res) {
        return new Resource() {
            private byte[] instrumented;

            @Override
            public synchronized byte[] getBytes() throws IOException {
                if (instrumented == null) {
                    final byte[] bytes;
                    ByteBuffer bb = res.getByteBuffer();
                    if (bb != null) {
                        final int size = bb.remaining();
                        bytes = new byte[size];
                        bb.get(bytes);
                    } else
                        bytes = res.getBytes();
                    try {
                        this.instrumented = instrumentor.instrumentClass(cl, className, bytes);
                    } catch (Exception ex) {
                        if (MethodDatabase.isProblematicClass(className))
                            instrumentor.log(LogLevel.INFO, "Skipping problematic class instrumentation %s - %s %s", className, ex, Arrays.toString(ex.getStackTrace()));
                        else
                            instrumentor.error("Unable to instrument " + className, ex);
                        instrumented = bytes;
                    }
                }
                return instrumented;
            }

            @Override
            public ByteBuffer getByteBuffer() throws IOException {
                return null;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new AssertionError();
            }

            @Override
            public String getName() {
                return res.getName();
            }

            @Override
            public URL getURL() {
                return res.getURL();
            }

            @Override
            public URL getCodeSourceURL() {
                return res.getCodeSourceURL();
            }

            @Override
            public int getContentLength() throws IOException {
                return res.getContentLength();
            }

            @Override
            public Manifest getManifest() throws IOException {
                return res.getManifest();
            }

            @Override
            public Certificate[] getCertificates() {
                return res.getCertificates();
            }

            @Override
            public CodeSigner[] getCodeSigners() {
                return res.getCodeSigners();
            }
        };
    }
    // private members access
    private static final Field ucpField;
    private static final Field accField;
    private static final Method defineClassMethod;

    static {
        try {
            ucpField = doPrivileged(new GetAccessDeclaredField(URLClassLoader.class, "ucp"));
            accField = doPrivileged(new GetAccessDeclaredField(URLClassLoader.class, "acc"));
            defineClassMethod = doPrivileged(new GetAccessDeclaredMethod(URLClassLoader.class, "defineClass", String.class, Resource.class));
        } catch (PrivilegedActionException e) {
            Throwable t = e.getCause();
            throw (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    private Class defineClass(String name, Resource res) throws IOException {
        try {
            return (Class) defineClassMethod.invoke(cl, name, res);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException)
                throw (IOException) e.getCause();
            throw new RuntimeException(e.getCause());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private URLClassPath ucp() {
        try {
            return (URLClassPath) ucpField.get(cl);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private AccessControlContext acc() {
        try {
            return (AccessControlContext) accField.get(cl);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
