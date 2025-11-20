package io.quarkus.qusaq.it.dto;

import java.util.Objects;

/**
 * DTO for testing constructor-based projections with 3 string fields.
 * <p>
 * Used to test: {@code Person.select(p -> new PersonBasicDTO(p.firstName, p.lastName, p.email)).toList()}
 */
public class PersonBasicDTO {

    private final String firstName;
    private final String lastName;
    private final String email;

    public PersonBasicDTO(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonBasicDTO that)) return false;
        return Objects.equals(firstName, that.firstName) &&
               Objects.equals(lastName, that.lastName) &&
               Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, email);
    }

    @Override
    public String toString() {
        return "PersonBasicDTO{firstName='" + firstName + "', lastName='" + lastName + "', email='" + email + "'}";
    }
}
