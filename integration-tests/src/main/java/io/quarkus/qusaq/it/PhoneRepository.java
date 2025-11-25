package io.quarkus.qusaq.it;

import io.quarkus.qusaq.runtime.QusaqRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Phone entities using the Repository pattern with fluent API query methods.
 */
@ApplicationScoped
public class PhoneRepository implements QusaqRepository<Phone, Long> {
    // QusaqRepository methods are auto-generated via @GenerateBridge at build time
}
