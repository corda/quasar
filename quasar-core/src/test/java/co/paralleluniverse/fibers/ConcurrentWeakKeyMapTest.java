package co.paralleluniverse.fibers;

import org.junit.Before;
import org.junit.Test;

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConcurrentWeakKeyMapTest {
    private static final long PAUSE_MILLIS = 100;
    private static final int MAX_THREADS = 8;

    private ConcurrentWeakKeyMap<String, Object> weakMap;

    @Before
    public void setup() {
        weakMap = new ConcurrentWeakKeyMap<>();
    }

    @Test
    public void testBasicUsage() {
        final String key = keyOf("KEY");

        // Add an element...
        assertNull(weakMap.put(key, 100L));
        assertEquals(100L, weakMap.get(key));
        assertEquals(1, weakMap.size());
        assertFalse(weakMap.isEmpty());

        // Then remove it again.
        assertEquals(100L, weakMap.remove(key));
        assertEquals(0, weakMap.size());
        assertTrue(weakMap.isEmpty());
    }

    @Test
    public void testKeysUseObjectIdentity() {
        final String key1 = keyOf("KEY");
        final String key2 = keyOf("KEY");
        final String key3 = keyOf("KEY");

        // Sanity-check our key objects.
        assertNotSame(key1, key2);
        assertNotSame(key1, key3);
        assertNotSame(key2, key3);
        assertEquals(key1, key2);
        assertEquals(key2, key3);

        assertNull(weakMap.put(key1, 100));
        assertNull(weakMap.put(key2, 200));
        assertNull(weakMap.put(key3, 300));
        assertEquals(3, weakMap.size());

        assertEquals(200, weakMap.remove(key2));
        assertEquals(2, weakMap.size());
        assertEquals(300, weakMap.remove(key3));
        assertEquals(1, weakMap.size());
        assertEquals(100, weakMap.remove(key1));
        assertEquals(0, weakMap.size());
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testKeysAreWeak() throws InterruptedException {
        String key = keyOf("KEY");
        assertNull(weakMap.put(key, "VALUE"));
        assertEquals(1, weakMap.size());

        // Remove strong reference to key object
        key = null;
        System.gc();

        repeatUntil(60, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testPutIfAbsent() throws InterruptedException {
        final String key1 = keyOf("KEY");
        assertNull(weakMap.putIfAbsent(key1, 1000));
        assertEquals(1000, weakMap.putIfAbsent(key1, 2000));
        assertEquals(1000, weakMap.get(key1));
        assertEquals(1, weakMap.size());

        String key2 = keyOf("KEY");
        assertNull(weakMap.putIfAbsent(key2, -2000));
        assertEquals(-2000, weakMap.putIfAbsent(key2, 3000));
        assertEquals(2, weakMap.size());

        // Remove strong reference to key2 object
        key2 = null;
        System.gc();

        repeatUntil(60, () ->
            assertEquals(1, weakMap.size())
        );

        // Ensure we keep this strong reference to key1 throughout.
        // Prevent an optimising compiler from discarding it early!
        assertNotNull(key1);
    }

    @Test
    public void testRemoveValue() {
        final String key = keyOf("REMOVE");
        assertNull(weakMap.put(key, "TWO"));
        assertFalse(weakMap.remove(key, "ONE"));
        assertTrue(weakMap.remove(key, "TWO"));
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testReplaceValue() throws InterruptedException {
        String key = keyOf("REPLACE");

        assertNull(weakMap.replace(key, "ONE"));
        assertNull(weakMap.put(key, "TWO"));
        assertEquals("TWO", weakMap.replace(key, "THREE"));
        assertEquals("THREE", weakMap.get(key));
        assertEquals(1, weakMap.size());

        // Remove strong reference to key object
        key = null;
        System.gc();

        repeatUntil(30, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testReplaceSpecificValue() throws InterruptedException {
        String key = keyOf("REPLACE");

        assertNull(weakMap.put(key, "ZERO"));
        assertFalse(weakMap.replace(key, "ONE", "TWO"));
        assertTrue(weakMap.replace(key, "ZERO", "THREE"));
        assertEquals("THREE", weakMap.get(key));
        assertEquals(1, weakMap.size());

        // Remove strong reference to key object
        key = null;
        System.gc();

        repeatUntil(30, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @Test(timeout = 10000)
    public void testConcurrentUsage() throws Exception {
        final List<String> values = IntStream.range(0, 1000)
            .mapToObj(Integer::toString)
            .collect(toUnmodifiableList());

        final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        final ExecutorCompletionService<String> completion = new ExecutorCompletionService<>(executor);
        final Set<Future<String>> futures;
        try {
            futures = values.stream().map(value -> {
                final String keyObj = keyOf(value);
                return completion.submit(() -> weakMap.put(keyObj, value), keyObj);
            }).collect(toCollection(LinkedHashSet::new));
        } finally {
            executor.shutdown();
        }
        assertThat(futures).hasSameSizeAs(values);

        final Deque<String> strongKeys = new LinkedList<>();
        while (!futures.isEmpty()) {
            final Future<String> future = completion.take();
            strongKeys.addLast(future.get());
            futures.remove(future);
        }

        assertThat(weakMap.values())
            .containsExactlyInAnyOrderElementsOf(values)
            .hasSameSizeAs(strongKeys);

        // Check "weak" property behaves as expected.
        dropFirstHalf(strongKeys);
        System.gc();

        repeatUntil(10, () ->
            assertThat(weakMap.values()).hasSameSizeAs(strongKeys)
        );
        for (String strongKey : strongKeys) {
            assertThat(weakMap).containsKey(strongKey);
        }
    }

    private static String keyOf(String key) {
        return '[' + key + ']';
    }

    private void dropFirstHalf(Deque<?> items) {
        int halfSize = items.size() / 2;
        while (--halfSize >= 0) {
            items.removeFirst();
        }
    }

    private void repeatUntil(long seconds, Runnable action) throws InterruptedException {
        long remainingNanos = SECONDS.toNanos(seconds);
        while (remainingNanos > 0) {
            long startTime = System.nanoTime();
            try {
                action.run();
                return;
            } catch (AssertionError e) {
                Thread.sleep(PAUSE_MILLIS);
                remainingNanos -= (System.nanoTime() - startTime);
            }
        }
        fail("Failed after " + seconds + " seconds");
    }
}
