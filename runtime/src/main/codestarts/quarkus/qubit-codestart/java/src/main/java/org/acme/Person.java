package org.acme;

import io.quarkiverse.qubit.QubitEntity;
import jakarta.persistence.Entity;

/**
 * Sample entity demonstrating Qubit type-safe queries.
 *
 * <p>Qubit extends Panache entities with lambda-based querying:
 * <pre>{@code
 * // Find adults
 * Person.where(p -> p.age >= 18).toList();
 *
 * // Find by name pattern
 * Person.where(p -> p.name.startsWith("John")).toList();
 *
 * // Complex queries with sorting
 * Person.where(p -> p.active && p.age > 21)
 *       .sortedBy(p -> p.name)
 *       .toList();
 * }</pre>
 */
@Entity
public class Person extends QubitEntity {

    public String name;
    public int age;
    public boolean active;

    public Person() {
    }

    public Person(String name, int age, boolean active) {
        this.name = name;
        this.age = age;
        this.active = active;
    }
}
