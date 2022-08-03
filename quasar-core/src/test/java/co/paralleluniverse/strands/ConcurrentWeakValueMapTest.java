package co.paralleluniverse.strands;

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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConcurrentWeakValueMapTest {
    private static final long PAUSE_MILLIS = 100;
    private static final int MAX_THREADS = 8;

    private ConcurrentWeakValueMap<String, String> weakMap;

    @Before
    public void setup() {
        weakMap = new ConcurrentWeakValueMap<>();
    }

    @Test
    public void testBasicUsage() {
        final String value = valueOf("VALUE");
        final String key = "KEY";

        // Add an element...
        assertNull(weakMap.put(key, value));
        assertEquals(value, weakMap.get(key));
        assertEquals(1, weakMap.size());
        assertFalse(weakMap.isEmpty());

        // Then remove it again.
        assertEquals(value, weakMap.remove(key));
        assertEquals(0, weakMap.size());
        assertTrue(weakMap.isEmpty());
    }

    @Test
    public void testValuesUseObjectIdentity() {
        final String value1 = valueOf("VALUE");
        final String value2 = valueOf("VALUE");
        final String value3 = valueOf("VALUE");

        // Sanity-check our value objects.
        assertNotSame(value1, value2);
        assertNotSame(value1, value3);
        assertNotSame(value2, value3);
        assertEquals(value1, value2);
        assertEquals(value2, value3);

        assertNull(weakMap.put("ONE", value1));
        assertNull(weakMap.put("TWO", value2));
        assertEquals(2, weakMap.size());
        assertTrue(weakMap.containsValue(value1));
        assertTrue(weakMap.containsValue(value2));
        assertFalse(weakMap.containsValue(value3));
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testValuesAreWeak() throws InterruptedException {
        String value = valueOf("VALUE");
        assertNull(weakMap.put("KEY", value));
        assertEquals(1, weakMap.size());

        // Remove strong reference to value object
        value = null;
        System.gc();

        repeatUntil(60, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testContainsKey() {
        final String key = "KEY";
        String value = valueOf("VALUE");
        assertNull(weakMap.put(key, value));
        assertTrue("Key " + key + " not found", weakMap.containsKey(key));

        // Remove strong reference to value object
        value = null;
        System.gc();

        assertFalse("Key " + key + " still present", weakMap.containsKey(key));
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testGetOrDefaultValue() {
        final String key = "KEY";
        final String defaultValue = "default-value";
        String value = valueOf("VALUE");
        assertNull(weakMap.put(key, value));
        assertEquals(value, weakMap.getOrDefault(key, defaultValue));

        // Remove strong reference to value object
        value = null;
        System.gc();

        assertEquals(defaultValue, weakMap.getOrDefault(key, defaultValue));
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testPutIfAbsent() throws InterruptedException {
        final String key = "KEY";
        String value1 = valueOf("VALUE1");
        assertNull(weakMap.putIfAbsent(key, value1));

        final String value2 = valueOf("VALUE2");
        assertEquals(value1, weakMap.putIfAbsent(key, value2));
        assertEquals(1, weakMap.size());

        // Remove strong reference to value1 object
        value1 = null;
        System.gc();

        repeatUntil(60, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @Test
    public void testAggressivelyPutIfAbsent() {
        final String key = "KEY";
        assertNull(weakMap.putIfAbsent(key, valueOf("VALUE1")));
        assertEquals(1, weakMap.size());

        final String value2 = valueOf("VALUE2");
        System.gc();

        final String oldValue = weakMap.putIfAbsent(key, value2);
        assertEquals("And oldValue=" + oldValue, 1, weakMap.size());
        assertEquals(valueOf("VALUE2"), weakMap.get(key));
        assertNull(oldValue);
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testComputeIfAbsent() throws InterruptedException {
        final String key = "KEY";
        String value1 = weakMap.computeIfAbsent(key, k -> k + " - ONE");
        assertEquals(value1, weakMap.computeIfAbsent(key, k -> k + " - TWO"));
        assertEquals(1, weakMap.size());

        // Remove strong reference to value1 object
        value1 = null;
        System.gc();

        repeatUntil(60, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @Test
    public void testRemoveValue() {
        final String key = "KEY";
        final String value1 = valueOf("ONE");
        final String value2 = valueOf("TWO");
        assertNull(weakMap.put(key, value2));
        assertFalse(weakMap.remove(key, value1));
        assertTrue(weakMap.remove(key, value2));
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testReplaceValue() throws InterruptedException {
        final String key = "REPLACE";
        final String value1 = valueOf("ONE");
        final String value2 = valueOf("TWO");
        String value3 = valueOf("THREE");

        assertNull(weakMap.replace(key, value1));
        assertNull(weakMap.put(key, value2));
        assertEquals(value2, weakMap.replace(key, value3));
        assertEquals(value3, weakMap.get(key));
        assertEquals(1, weakMap.size());

        // Remove strong reference to value3 object
        value3 = null;
        System.gc();

        repeatUntil(30, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    public void testReplaceSpecificValue() throws InterruptedException {
        final String key = "REPLACE";
        final String value0 = valueOf("ZERO");
        final String value1 = valueOf("ONE");
        final String value2 = valueOf("TWO");
        String value3 = valueOf("THREE");

        assertNull(weakMap.put(key, value0));
        assertFalse(weakMap.replace(key, value1, value2));
        assertTrue(weakMap.replace(key, value0, value3));
        assertEquals(value3, weakMap.get(key));
        assertEquals(1, weakMap.size());

        // Remove strong reference to value3 object
        value3 = null;
        System.gc();

        repeatUntil(30, () ->
            assertEquals(0, weakMap.size())
        );
    }

    @Test
    public void testForEach() {
        final List<String> keys = IntStream.range(0, 6)
            .mapToObj(Integer::toString)
            .collect(toUnmodifiableList());

        final Deque<String> strongValues = new LinkedList<>();
        keys.forEach(key -> {
            final String value = valueOf(key);
            weakMap.put(key, value);
            strongValues.add(value);
        });

        assertThat(weakMap).hasSameSizeAs(strongValues);
        weakMap.forEach((key, value) ->
            assertEquals(valueOf(key), value)
        );

        final Deque<String> remainingValues = new LinkedList<>();
        dropFirstHalf(strongValues);
        System.gc();

        weakMap.forEach((key, value) -> {
            assertEquals(valueOf(key), value);
            remainingValues.add(value);
        });
        assertThat(remainingValues)
            .containsExactlyInAnyOrderElementsOf(strongValues)
            .hasSameSizeAs(weakMap.keySet());
    }

    @Test(timeout = 20000)
    public void testConcurrentUsage() throws Exception {
        final List<String> keys = IntStream.range(0, 1000)
            .mapToObj(Integer::toString)
            .collect(toUnmodifiableList());

        final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        final ExecutorCompletionService<String> completion = new ExecutorCompletionService<>(executor);
        final Set<Future<String>> futures;
        try {
            futures = keys.stream().map(key -> {
                final String valueObj = valueOf(key);
                return completion.submit(() -> weakMap.put(key, valueObj), valueObj);
            }).collect(toCollection(LinkedHashSet::new));
        } finally {
            executor.shutdown();
        }
        assertThat(futures).hasSameSizeAs(keys);

        final Deque<String> strongValues = new LinkedList<>();
        while (!futures.isEmpty()) {
            final Future<String> future = completion.take();
            strongValues.addLast(future.get());
            futures.remove(future);
        }

        assertThat(weakMap.keySet())
            .containsExactlyInAnyOrderElementsOf(keys)
            .hasSameSizeAs(strongValues);

        // Check "weak" property behaves as expected.
        dropFirstHalf(strongValues);
        System.gc();

        repeatUntil(10, () ->
            assertThat(weakMap.keySet()).hasSameSizeAs(strongValues)
        );
        for (String strongValue : strongValues) {
            assertThat(weakMap).containsValue(strongValue);
        }
    }

    private static String valueOf(String value) {
        return '[' + value + ']';
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
