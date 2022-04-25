package co.paralleluniverse.fibers.dynamic;

@SuppressWarnings("unused")
public class DynamicallyLoadedRuntimeException extends RuntimeException {
    public DynamicallyLoadedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public DynamicallyLoadedRuntimeException(String message) {
        super(message);
    }
}
