package io.quarkus.qusaq.it;

import io.quarkus.qusaq.runtime.QusaqRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Product entities using the Repository pattern with fluent API query methods.
 * <p>
 * This repository provides the same fluent API as QusaqEntity but through dependency injection
 * and instance methods rather than static methods. It demonstrates the Repository pattern
 * variant of Qusaq's lambda-based query capabilities.
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * @Inject
 * ProductRepository productRepository;
 *
 * // Usage:
 * List<Product> available = productRepository.where(p -> p.available).toList();
 * Double avgPrice = productRepository.avg(p -> p.price).getSingleResult();
 * }</pre>
 * <p>
 * All fluent API methods (where, select, sortedBy, min, max, avg, sumInteger, sumLong, sumDouble)
 * are generated at build time via the @GenerateBridge annotation and QusaqRepositoryEnhancer.
 */
@ApplicationScoped
public class ProductRepository implements QusaqRepository<Product, Long> {
    // QusaqRepository methods are auto-generated via @GenerateBridge at build time
}
