package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

/**
 * Implementation of {@link TagQueryOperations} using static entity methods.
 */
public class StaticTagQueryOperations implements TagQueryOperations {

    public static final StaticTagQueryOperations INSTANCE = new StaticTagQueryOperations();

    private StaticTagQueryOperations() {
    }

    @Override
    public QubitStream<Tag> where(QuerySpec<Tag, Boolean> spec) {
        return Tag.where(spec);
    }
}
