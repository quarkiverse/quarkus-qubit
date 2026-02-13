package io.quarkiverse.qubit.deployment.testutil;

import java.util.List;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import io.quarkiverse.qubit.QubitEntity;

/**
 * Source class containing fluent API calls for InvokeDynamicScanner testing.
 *
 * <p>
 * Each method in this class contains a fluent API call pattern that will be
 * compiled by javac into invokedynamic bytecode followed by terminal method calls.
 * The InvokeDynamicScanner tests will load this compiled class and verify that
 * call sites are correctly detected.
 *
 * <p>
 * <strong>Pattern</strong>: Each method uses the QubitEntity fluent API with
 * terminal operations like toList(), count(), exists(), etc.
 */
public class FluentApiTestSources {

    // Test entity for simple queries
    @Entity
    public static class TestEmployee extends QubitEntity {
        public int age;
        public String name;
        public double salary;
        public boolean active;
        public String department;
    }

    // Test entity with relationships for join queries
    @Entity
    public static class TestOrder extends QubitEntity {
        public double total;
        public String status;

        @ManyToOne
        public TestCustomer customer;

        @OneToMany
        public Set<TestOrderItem> items;
    }

    @Entity
    public static class TestCustomer extends QubitEntity {
        public String name;
        public String email;

        @OneToMany(mappedBy = "customer")
        public List<TestOrder> orders;
    }

    @Entity
    public static class TestOrderItem extends QubitEntity {
        public String productName;
        public int quantity;
        public double price;
    }

    /**
     * Simple where with toList terminal.
     * Pattern: where(lambda).toList()
     */
    public List<TestEmployee> whereToList() {
        return TestEmployee.where((TestEmployee e) -> e.age > 30).toList();
    }

    /**
     * Where with count terminal.
     * Pattern: where(lambda).count()
     */
    public long whereCount() {
        return TestEmployee.where((TestEmployee e) -> e.active).count();
    }

    /**
     * Where with exists terminal.
     * Pattern: where(lambda).exists()
     */
    public boolean whereExists() {
        return TestEmployee.where((TestEmployee e) -> e.salary > 100000).exists();
    }

    /**
     * Chained where clauses.
     * Pattern: where(lambda1).where(lambda2).toList()
     */
    public List<TestEmployee> chainedWhere() {
        return TestEmployee.where((TestEmployee e) -> e.age > 25)
                .where((TestEmployee e) -> e.active)
                .toList();
    }

    /**
     * Simple select projection.
     * Pattern: select(lambda).toList()
     */
    public List<String> selectToList() {
        return TestEmployee.select((TestEmployee e) -> e.name).toList();
    }

    /**
     * Where followed by select.
     * Pattern: where(lambda).select(lambda).toList()
     */
    public List<String> whereSelect() {
        return TestEmployee.where((TestEmployee e) -> e.active)
                .select((TestEmployee e) -> e.name)
                .toList();
    }

    /**
     * Sorted by ascending.
     * Pattern: sortedBy(lambda).toList()
     */
    public List<TestEmployee> sortedByAscending() {
        return TestEmployee.sortedBy((TestEmployee e) -> e.age).toList();
    }

    /**
     * Sorted by descending.
     * Pattern: sortedDescendingBy(lambda).toList()
     */
    public List<TestEmployee> sortedByDescending() {
        return TestEmployee.sortedDescendingBy((TestEmployee e) -> e.salary).toList();
    }

    /**
     * Where with sorting.
     * Pattern: where(lambda).sortedBy(lambda).toList()
     */
    public List<TestEmployee> whereWithSorting() {
        return TestEmployee.where((TestEmployee e) -> e.active)
                .sortedBy((TestEmployee e) -> e.name)
                .toList();
    }

    /**
     * Min aggregation.
     * Pattern: min(lambda).getSingleResult()
     */
    public Integer minAge() {
        return TestEmployee.min((TestEmployee e) -> e.age).getSingleResult();
    }

    /**
     * Max aggregation.
     * Pattern: max(lambda).getSingleResult()
     */
    public Double maxSalary() {
        return TestEmployee.max((TestEmployee e) -> e.salary).getSingleResult();
    }

    /**
     * Avg aggregation.
     * Pattern: avg(lambda).getSingleResult()
     */
    public Double avgSalary() {
        return TestEmployee.avg((TestEmployee e) -> e.salary).getSingleResult();
    }

    /**
     * Sum aggregation (integer to long).
     * Pattern: sumInteger(lambda).getSingleResult()
     */
    public Long sumAges() {
        return TestEmployee.sumInteger((TestEmployee e) -> e.age).getSingleResult();
    }

    /**
     * Where with min.
     * Pattern: where(lambda).min(lambda).getSingleResult()
     */
    public Double minSalaryForActive() {
        return TestEmployee.where((TestEmployee e) -> e.active)
                .min((TestEmployee e) -> e.salary)
                .getSingleResult();
    }

    /**
     * Skip and limit query (pagination).
     * Pattern: where(lambda).skip(10).limit(10).toList()
     */
    public List<TestEmployee> skipAndLimit() {
        return TestEmployee.where((TestEmployee e) -> e.active)
                .skip(10)
                .limit(10)
                .toList();
    }

    /**
     * Limit query (first N).
     * Pattern: where(lambda).limit(5).toList()
     */
    public List<TestEmployee> limitQuery() {
        return TestEmployee.where((TestEmployee e) -> e.salary > 50000)
                .limit(5)
                .toList();
    }

    /**
     * Get single result.
     * Pattern: where(lambda).getSingleResult()
     */
    public TestEmployee getSingleResult() {
        return TestEmployee.where((TestEmployee e) -> e.name.equals("John"))
                .getSingleResult();
    }

    /**
     * Find first optional.
     * Pattern: where(lambda).findFirst()
     */
    public java.util.Optional<TestEmployee> findFirst() {
        return TestEmployee.where((TestEmployee e) -> e.age > 50)
                .findFirst();
    }

    /**
     * Complex query with multiple clauses.
     * Pattern: where().where().select().sortedBy().limit().toList()
     */
    public List<String> complexQuery() {
        return TestEmployee.where((TestEmployee e) -> e.active)
                .where((TestEmployee e) -> e.age > 25)
                .select((TestEmployee e) -> e.name)
                .sortedBy((String name) -> name)
                .limit(10)
                .toList();
    }

    /**
     * Query with captured variable.
     * Pattern: where(lambda with captured var).toList()
     */
    public List<TestEmployee> withCapturedVariable(int minAge) {
        return TestEmployee.where((TestEmployee e) -> e.age > minAge).toList();
    }

    /**
     * Query with multiple captured variables.
     */
    public List<TestEmployee> withMultipleCapturedVariables(int minAge, String dept) {
        return TestEmployee.where((TestEmployee e) -> e.age > minAge && e.department.equals(dept))
                .toList();
    }
}
