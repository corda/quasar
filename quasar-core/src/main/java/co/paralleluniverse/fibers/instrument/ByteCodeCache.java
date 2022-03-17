package co.paralleluniverse.fibers.instrument;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

interface ByteCodeCache {
    byte[] computeIfAbsent(String className, byte[] byteCode, Supplier<byte[]> computer);

    /**
     * Common key type used by all byte-code caches.
     */
    final class CacheKey {
        private final String className;
        private final int byteCodeLength;
        private final byte[] byteCodeHash;
        private final int hashCode;

        private CacheKey(String className, int byteCodeLength, byte[] byteCodeHash) {
            this.className = className;
            this.byteCodeLength = byteCodeLength;
            this.byteCodeHash = byteCodeHash;
            this.hashCode = Objects.hash(className, byteCodeLength, Arrays.hashCode(byteCodeHash));
        }

        int getByteCodeLength() {
            return byteCodeLength;
        }

        byte[] getByteCodeHash() {
            return Arrays.copyOf(byteCodeHash, byteCodeHash.length);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            return className.equals(other.className)
                && byteCodeLength == other.byteCodeLength
                && Arrays.equals(byteCodeHash, other.byteCodeHash);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    final class CacheKeyFactory {
        private final MessageDigest digester;

        private CacheKeyFactory(MessageDigest digester) {
            this.digester = digester;
        }

        CacheKey createKey(String className, byte[] byteCode) {
            return new CacheKey(className, byteCode.length, digester.digest(byteCode));
        }
    }

    static CacheKeyFactory createKeyFactory(String algorithmName) throws NoSuchAlgorithmException {
        MessageDigest digester = MessageDigest.getInstance(algorithmName);
        return new CacheKeyFactory(digester);
    }
}
