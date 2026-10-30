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
package co.paralleluniverse.concurrent.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import co.paralleluniverse.common.reflection.GetAccessDeclaredConstructor;
import co.paralleluniverse.common.reflection.GetAccessDeclaredField;
import co.paralleluniverse.common.reflection.GetDeclaredField;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;

import static java.security.AccessController.doPrivileged;

/**
 *
 * @author pron
 */
public class ThreadAccess {
    private static final VarHandle TARGET;
    // Java 19+: the thread's target is the final field Thread.holder.task
    private static final VarHandle HOLDER;
    private static final VarHandle HOLDER_TASK;
    private static final VarHandle THREAD_LOCALS;
    private static final VarHandle INHERITABLE_THREAD_LOCALS;
    private static final VarHandle CONTEXT_CLASS_LOADER;
    private static final VarHandle INHERITED_ACCESS_CONTROL_CONTEXT;
    
    private static final Constructor<?> threadLocalMapConstructor;
    private static final Constructor<?> threadLocalMapInheritedConstructor;
    private static final Field threadLocalMapTableField;
    private static final Field threadLocalMapSizeField;
    private static final Field threadLocalMapThresholdField;
    private static final Class<?> threadLocalMapEntryClass;
    private static final Constructor<?> threadLocalMapEntryConstructor;
    private static final Field threadLocalMapEntryValueField;

    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(Thread.class, lookup);

            // ENT-5489, For openj9 vm name is runnable.
            VarHandle _target = null;
            VarHandle _holder = null;
            VarHandle _holderTask = null;
            try {
                if (System.getProperty("java.vm.name").toLowerCase().contains("openj9")) {
                    _target = l.findVarHandle(Thread.class, "runnable", Runnable.class);
                } else {
                    _target = l.findVarHandle(Thread.class, "target", Runnable.class);
                }
            } catch (NoSuchFieldException e) {
                // ENT-17055: Java 19+ moved the target into Thread.holder.task, which is final, so the targets Quasar sets are kept in TARGETS.
                Class<?> holderClass = Class.forName("java.lang.Thread$FieldHolder");
                _holder = l.findVarHandle(Thread.class, "holder", holderClass);
                _holderTask = MethodHandles.privateLookupIn(holderClass, lookup).findVarHandle(holderClass, "task", Runnable.class);
            }
            TARGET = _target;
            HOLDER = _holder;
            HOLDER_TASK = _holderTask;

            THREAD_LOCALS = l.unreflectVarHandle(doPrivileged(new GetDeclaredField(Thread.class, "threadLocals")));
            INHERITABLE_THREAD_LOCALS = l.unreflectVarHandle(doPrivileged(new GetDeclaredField(Thread.class, "inheritableThreadLocals")));
            CONTEXT_CLASS_LOADER = l.unreflectVarHandle(doPrivileged(new GetDeclaredField(Thread.class, "contextClassLoader")));

            VarHandle _inheritedAccessControlContext = null;
            try {
                _inheritedAccessControlContext = l.unreflectVarHandle(doPrivileged(new GetDeclaredField(Thread.class, "inheritedAccessControlContext")));
            } catch (PrivilegedActionException e) {
                Throwable t = e.getCause();
                if (!(t instanceof NoSuchFieldException)) {
                    throw new RuntimeException(t);
                }
            }
            INHERITED_ACCESS_CONTROL_CONTEXT = _inheritedAccessControlContext;

            Class<?> threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            threadLocalMapConstructor = doPrivileged(new GetAccessDeclaredConstructor<>(threadLocalMapClass, ThreadLocal.class, Object.class));
            threadLocalMapInheritedConstructor = doPrivileged(new GetAccessDeclaredConstructor<>(threadLocalMapClass, threadLocalMapClass));
            threadLocalMapTableField = doPrivileged(new GetAccessDeclaredField(threadLocalMapClass, "table"));
            threadLocalMapSizeField = doPrivileged(new GetAccessDeclaredField(threadLocalMapClass, "size"));
            threadLocalMapThresholdField = doPrivileged(new GetAccessDeclaredField(threadLocalMapClass, "threshold"));

            threadLocalMapEntryClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry");
            threadLocalMapEntryConstructor = doPrivileged(new GetAccessDeclaredConstructor<>(threadLocalMapEntryClass, ThreadLocal.class, Object.class));
            threadLocalMapEntryValueField = doPrivileged(new GetAccessDeclaredField(threadLocalMapEntryClass, "value"));
        } catch (PrivilegedActionException ex) {
            throw new AssertionError(ex.getCause());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * The targets set by {@link #setTarget} on Java 19+, where a thread's own target can't be changed.
     */
    private static final Map<Thread, Runnable> TARGETS = Collections.synchronizedMap(new WeakHashMap<>());

    public static Runnable getTarget(Thread thread) {
        if (TARGET != null)
            return (Runnable) TARGET.get(thread);
        final Runnable target = TARGETS.get(thread);
        return target != null ? target : getOriginalTarget(thread);
    }

    public static void setTarget(Thread thread, Runnable target) {
        if (TARGET != null)
            TARGET.set(thread, target);
        else if (target == null || target == getOriginalTarget(thread))
            TARGETS.remove(thread);
        else
            TARGETS.put(thread, target);
    }

    private static Runnable getOriginalTarget(Thread thread) {
        final Object holder = HOLDER.get(thread);
        return holder != null ? (Runnable) HOLDER_TASK.get(holder) : null; // virtual threads have no holder
    }

    public static Object getThreadLocals(Thread thread) {
        return (Object) THREAD_LOCALS.get(thread);
    }

    public static void setThreadLocals(Thread thread, Object threadLocals) {
        THREAD_LOCALS.set(thread, threadLocals);
    }

    public static Object getInheritableThreadLocals(Thread thread) {
        return (Object) INHERITABLE_THREAD_LOCALS.get(thread);
    }

    public static void setInheritableThreadLocals(Thread thread, Object inheritableThreadLocals) {
        INHERITABLE_THREAD_LOCALS.set(thread, inheritableThreadLocals);
    }

    private static Object createThreadLocalMap(ThreadLocal tl, Object firstValue) {
        try {
            return threadLocalMapConstructor.newInstance(tl, firstValue);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    public static Object createInheritedMap(Object inheritableThreadLocals) {
        try {
            return threadLocalMapInheritedConstructor.newInstance(inheritableThreadLocals);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
    
    // createInheritedMap works only for InheritableThreadLocals
    public static Object cloneThreadLocalMap(Object orig) {
        try {
            Object clone = createThreadLocalMap(new ThreadLocal(), null);

            Object origTable = threadLocalMapTableField.get(orig);
            final int len = Array.getLength(origTable);
            Object tableClone = Array.newInstance(threadLocalMapEntryClass, len);
            for (int i = 0; i < len; i++) {
                Object entry = Array.get(origTable, i);
                if (entry != null)
                    Array.set(tableClone, i, cloneThreadLocalMapEntry(entry));
            }

            threadLocalMapTableField.set(clone, tableClone);
            threadLocalMapSizeField.setInt(clone, threadLocalMapSizeField.getInt(orig));
            threadLocalMapThresholdField.setInt(clone, threadLocalMapThresholdField.getInt(orig));
            return clone;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Object cloneThreadLocalMapEntry(Object entry) {
        try {
            final ThreadLocal key = ((Reference<ThreadLocal>) entry).get();
            final Object value = threadLocalMapEntryValueField.get(entry);
            return threadLocalMapEntryConstructor.newInstance(key, value);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    public static Map<ThreadLocal, Object> toMap(Object threadLocalMap) {
        try {
            final Map<ThreadLocal, Object> map = new HashMap<>();
            Object table = threadLocalMapTableField.get(threadLocalMap);
            final int len = Array.getLength(table);
            for (int i = 0; i < len; i++) {
                Object entry = Array.get(table, i);
                if (entry != null)
                    map.put(((Reference<ThreadLocal>) entry).get(), threadLocalMapEntryValueField.get(entry));
            }
            return map;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    public static ClassLoader getContextClassLoader(Thread thread) {
        return (ClassLoader) CONTEXT_CLASS_LOADER.get(thread);
    }

    public static void setContextClassLoader(Thread thread, ClassLoader classLoader) {
        CONTEXT_CLASS_LOADER.set(thread, classLoader);
    }

    public static AccessControlContext getInheritedAccessControlContext(Thread thread) {
        if (INHERITED_ACCESS_CONTROL_CONTEXT == null)
            return null;
        return (AccessControlContext) INHERITED_ACCESS_CONTROL_CONTEXT.get(thread);
    }

    public static void setInheritedAccessControlContext(Thread thread, AccessControlContext accessControlContext) {
        if (INHERITED_ACCESS_CONTROL_CONTEXT != null)
            INHERITED_ACCESS_CONTROL_CONTEXT.set(thread, accessControlContext);
    }
}
