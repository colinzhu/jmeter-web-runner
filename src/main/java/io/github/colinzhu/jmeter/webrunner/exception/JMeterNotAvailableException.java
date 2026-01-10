package io.github.colinzhu.jmeter.webrunner.exception;

/**
 * Exception thrown when JMeter binary is not available at the configured path.
 */
public class JMeterNotAvailableException extends RuntimeException {

    public JMeterNotAvailableException(String message) {
        super(message);
    }

    public JMeterNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
