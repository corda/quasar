/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2016, Parallel Universe Software Co. All rights reserved.
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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumMap;

import co.paralleluniverse.fibers.Instrumented;
import co.paralleluniverse.fibers.Suspendable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * This class contains hard-coded values with the names of the classes and methods relevant for instrumentation.
 *
 * @author pron
 */
final class Classes {
    private static final BlockingMethod[] BLOCKING_METHODS = {
        new BlockingMethod("java/lang/Thread", "sleep", "(J)V", "(JI)V"),
        new BlockingMethod("java/lang/Thread", "join", "()V", "(J)V", "(JI)V"),
        new BlockingMethod("java/lang/Object", "wait", "()V", "(J)V", "(JI)V")
    };

    private static final Set<String> yieldMethods = Set.of(
        "park", "yield", "parkAndUnpark", "yieldAndUnpark", "parkAndSerialize", "parkAndCustomSerialize"
    );

    // Don't load these classes
    static final String STACK_NAME       = /*Stack.class.getName().replace('.', '/')*/ "co/paralleluniverse/fibers/Stack";
    static final String FIBER_CLASS_NAME = /*Fiber.class.getName().replace('.', '/')*/ "co/paralleluniverse/fibers/Fiber";
    static final String STRAND_NAME      = /*Strand.class.getName().replace('.', '/')*/"co/paralleluniverse/strands/Strand";
    static final String FIBER_HELPER_NAME = "co/paralleluniverse/fibers/FiberHelper";

    static final String THROWABLE_NAME         = Throwable.class.getName().replace('.', '/');
    static final String EXCEPTION_NAME         = Exception.class.getName().replace('.', '/');
    static final String RUNTIME_EXCEPTION_NAME = RuntimeException.class.getName().replace('.', '/');

    static final String RUNTIME_SUSPEND_EXECUTION_NAME = "co/paralleluniverse/fibers/RuntimeSuspendExecution";
    static final String UNDECLARED_THROWABLE_NAME      = UndeclaredThrowableException.class.getName().replace('.', '/');
    static final String SUSPEND_EXECUTION_NAME         = "co/paralleluniverse/fibers/SuspendExecution";

    static final String LAMBDA_METHOD_PREFIX = "lambda$";

    // CORE-21 : Provide getter and setter for annotation types.
    static class TypeDesc {

        enum ID {
            SUSPENDABLE,
            DONT_INSTRUMENT,
            INSTRUMENTED,
        };

        // On TYPE_DESC_ID can map to more than one type.
        private EnumMap<ID, Set<String>> descIds = new EnumMap<>(ID.class);

        TypeDesc() {
            set(ID.SUSPENDABLE, Type.getDescriptor(Suspendable.class));
            set(ID.DONT_INSTRUMENT, Type.getDescriptor(DontInstrument.class));
            set(ID.INSTRUMENTED, Type.getDescriptor(Instrumented.class));
        }

        boolean contains(ID id, String s) {
            return descIds.get(id).contains(s);
        }

        void clear(ID id) {
            descIds.get(id).clear();
        }

        String get(ID id) {
            // Just return first element from iterator, fine for singletons, which is default.
            return descIds.get(id).iterator().next();
        }

        void set(ID id, String s) {
            descIds.put(id, new HashSet<>(Arrays.asList(s)));
        }

        void add(ID id, String s) {
            descIds.get(id).add(s);
        }

        boolean clear(String id) {
            try {
                clear(ID.valueOf(id));
            }
            catch (IllegalArgumentException e) {
                return false;
            }
            return true;
        }

        boolean add(String id, String s) {
            try {
                add(ID.valueOf(id), s);
            }
            catch (IllegalArgumentException e) {
                return false;
            }
            return true;
        }
    };

    private static final TypeDesc typeDesc = new TypeDesc();
    static TypeDesc getTypeDesc() {
        return typeDesc;
    }

    static boolean isYieldMethod(String className, String methodName) {
        return FIBER_CLASS_NAME.equals(className) && yieldMethods.contains(methodName);
    }

    /**
     * @noinspection UnusedParameters
     */
    static boolean isAllowedToBlock(String className, String methodName) {
        return STRAND_NAME.equals(className);
    }

    static int blockingCallIdx(MethodInsnNode ins) {
        for (int i = 0, n = BLOCKING_METHODS.length; i < n; i++) {
            if (BLOCKING_METHODS[i].match(ins))
                return i;
        }
        return -1;
    }

    private static class BlockingMethod {
        private final String owner;
        private final String name;
        private final String[] descs;

        private BlockingMethod(String owner, String name, String... descs) {
            this.owner = owner;
            this.name = name;
            this.descs = descs;
        }

        boolean match(MethodInsnNode min) {
            if (owner.equals(min.owner) && name.equals(min.name)) {
                for (String desc : descs) {
                    if (desc.equals(min.desc)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    static int[] toIntArray(List<Integer> suspOffsetsAfterInstrL) {
        if (suspOffsetsAfterInstrL == null)
            return null;

        final List<Integer> suspOffsetsAfterInstrLFiltered = new ArrayList<>(suspOffsetsAfterInstrL.size());
        for (final Integer i : suspOffsetsAfterInstrL) {
            if (i != null)
                suspOffsetsAfterInstrLFiltered.add(i);
        }

        final int[] ret = new int[suspOffsetsAfterInstrLFiltered.size()];
        int j = 0;
        for (final Integer i : suspOffsetsAfterInstrLFiltered) {
            ret[j] = i;
            j++;
        }

        return ret;
    }

    private Classes() {
    }
}
