package io.quarkiverse.qubit.it;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkiverse.qubit.QubitEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Test entity for integration tests demonstrating Qubit lambda-based queries.
 *
 * <p>
 * This entity extends {@link QubitEntity} to gain lambda-based
 * query methods. The abstract methods are implemented at build time.
 */
@Entity
public class Person extends QubitEntity {

    public String firstName;
    public String lastName;
    public String email;
    public int age;
    public LocalDate birthDate;
    public boolean active;
    public Double salary;
    public Long employeeId; // BIGINT type for testing Long operations
    public Float height; // REAL/FLOAT type for testing Float operations
    public LocalDateTime createdAt; // TIMESTAMP type for testing LocalDateTime operations
    public LocalTime startTime; // TIME type for testing LocalTime operations

    @ManyToOne
    public Department department; // For testing multi-level navigation: phone.owner.department.name

    @JsonIgnore // Prevent circular reference in JSON serialization
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Phone> phones = new ArrayList<>();

    public Person() {
    }

    public Person(String firstName, String lastName, String email, int age,
            LocalDate birthDate, boolean active, Double salary, Long employeeId, Float height,
            LocalDateTime createdAt, LocalTime startTime) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.age = age;
        this.birthDate = birthDate;
        this.active = active;
        this.salary = salary;
        this.employeeId = employeeId;
        this.height = height;
        this.createdAt = createdAt;
        this.startTime = startTime;
    }

    // Getters for method reference tests
    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public int getAge() {
        return age;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public boolean isActive() {
        return active;
    }

    public Double getSalary() {
        return salary;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Float getHeight() {
        return height;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public Department getDepartment() {
        return department;
    }

    public List<Phone> getPhones() {
        return phones;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Person person))
            return false;
        return age == person.age &&
                active == person.active &&
                Objects.equals(id, person.id) &&
                Objects.equals(firstName, person.firstName) &&
                Objects.equals(lastName, person.lastName) &&
                Objects.equals(email, person.email) &&
                Objects.equals(birthDate, person.birthDate) &&
                Objects.equals(salary, person.salary) &&
                Objects.equals(employeeId, person.employeeId) &&
                Objects.equals(height, person.height) &&
                Objects.equals(createdAt, person.createdAt) &&
                Objects.equals(startTime, person.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt,
                startTime);
    }

    @Override
    public String toString() {
        return "Person{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                ", birthDate=" + birthDate +
                ", active=" + active +
                ", salary=" + salary +
                ", employeeId=" + employeeId +
                ", height=" + height +
                ", createdAt=" + createdAt +
                ", startTime=" + startTime +
                '}';
    }
}
