/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2021, R3 Ltd. All rights reserved.
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
package co.paralleluniverse.fibers.suspend;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class Stacks {
    private static volatile Supplier<? extends StackOps> GET_STACK;

    public static StackOps getStack() {
        final Supplier<? extends StackOps> getter = GET_STACK;
        return getter != null ? getter.get() : null;
    }
}
