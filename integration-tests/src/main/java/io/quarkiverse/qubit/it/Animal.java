package io.quarkiverse.qubit.it;

import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import io.quarkiverse.qubit.QubitEntity;

/**
 * Base entity for testing inheritance queries with TREAT support.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class Animal extends QubitEntity {

    public String name;
    public int weight;
    public boolean vaccinated;

    // Getters for assertions in tests
    public String getName() {
        return name;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isVaccinated() {
        return vaccinated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name='" + name + "', weight=" + weight + "}";
    }
}
