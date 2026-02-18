package io.quarkiverse.qubit.it;

import jakarta.persistence.Entity;

/**
 * Cat entity for testing TREAT downcasting to a different subclass.
 */
@Entity
public class Cat extends Animal {

    public boolean indoor;
    public String color;

    public boolean isIndoor() {
        return indoor;
    }

    public String getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "Cat{name='" + name + "', color='" + color + "', indoor=" + indoor + "}";
    }
}
