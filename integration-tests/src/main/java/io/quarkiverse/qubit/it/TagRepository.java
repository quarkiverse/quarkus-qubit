package io.quarkiverse.qubit.it;

import io.quarkiverse.qubit.runtime.QubitRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Tag entities using the Repository pattern with fluent API query methods.
 */
@ApplicationScoped
public class TagRepository implements QubitRepository<Tag, Long> {
    // QubitRepository methods are auto-generated via @GenerateBridge at build time
}
