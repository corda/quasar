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

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.TestsHelper;
import co.paralleluniverse.fibers.suspend.SuspendExecution;
import co.paralleluniverse.strands.SuspendableRunnable;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Suspendable calls in constructor arguments, i.e. a suspension point between NEW/DUP and the constructor call, which the instrumentation
 * has to move (see InstrumentMethod.emitNewAndDup). This broke with ASM 9.6+, which analyses DUP differently (ENT-17055).
 */
public class SuspendableConstructorArgumentTest implements SuspendableRunnable {
    private Object result;

    @Test
    public void testSuspendableConstructorArguments() {
        Fiber<?> co = new Fiber<>((String) null, null, this);
        int count = 1;
        while (!TestsHelper.exec(co))
            count++;

        assertEquals(4, count);
        assertEquals(new Pair("a", new Pair("b", "c")), result);
    }

    @Override
    public void run() throws SuspendExecution {
        result = new Pair(park("a"), new Pair(park("b"), park("c")));
    }

    private static String park(String value) throws SuspendExecution {
        Fiber.park();
        return value;
    }

    private static final class Pair {
        private final Object first;
        private final Object second;

        Pair(Object first, Object second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pair && first.equals(((Pair) o).first) && second.equals(((Pair) o).second);
        }

        @Override
        public int hashCode() {
            return 31 * first.hashCode() + second.hashCode();
        }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }
    }
}
