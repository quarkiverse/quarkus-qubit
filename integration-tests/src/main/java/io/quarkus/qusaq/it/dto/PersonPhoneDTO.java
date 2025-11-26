package io.quarkus.qusaq.it.dto;

import java.util.Objects;

/**
 * DTO for testing join projection queries.
 * <p>
 * Used to test bi-entity projections like:
 * {@code Person.join(p -> p.phones).select((p, ph) -> new PersonPhoneDTO(p.firstName, ph.number)).toList()}
 * <p>
 * Iteration 6.6: Join Projections implementation.
 */
public class PersonPhoneDTO {

    private final String personName;
    private final String phoneNumber;

    public PersonPhoneDTO(String personName, String phoneNumber) {
        this.personName = personName;
        this.phoneNumber = phoneNumber;
    }

    public String getPersonName() {
        return personName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonPhoneDTO that)) return false;
        return Objects.equals(personName, that.personName) &&
               Objects.equals(phoneNumber, that.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personName, phoneNumber);
    }

    @Override
    public String toString() {
        return "PersonPhoneDTO{personName='" + personName + "', phoneNumber='" + phoneNumber + "'}";
    }
}
