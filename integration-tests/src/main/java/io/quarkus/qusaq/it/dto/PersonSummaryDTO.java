package io.quarkus.qusaq.it.dto;

import java.util.Objects;

/**
 * DTO for testing constructor-based projections with multiple field types.
 * <p>
 * Used to test: {@code Person.select(p -> new PersonSummaryDTO(p.firstName, p.age, p.salary)).toList()}
 */
public class PersonSummaryDTO {

    private final String firstName;
    private final int age;
    private final Double salary;

    public PersonSummaryDTO(String firstName, int age, Double salary) {
        this.firstName = firstName;
        this.age = age;
        this.salary = salary;
    }

    public String getFirstName() {
        return firstName;
    }

    public int getAge() {
        return age;
    }

    public Double getSalary() {
        return salary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonSummaryDTO that)) return false;
        return age == that.age &&
               Objects.equals(firstName, that.firstName) &&
               Objects.equals(salary, that.salary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, age, salary);
    }

    @Override
    public String toString() {
        return "PersonSummaryDTO{firstName='" + firstName + "', age=" + age + ", salary=" + salary + '}';
    }
}
