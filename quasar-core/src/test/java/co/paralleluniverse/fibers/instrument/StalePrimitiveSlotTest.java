package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.suspend.SuspendExecution;
import co.paralleluniverse.strands.SuspendableCallable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for CS-4268: primitives left behind in the fiber stack (Stack.dataLong) by an already-popped frame
 * must never be mistaken for a frame record.
 * <p>
 * Stack.nextMethodEntry() reads dataLong[sp + numSlots(topRecord)] and treats it as the callee's frame record. When an
 * instrumented method is entered from a plain (non-instrumented) caller nobody called pushMethod(), and if the current
 * top frame has not pushed yet (numSlots == 0) the slot read is that frame's first data slot. If it still holds a
 * primitive whose top 14 bits form a valid entry index, the callee believes it is being resumed, restores garbage
 * locals and jumps into the middle of itself.
 * <p>
 * The poisoners leave sixteen longs equal to 1L &lt;&lt; 50 (entry == 1) behind; topK shifts where the next frame lands by
 * K slots so the sweep covers the whole poisoned region; poisonerTwoSites additionally makes the frame record of the
 * popped frame report fewer slots than were written (its last call site has fewer live locals), which defeats a
 * popMethod()-only clean-up.
 */
public class StalePrimitiveSlotTest {
    static final long POISON = 1L << 50;
    static final long SAFE = (1L << 50) - 1;
    static final int VICTIM_OK = 701;

    /** Instrumented; owns resume entry #1 through a suspendable call that is never executed. */
    @Suspendable
    static int victim(int x) throws SuspendExecution {
        int marker = 700;
        if (x == 42) Fiber.yield();
        return marker + x;
    }

    /** Plain method: victim() is entered without Stack.pushMethod(). */
    static int plainBridge(int x) {
        try {
            return victim(x);
        } catch (SuspendExecution e) {
            throw new AssertionError(e);
        }
    }

    /** Properly pushed by its caller; calls the victim through the plain bridge before pushing itself. */
    @Suspendable
    static int mid() throws SuspendExecution {
        int r = plainBridge(1);
        if (r == -1) Fiber.yield();
        return r;
    }

    @Suspendable
    static long poisonerOneSite(long v) throws SuspendExecution {
        long p0 = v, p1 = v, p2 = v, p3 = v, p4 = v, p5 = v, p6 = v, p7 = v;
        long p8 = v, p9 = v, p10 = v, p11 = v, p12 = v, p13 = v, p14 = v, p15 = v;
        Fiber.yield();
        return p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15;
    }

    @Suspendable
    static long poisonerTwoSites(long v, boolean flag) throws SuspendExecution {
        long result = 0;
        if (flag) {
            long p0 = v, p1 = v, p2 = v, p3 = v, p4 = v, p5 = v, p6 = v, p7 = v;
            long p8 = v, p9 = v, p10 = v, p11 = v, p12 = v, p13 = v, p14 = v, p15 = v;
            Fiber.yield();
            result = p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15;
        }
        Fiber.yield(); // last call site: only v, flag and result are live, so the frame record says ~3 slots
        return result;
    }

    @Suspendable
    static long poison(long v, boolean twoSites) throws SuspendExecution {
        return twoSites ? poisonerTwoSites(v, true) : poisonerOneSite(v);
    }

    // topK: K extra longs live at the mid() call site shift the position of mid's frame by K slots.
    @Suspendable static int top0(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d0 = r + 0; int y = mid(); return y + (int) ((r + d1 + d0) & 0); }
    @Suspendable static int top1(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; int y = mid(); return y + (int) ((r + d1) & 0); }
    @Suspendable static int top2(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; int y = mid(); return y + (int) ((r + d1 + d2) & 0); }
    @Suspendable static int top3(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; int y = mid(); return y + (int) ((r + d1 + d2 + d3) & 0); }
    @Suspendable static int top4(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4) & 0); }
    @Suspendable static int top5(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5) & 0); }
    @Suspendable static int top6(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6) & 0); }
    @Suspendable static int top7(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7) & 0); }
    @Suspendable static int top8(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8) & 0); }
    @Suspendable static int top9(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9) & 0); }
    @Suspendable static int top10(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10) & 0); }
    @Suspendable static int top11(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; long d11 = r + 11; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10 + d11) & 0); }
    @Suspendable static int top12(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; long d11 = r + 11; long d12 = r + 12; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10 + d11 + d12) & 0); }
    @Suspendable static int top13(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; long d11 = r + 11; long d12 = r + 12; long d13 = r + 13; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10 + d11 + d12 + d13) & 0); }
    @Suspendable static int top14(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; long d11 = r + 11; long d12 = r + 12; long d13 = r + 13; long d14 = r + 14; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10 + d11 + d12 + d13 + d14) & 0); }
    @Suspendable static int top15(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; long d11 = r + 11; long d12 = r + 12; long d13 = r + 13; long d14 = r + 14; long d15 = r + 15; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10 + d11 + d12 + d13 + d14 + d15) & 0); }
    @Suspendable static int top16(long v, boolean two) throws SuspendExecution { long r = poison(v, two); long d1 = r + 1; long d2 = r + 2; long d3 = r + 3; long d4 = r + 4; long d5 = r + 5; long d6 = r + 6; long d7 = r + 7; long d8 = r + 8; long d9 = r + 9; long d10 = r + 10; long d11 = r + 11; long d12 = r + 12; long d13 = r + 13; long d14 = r + 14; long d15 = r + 15; long d16 = r + 16; int y = mid(); return y + (int) ((r + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10 + d11 + d12 + d13 + d14 + d15 + d16) & 0); }

    static final class Task implements SuspendableCallable<Integer> {
        final int k;
        final long v;
        final boolean two;

        Task(int k, long v, boolean two) {
            this.k = k;
            this.v = v;
            this.two = two;
        }

        @Override
        public Integer run() throws SuspendExecution, InterruptedException {
            switch (k) {
                case 0: return top0(v, two);
                case 1: return top1(v, two);
                case 2: return top2(v, two);
                case 3: return top3(v, two);
                case 4: return top4(v, two);
                case 5: return top5(v, two);
                case 6: return top6(v, two);
                case 7: return top7(v, two);
                case 8: return top8(v, two);
                case 9: return top9(v, two);
                case 10: return top10(v, two);
                case 11: return top11(v, two);
                case 12: return top12(v, two);
                case 13: return top13(v, two);
                case 14: return top14(v, two);
                case 15: return top15(v, two);
                default: return top16(v, two);
            }
        }
    }

    private static void sweep(long value, boolean twoSites) throws Exception {
        List<String> corrupted = new ArrayList<>();
        for (int k = 0; k <= 16; k++) {
            int y = new Fiber<>(new Task(k, value, twoSites)).start().get();
            if (y != VICTIM_OK)
                corrupted.add("K=" + k + " -> " + y);
        }
        assertEquals("victim() mis-resumed for " + (twoSites ? "twoSites" : "oneSite") + " poisoner: " + corrupted,
                0, corrupted.size());
    }

    @Test
    public void safeValuesOneSite() throws Exception {
        sweep(SAFE, false);
    }

    @Test
    public void safeValuesTwoSites() throws Exception {
        sweep(SAFE, true);
    }

    @Test
    public void poisonedValuesOneSite() throws Exception {
        sweep(POISON, false);
    }

    @Test
    public void poisonedValuesTwoSites() throws Exception {
        sweep(POISON, true);
    }
}
