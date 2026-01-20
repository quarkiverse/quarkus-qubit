package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

/**
 * Abstraction for Tag query operations.
 */
public interface TagQueryOperations {

    QubitStream<Tag> where(QuerySpec<Tag, Boolean> spec);
}
