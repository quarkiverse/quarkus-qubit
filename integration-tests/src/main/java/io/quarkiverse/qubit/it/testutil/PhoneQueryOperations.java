package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

/**
 * Abstraction for Phone query operations.
 */
public interface PhoneQueryOperations {

    QubitStream<Phone> where(QuerySpec<Phone, Boolean> spec);
}
