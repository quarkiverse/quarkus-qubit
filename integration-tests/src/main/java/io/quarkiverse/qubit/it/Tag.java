package io.quarkiverse.qubit.it;

import io.quarkiverse.qubit.runtime.QubitEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Tag entity for testing many-to-many relationships.
 *
 * <p>This entity extends {@link QubitEntity} to gain lambda-based
 * query methods including relationship navigation.
 */
@Entity
public class Tag extends QubitEntity {

    public String name;
    public String color;

    @ManyToMany(mappedBy = "tags")
    public Set<Product> products = new HashSet<>();

    public Tag() {
    }

    public Tag(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Set<Product> getProducts() {
        return products;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tag tag)) return false;
        return Objects.equals(id, tag.id) &&
               Objects.equals(name, tag.name) &&
               Objects.equals(color, tag.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, color);
    }

    @Override
    public String toString() {
        return "Tag{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", color='" + color + '\'' +
               '}';
    }
}
