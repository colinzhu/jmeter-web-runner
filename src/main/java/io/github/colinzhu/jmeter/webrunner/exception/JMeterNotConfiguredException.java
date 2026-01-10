package io.github.colinzhu.jmeter.webrunner.exception;

/**
 * Exception thrown when JMeter is not configured but an operation requires it.
 */
public class JMeterNotConfiguredException extends RuntimeException {

    public JMeterNotConfiguredException(String message) {
        super(message);
    }

    public JMeterNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
