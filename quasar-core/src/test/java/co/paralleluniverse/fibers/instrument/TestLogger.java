package co.paralleluniverse.fibers.instrument;

import org.slf4j.Logger;

public final class TestLogger implements Log {
    private final Logger logger;

    public TestLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(LogLevel level, String msg, Object... args) {
        switch (level) {
            case DEBUG:
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format(msg, args));
                }
                break;

            case INFO:
                if (logger.isInfoEnabled()) {
                    logger.info(String.format(msg, args));
                }
                break;

            case WARNING:
                if (logger.isWarnEnabled()) {
                    logger.warn(String.format(msg, args));
                }
                break;
        }
    }

    @Override
    public void error(String msg, Throwable ex) {
        logger.error(msg, ex);
    }
}
