package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

/**
 * Implementation of {@link PhoneQueryOperations} using static entity methods.
 */
public class StaticPhoneQueryOperations implements PhoneQueryOperations {

    public static final StaticPhoneQueryOperations INSTANCE = new StaticPhoneQueryOperations();

    private StaticPhoneQueryOperations() {
    }

    @Override
    public QubitStream<Phone> where(QuerySpec<Phone, Boolean> spec) {
        return Phone.where(spec);
    }
}
