package co.paralleluniverse.fibers.instrument.function;

/**
 * Extends {@link java.util.function.BiFunction} to support checked exceptions.
 */
@FunctionalInterface
public interface BiFunction<T, U, R> extends java.util.function.BiFunction<T, U, R> {
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
