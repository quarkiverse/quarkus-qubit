package io.quarkiverse.qubit;

/**
 * Thrown when query executor registration fails during static initialization.
 */
public class QueryExecutorRegistrationException extends RuntimeException {

    public QueryExecutorRegistrationException(String message) {
        super(message);
    }

    public QueryExecutorRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
