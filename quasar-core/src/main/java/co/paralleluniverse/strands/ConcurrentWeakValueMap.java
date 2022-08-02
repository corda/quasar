package co.paralleluniverse.strands;

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
 * Implementation of {@link ConcurrentMap} where the values are "weak".
 * This means that values can be garbage-collected once no strong
 * references to them exist, and then their entries will automatically
 * disappear from the map too.
 * @param <K> Generic key type.
 * @param <V> Generic value type.
 *
 * This implementation is (more than) sufficient for Quasar, although
 * some map operations are not supported.
 */
final class ConcurrentWeakValueMap<K, V> implements ConcurrentMap<K, V> {
    private final ConcurrentMap<K, WeakEntry<K, V>> references = new ConcurrentHashMap<>();
    private final ReferenceQueue<V> queue = new ReferenceQueue<>();

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
        // A key mapping to an empty WeakEntry is not good enough,
        // and so we cannot just invoke the underlying containsKey().
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        requireNonNull(value, "value cannot be null");
        purgeStaleEntries();
        return references.containsValue(new WeakEntry<>(value));
    }

    @Override
    public V get(Object key) {
        purgeStaleEntries();
        return mapValueOf(references.get(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        requireNonNull(defaultValue, "defaultValue cannot be null");
        return ConcurrentMap.super.getOrDefault(key, defaultValue);
    }

    @Override
    public V put(K key, V value) {
        requireNonNull(value, "value cannot be null");
        purgeStaleEntries();
        return valueOf(references.put(key, new WeakEntry<>(key, value, queue)));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        requireNonNull(map, "map cannot be null");
        purgeStaleEntries();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            final K key = entry.getKey();
            final V value = entry.getValue();
            requireNonNull(value, () -> "value for key " + key + " cannot be null");
            references.put(key, new WeakEntry<>(key, value, queue));
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        requireNonNull(value, "value cannot be null");

        // The map's "if-absent" check cannot work if this key
        // maps to an empty WeakEntry value. There is also a brief
        // interval between the GC clearing the WeakEntry and the
        // GC adding it to the reference queue for purging.
        // We must remove the entry ourselves in that case.
        purgeStaleEntries();

        final WeakEntry<K, V> newEntry = new WeakEntry<>(key, value, queue);
        for (;;) {
            final WeakEntry<K, V> oldEntry = references.putIfAbsent(key, newEntry);
            if (oldEntry == null) {
                return null;
            }

            final V oldValue;
            if ((oldValue = oldEntry.get()) != null) {
                return oldValue;
            }

            // This dead entry is preventing us from adding our new value - remove it!
            references.remove(key, oldEntry);
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        // The map's "if-absent" check cannot work if this key
        // maps to an empty WeakEntry value. There is also a brief
        // interval between the GC clearing the WeakEntry and the
        // GC adding it to the reference queue for purging.
        // We must remove the entry ourselves in that case.
        purgeStaleEntries();

        final ValueGuard<V> valueGuard = new ValueGuard<>();
        for (;;) {
            final WeakEntry<K, V> currentEntry = references.computeIfAbsent(key, mappingFunction.andThen(value ->
                (value == null) ? null : new WeakEntry<>(key, valueGuard.protect(value), queue)
            ));
            if (currentEntry == null) {
                return null;
            }

            final V currentValue;
            if ((currentValue = currentEntry.get()) != null) {
                return currentValue;
            }

            // This dead entry is preventing us from adding our new value - remove it!
            references.remove(key, currentEntry);
        }
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        purgeStaleEntries();
        final ValueGuard<V> valueGuard = new ValueGuard<>();
        return valueOf(references.computeIfPresent(key, (k, oldEntry) -> {
            final V oldValue;
            if ((oldValue = oldEntry.get()) == null) {
                return null;
            }
            final V newValue = remappingFunction.apply(k, oldValue);
            return (newValue == null) ? null : new WeakEntry<>(k, valueGuard.protect(newValue), queue);
        }));
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        purgeStaleEntries();
        final ValueGuard<V> valueGuard = new ValueGuard<>();
        return valueOf(references.compute(key, (k, oldEntry) -> {
            final V oldValue = valueOf(oldEntry);
            final V newValue = remappingFunction.apply(k, oldValue);
            return (newValue == null) ? null : new WeakEntry<>(k, valueGuard.protect(newValue), queue);
        }));
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        requireNonNull(value, "value cannot be null");
        purgeStaleEntries();
        final ValueGuard<V> valueGuard = new ValueGuard<>();
        return valueOf(references.merge(key, new WeakEntry<>(key, value, queue), (oldEntry, newEntry) -> {
            final V oldValue;
            if ((oldValue = valueOf(oldEntry)) == null) {
                return newEntry;
            }
            final V newValue = remappingFunction.apply(oldValue, newEntry.get());
            return (newValue == null) ? null : new WeakEntry<>(key, valueGuard.protect(newValue), queue);
        }));
    }

    @Override
    public V remove(Object key) {
        purgeStaleEntries();
        return valueOf(references.remove(key));
    }

    @Override
    public boolean remove(Object key, Object value) {
        purgeStaleEntries();
        return references.remove(key, new WeakEntry<>(value));
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        requireNonNull(oldValue, "oldValue cannot be null");
        requireNonNull(newValue, "newValue cannot be null");
        purgeStaleEntries();
        return references.replace(key, new WeakEntry<>(oldValue), new WeakEntry<>(key, newValue, queue));
    }

    @Override
    public V replace(K key, V value) {
        requireNonNull(value, "value cannot be null");
        purgeStaleEntries();

        // A key mapping to an empty WeakEntry is not good enough,
        // and so we cannot just replace this entry blindly.
        final WeakEntry<K, V> newEntry = new WeakEntry<>(key, value, queue);
        for (;;) {
            final WeakEntry<K, V> oldEntry;
            if ((oldEntry = references.get(key)) == null) {
                return null;
            }

            final V oldValue;
            if ((oldValue = oldEntry.get()) == null) {
                if (references.remove(key, oldEntry)) {
                    return null;
                }
            } else if (references.replace(key, oldEntry, newEntry)) {
                return oldValue;
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException("replaceAll() not supported");
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        purgeStaleEntries();
        references.forEach((key, entry) -> {
            final V value;
            if ((value = mapValueOf(entry)) != null) {
                action.accept(key, value);
            }
        });
    }

    @Override
    public Set<K> keySet() {
        purgeStaleEntries();
        return references.keySet();
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException("values() not supported");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("entrySet() not supported");
    }

    @SuppressWarnings("unchecked")
    private void purgeStaleEntries() {
        WeakEntry<K, V> entry;
        while ((entry = (WeakEntry<K, V>) queue.poll()) != null) {
            // This entry must have been enqueued by the garbage collector,
            // and so no longer contains an object. Which means entry.equals(obj)
            // can now only return true when obj === entry.
            references.remove(entry.getKey(), entry);
        }
    }

    // Extract the value from a WeakEntry that
    // currently belongs to the references map.
    private static <V> V mapValueOf(WeakEntry<?, V> entry) {
        if (entry == null) {
            return null;
        } else {
            final V value;
            if ((value = entry.get()) == null) {
                // The garbage collector does not clear
                // and enqueue references atomically.
                // Enqueue it manually to avoid waiting.
                entry.enqueue();
            }
            return value;
        }
    }

    private static <V> V valueOf(WeakEntry<?, V> entry) {
        return (entry != null) ? entry.get(): null;
    }

    // Holds a strong reference to the value inside,
    // to protect it from being garbage collected.
    private static final class ValueGuard<V> {
        private V value;

        V protect(V value) {
            return (this.value = value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static final class WeakEntry<K, V> extends WeakReference<V> {
        private final int valueHash;
        private final K key;

        WeakEntry(K key, V value, ReferenceQueue<V> queue) {
            super(value, queue);
            this.valueHash = System.identityHashCode(value);
            this.key = key;
        }

        WeakEntry(V value) {
            this(null, value, null);
        }

        K getKey() {
            return key;
        }

        @Override
        public int hashCode() {
            return valueHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof WeakEntry)) {
                return false;
            }

            // Equality is via object identity.
            final Object value;
            return ((value = get()) != null) && value == ((WeakEntry<?,?>) obj).get();
        }

        @Override
        public String toString() {
            return "WeakEntry[key=" + key + ", valueHash=" + valueHash + ']';
        }
    }
}
