package org.testing.osgi.supers;

import java.util.function.Function;

@SuppressWarnings("unused")
public class SuperClassAction implements Function<String, Exception> {
    @Override
    public Exception apply(String message) {
        try {
            throw new FourthException(message);
        } catch (FourthException | RuntimeException e) {
            System.err.println("Caught: " + e.getMessage());
            return e;
        }
    }
}
