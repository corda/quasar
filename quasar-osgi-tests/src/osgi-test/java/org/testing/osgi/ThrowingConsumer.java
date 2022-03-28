package org.testing.osgi;

@FunctionalInterface
interface ThrowingConsumer<T> {
    void accept(T obj) throws Exception;
}
