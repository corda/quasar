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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Java 19+ moved a thread's target into the final field Thread.holder.task (ENT-17055).
 */
public class ThreadAccessTest {
    @Test
    public void getTargetReturnsTheThreadsRunnable() {
        Runnable runnable = () -> {};
        Thread thread = new Thread(runnable);
        assertSame(runnable, ThreadAccess.getTarget(thread));
        assertNull(ThreadAccess.getTarget(new Thread()));
    }

    @Test
    public void setTargetChangesTheTarget() {
        Runnable original = () -> {};
        Runnable replacement = () -> {};
        Thread thread = new Thread(original);

        ThreadAccess.setTarget(thread, replacement);
        assertSame(replacement, ThreadAccess.getTarget(thread));

        ThreadAccess.setTarget(thread, original);
        assertSame(original, ThreadAccess.getTarget(thread));
    }

    @Test
    public void targetsAreSetPerThread() {
        Runnable target = () -> {};
        Thread thread = new Thread();
        Thread other = new Thread();

        ThreadAccess.setTarget(thread, target);
        assertSame(target, ThreadAccess.getTarget(thread));
        assertNull(ThreadAccess.getTarget(other));
    }
}
