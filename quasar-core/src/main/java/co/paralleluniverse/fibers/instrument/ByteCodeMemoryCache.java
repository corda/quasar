package co.paralleluniverse.fibers.instrument;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

final class ByteCodeMemoryCache implements ByteCodeCache {
    private final ConcurrentMap<CacheKey, byte[]> cache;
    private final CacheKeyFactory keyFactory;

    ByteCodeMemoryCache(CacheKeyFactory keyFactory) {
        cache = new ConcurrentHashMap<>();
        this.keyFactory = keyFactory;
    }

    @Override
    public byte[] computeIfAbsent(String className, byte[] byteCode, Supplier<byte[]> computer) {
        if (byteCode == null) {
            return null;
        }

        // ConcurrentHashMap.computeIfAbsent() uses locking,
        // even if it already contains a value for our key.
        final CacheKey cacheKey = keyFactory.createKey(className, byteCode);
        byte[] cachedByteCode;
        if ((cachedByteCode = cache.get(cacheKey)) == null) {
            cachedByteCode = cache.computeIfAbsent(cacheKey, key -> computer.get());
        }
        return cachedByteCode;
    }
}
