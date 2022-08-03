package co.paralleluniverse.fibers;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link ConcurrentMap} where the keys are "weak".
 * This means that keys can be garbage-collected once no strong
 * references to them exist, and then their entries will automatically
 * disappear from the map too.
 * @param <K> Generic key type.
 * @param <V> Generic value type.
 *
 * This implementation is (more than) sufficient for Quasar, although
 * some map operations are not supported.
 */
final class ConcurrentWeakKeyMap<K, V> implements ConcurrentMap<K, V> {
    private final ConcurrentMap<WeakKey<K>, V> references = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();

    @Override
    public int size() {
        purgeStaleEntries();
        return references.size();
    }

    @Override
    public boolean isEmpty() {
        purgeStaleEntries();
        return references.isEmpty();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public void clear() {
        references.clear();
        while (queue.poll() != null) {}
    }

    @Override
    public boolean containsKey(Object key) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.containsKey(new WeakKey<>(key));
    }

    @Override
    public boolean containsValue(Object value) {
        purgeStaleEntries();
        return references.containsValue(value);
    }

    @Override
    public V get(Object key) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.get(new WeakKey<>(key));
    }

    @Override
    public V put(K key, V value) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.put(new WeakKey<>(key, queue), value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        requireNonNull(map, "map cannot be null");
        purgeStaleEntries();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            final K key = entry.getKey();
            requireNonNull(key, () -> "key for value " + entry.getValue() + " cannot be null");
            references.put(new WeakKey<>(key, queue), entry.getValue());
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.putIfAbsent(new WeakKey<>(key, queue), value);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.computeIfAbsent(new WeakKey<>(key, queue), mappingFunction.compose(WeakKey::get));
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.computeIfPresent(new WeakKey<>(key, queue), (wk, value) -> remappingFunction.apply(wk.get(), value));
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.compute(new WeakKey<>(key, queue), (wk, value) -> remappingFunction.apply(wk.get(), value));
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.merge(new WeakKey<>(key, queue), value, remappingFunction);
    }

    @Override
    public V remove(Object key) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.remove(new WeakKey<>(key));
    }

    @Override
    public boolean remove(Object key, Object value) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.remove(new WeakKey<>(key), value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.replace(new WeakKey<>(key, queue), oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        requireNonNull(key, "key cannot be null");
        purgeStaleEntries();
        return references.replace(new WeakKey<>(key, queue), value);
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        purgeStaleEntries();
        references.replaceAll((wk, value) -> {
            final K key;
            return ((key = wk.get()) == null) ? value : function.apply(key, value);
        });
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        purgeStaleEntries();
        references.forEach((wk, value) -> {
            final K key;
            if ((key = wk.get()) != null) {
                action.accept(key, value);
            } else {
                wk.enqueue();
            }
        });
    }

    @Override
    public Collection<V> values() {
        purgeStaleEntries();
        return references.values();
    }

    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException("keySet() not supported");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("entrySet() not supported");
    }

    private void purgeStaleEntries() {
        WeakKey<?> key;
        while ((key = (WeakKey<?>) queue.poll()) != null) {
            // This key must have been enqueued by the garbage collector,
            // and so no longer contains an object. Which means key.equals(obj)
            // can now only return true when obj === key.
            if (references.remove(key) == null) {
                System.err.println("[quasar] Failed to purge stale entry " + key + " from ConcurrentWeakKeyMap");
            }
        }
    }

    private static final class WeakKey<K> extends WeakReference<K> {
        private final int hash;

        WeakKey(K key, ReferenceQueue<K> queue) {
            super(key, queue);
            this.hash = System.identityHashCode(key);
        }

        WeakKey(K key) {
            this(key, null);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof WeakKey)) {
                return false;
            }

            // Equality is via object identity.
            final Object key;
            return ((key = get()) != null) && key == ((WeakKey<?>) obj).get();
        }

        @Override
        public String toString() {
            return "WeakKey[hash=" + hash + ']';
        }
    }
}
