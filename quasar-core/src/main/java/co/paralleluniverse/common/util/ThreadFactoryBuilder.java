package co.paralleluniverse.common.util;

import java.util.concurrent.ThreadFactory;

public final class ThreadFactoryBuilder {
    private String nameFormat = "quasar-thread-%d";
    private boolean isDaemon;

    public ThreadFactoryBuilder setNameFormat(String nameFormat) {
        this.nameFormat = nameFormat;
        return this;
    }

    public ThreadFactoryBuilder setDaemon(boolean isDaemon) {
        this.isDaemon = isDaemon;
        return this;
    }

    public ThreadFactory build() {
        return new ThreadFactoryImpl(nameFormat, isDaemon);
    }

    private static class ThreadFactoryImpl implements ThreadFactory {
        private final String nameFormat;
        private final boolean isDaemon;
        private long counter;

        ThreadFactoryImpl(String nameFormat, boolean isDaemon) {
            this.nameFormat = nameFormat;
            this.isDaemon = isDaemon;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(runnable, String.format(nameFormat, counter++));
            t.setDaemon(isDaemon);
            return t;
        }
    }
}
