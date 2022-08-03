package org.testing.osgi;

@FunctionalInterface
interface ThrowingRunnable extends Runnable {
    void throwingRun() throws Exception;

    @Override
    default void run() {
        try {
            throwingRun();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
