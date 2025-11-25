package io.quarkus.qusaq.it;

import io.quarkus.qusaq.runtime.QusaqEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.util.Objects;

/**
 * Phone entity for testing one-to-many relationships.
 *
 * <p>This entity extends {@link QusaqEntity} to gain lambda-based
 * query methods including relationship navigation.
 */
@Entity
public class Phone extends QusaqEntity {

    public String number;
    public String type; // "mobile", "home", "work"
    public boolean isPrimaryPhone;

    @ManyToOne
    public Person owner;

    public Phone() {
    }

    public Phone(String number, String type, boolean isPrimaryPhone, Person owner) {
        this.number = number;
        this.type = type;
        this.isPrimaryPhone = isPrimaryPhone;
        this.owner = owner;
    }

    public String getNumber() {
        return number;
    }

    public String getType() {
        return type;
    }

    public boolean isPrimaryPhone() {
        return isPrimaryPhone;
    }

    public Person getOwner() {
        return owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Phone phone)) return false;
        return isPrimaryPhone == phone.isPrimaryPhone &&
               Objects.equals(id, phone.id) &&
               Objects.equals(number, phone.number) &&
               Objects.equals(type, phone.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, number, type, isPrimaryPhone);
    }

    @Override
    public String toString() {
        return "Phone{" +
               "id=" + id +
               ", number='" + number + '\'' +
               ", type='" + type + '\'' +
               ", isPrimaryPhone=" + isPrimaryPhone +
               '}';
    }
}
