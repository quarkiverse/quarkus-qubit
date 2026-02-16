package io.quarkiverse.qubit.runtime.internal;

import java.util.Optional;

import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.ScalarResult;

/**
 * Wraps a {@link QubitStream} with aggregation state, restricting the API to
 * scalar-appropriate terminal operations only.
 */
public final class ScalarResultImpl<T> implements ScalarResult<T> {

    private final QubitStream<T> delegate;

    public ScalarResultImpl(QubitStream<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T getSingleResult() {
        return delegate.getSingleResult();
    }

    @Override
    public Optional<T> findFirst() {
        return delegate.findFirst();
    }
}
