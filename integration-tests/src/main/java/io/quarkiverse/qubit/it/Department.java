package io.quarkiverse.qubit.it;

import io.quarkiverse.qubit.runtime.QubitEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Department entity for testing multi-level relationship navigation.
 *
 * <p>This entity extends {@link QubitEntity} to gain lambda-based
 * query methods. Enables three-level navigation tests like:
 * {@code phone.owner.department.name}
 */
@Entity
public class Department extends QubitEntity {

    public String name;
    public String code;
    public int budget;

    @OneToMany(mappedBy = "department")
    public List<Person> employees = new ArrayList<>();

    public Department() {
    }

    public Department(String name, String code, int budget) {
        this.name = name;
        this.code = code;
        this.budget = budget;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public int getBudget() {
        return budget;
    }

    public List<Person> getEmployees() {
        return employees;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Department dept)) return false;
        return budget == dept.budget &&
               Objects.equals(id, dept.id) &&
               Objects.equals(name, dept.name) &&
               Objects.equals(code, dept.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, code, budget);
    }

    @Override
    public String toString() {
        return "Department{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", code='" + code + '\'' +
               ", budget=" + budget +
               '}';
    }
}
