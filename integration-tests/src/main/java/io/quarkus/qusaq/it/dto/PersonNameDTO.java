package io.quarkus.qusaq.it.dto;

import java.util.Objects;

/**
 * Simple DTO for testing constructor-based projections with 2 fields.
 * <p>
 * Used to test: {@code Person.select(p -> new PersonNameDTO(p.firstName, p.lastName)).toList()}
 */
public class PersonNameDTO {

    private final String firstName;
    private final String lastName;

    public PersonNameDTO(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonNameDTO that)) return false;
        return Objects.equals(firstName, that.firstName) &&
               Objects.equals(lastName, that.lastName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName);
    }

    @Override
    public String toString() {
        return "PersonNameDTO{firstName='" + firstName + "', lastName='" + lastName + "'}";
    }
}
