package io.quarkiverse.qubit.it;

import io.quarkiverse.qubit.QubitRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Phone entities using the Repository pattern with fluent API query methods.
 */
@ApplicationScoped
public class PhoneRepository implements QubitRepository<Phone, Long> {
    // QubitRepository methods are auto-generated via @GenerateBridge at build time
}
