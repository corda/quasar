package co.paralleluniverse.fibers;

import co.paralleluniverse.fibers.suspend.SuspendExecution;
import co.paralleluniverse.strands.SuspendableCallable;
import org.junit.Test;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Park a fiber three frames deep, serialise it with Quasar's own fiber serializer (what Corda checkpoints use),
 * deserialise into a fresh Fiber object and let it finish. Checks that the frame chain is resumed, not restarted.
 */
public class SerializeResumeTest {
    static final AtomicReference<byte[]> BYTES = new AtomicReference<>();
    static volatile int innerEntered = 0;
    static long STATIC_ZERO = 0;   // a field read before the suspendable call defeats the "forwarding method" optimisation, so the frame is instrumented

    @Suspendable
    static long inner(long v) throws SuspendExecution {
        innerEntered++;
        long a = v + 1;
        Fiber.parkAndSerialize((fiber, ser) -> BYTES.set(ser.write(fiber)));
        return a * 2;
    }

    @Suspendable
    static long middle(long v) throws SuspendExecution {
        long b = v * 10 + STATIC_ZERO;
        long r = inner(b);
        return r + b + 3;          // b is live across the suspendable call, so this frame must be saved
    }

    static final class Task implements SuspendableCallable<Long> {
        @Override
        public Long run() throws SuspendExecution, InterruptedException {
            long k = 100 + STATIC_ZERO;
            long r = middle(5);
            return r + k;           // k is live across the suspendable call
        }
    }

    static String describeStack(Fiber<?> f) throws Exception {
        java.lang.reflect.Field fs = Fiber.class.getDeclaredField("stack"); fs.setAccessible(true);
        Object st = fs.get(f);
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"sp", "maxFrame", "pushed"}) {
            try { java.lang.reflect.Field x = st.getClass().getDeclaredField(name); x.setAccessible(true); sb.append(name).append('=').append(x.get(st)).append(' '); }
            catch (NoSuchFieldException e) { sb.append(name).append("=n/a "); }
        }
        java.lang.reflect.Field d = st.getClass().getDeclaredField("dataLong"); d.setAccessible(true); long[] dl = (long[]) d.get(st);
        sb.append("raw:");
        for (int i = 0; i < 14; i++) sb.append(' ').append(Long.toHexString(dl[i]));
        sb.append(" records:");
        int idx = 0;
        while (idx < dl.length && (dl[idx] >>> 50) != 0) { long r = dl[idx]; int entry = (int) (r >>> 50); int numSlots = (int) ((r >>> 34) & 0xffff); sb.append(" [").append(idx).append(":entry=").append(entry).append(",slots=").append(numSlots).append(']'); idx += 1 + numSlots; }
        return sb.toString();
    }

    @Test
    public void testSerializeResume() throws Exception {
        System.out.println("Stack class loaded from: " + co.paralleluniverse.fibers.Stack.class.getProtectionDomain().getCodeSource().getLocation().toString().replaceAll(".*/(scratchpad|modules-2)/", ""));
        FiberScheduler scheduler = new FiberForkJoinScheduler("test", 2);
        Fiber<Long> original = new Fiber<>(scheduler, new Task()).start();
        long deadline = System.currentTimeMillis() + 10_000;
        while (BYTES.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertNotNull("FAILED: fiber never serialised", BYTES.get());
        System.out.println("serialised fiber: " + BYTES.get().length + " bytes, inner() entered " + innerEntered + " time(s)");

        @SuppressWarnings("unchecked")
        Fiber<Long> restored = (Fiber<Long>) Fiber.getFiberSerializer().read(BYTES.get());
        System.out.println("after deserialization: " + describeStack(restored));
        Fiber.unparkDeserialized(restored, scheduler);
        try {
            long result = restored.get(20, java.util.concurrent.TimeUnit.SECONDS);
            long expected = (5 * 10 + 1) * 2 + 50 + 3 + 100;   // 255
            System.out.println("restored fiber result=" + result + " expected=" + expected + ", inner() entered " + innerEntered + " time(s) in total");
            scheduler.shutdown();
            assertTrue("RESUME BROKEN (frames re-executed or wrong locals)", result == expected && innerEntered == 1);
            System.out.println("RESUME OK");
        } catch (Exception t) {
            System.out.println("restored fiber did not complete: " + t + ", inner() entered " + innerEntered + " time(s) in total");
            fail("RESUME BROKEN (" + t.getClass().getSimpleName() + ")");
        }
    }
}

