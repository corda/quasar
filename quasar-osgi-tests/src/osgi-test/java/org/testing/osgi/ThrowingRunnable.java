package org.testing.osgi;

@FunctionalInterface
interface ThrowingRunnable {
    void run() throws Exception;
}
