package org.testing.osgi;

import java.util.function.Consumer;

@FunctionalInterface
interface ThrowingConsumer<T> extends Consumer<T> {
    void throwingAccept(T obj) throws Exception;

    default void accept(T obj) {
        try {
            throwingAccept(obj);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
