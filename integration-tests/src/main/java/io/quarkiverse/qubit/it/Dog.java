package io.quarkiverse.qubit.it;

import jakarta.persistence.Entity;

/**
 * Dog entity for testing TREAT downcasting to subclass fields.
 */
@Entity
public class Dog extends Animal {

    public String breed;
    public boolean trained;

    public String getBreed() {
        return breed;
    }

    public boolean isTrained() {
        return trained;
    }

    @Override
    public String toString() {
        return "Dog{name='" + name + "', breed='" + breed + "', weight=" + weight + "}";
    }
}
