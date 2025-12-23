package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.runtime.QuerySpec;
import io.quarkiverse.qubit.runtime.QubitStream;

/**
 * Abstraction for Phone query operations.
 */
public interface PhoneQueryOperations {

    QubitStream<Phone> where(QuerySpec<Phone, Boolean> spec);
}
