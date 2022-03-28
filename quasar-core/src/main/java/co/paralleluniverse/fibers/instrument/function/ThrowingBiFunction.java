package co.paralleluniverse.fibers.instrument.function;

import java.util.function.BiFunction;

/**
 * Extends {@link BiFunction} to support checked exceptions.
 */
@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {
    R applyThrowing(T a, U b) throws Exception;

    default R apply(T a, U b) {
        try {
            return applyThrowing(a, b);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
