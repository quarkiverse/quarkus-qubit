package io.quarkiverse.qubit.runtime;

/**
 * Thrown when captured variable extraction from lambda instance fails.
 */
public class CapturedVariableExtractionException extends RuntimeException {

    public CapturedVariableExtractionException(String message) {
        super(message);
    }

    public CapturedVariableExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
