package io.quarkus.qusaq.deployment.testutil;

import io.quarkus.qusaq.runtime.QuerySpec;
import io.quarkus.qusaq.runtime.Subqueries;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Source class containing pre-compiled subquery lambda expressions for bytecode analysis testing.
 *
 * <p>Each method in this class contains a lambda that uses Subqueries.* methods.
 * The bytecode analysis tests will load this compiled class and analyze the
 * synthetic lambda methods to verify correct subquery AST generation.
 *
 * <p><strong>Pattern</strong>: Each method returns a QuerySpec that will never be
 * executed - it's purely for bytecode generation. The method names match the
 * test names for easy mapping.
 *
 * <p>Iteration 8: Subqueries - bytecode analysis verification.
 */
public class SubqueryLambdaTestSources {

    // Test entities for subquery lambda analysis
    @Entity
    public static class TestPerson {
        @Id
        public Long id;
        public String firstName;
        public String lastName;
        public int age;
        public double salary;
        public long budget;
        public String departmentId;
        public boolean active;
    }

    @Entity
    public static class TestDepartment {
        @Id
        public String id;
        public String name;
        public long budget;
    }

    @Entity
    public static class TestPhone {
        @Id
        public Long id;
        public String number;
        public String type;
        public Long ownerId;
    }

    // ==================== SCALAR AVG SUBQUERIES ====================

    /**
     * AVG subquery: p.salary > Subqueries.avg(Person.class, q -> q.salary)
     */
    public static QuerySpec<TestPerson, Boolean> avgSubquery() {
        return p -> p.salary > Subqueries.avg(TestPerson.class, q -> q.salary);
    }

    // ==================== SCALAR MAX SUBQUERIES ====================

    /**
     * MAX subquery: p.salary == Subqueries.max(Person.class, q -> q.salary)
     */
    public static QuerySpec<TestPerson, Boolean> maxSubquery() {
        return p -> p.salary == Subqueries.max(TestPerson.class, q -> q.salary);
    }

    // ==================== SCALAR MIN SUBQUERIES ====================

    /**
     * MIN subquery: p.salary >= Subqueries.min(Person.class, q -> q.salary)
     */
    public static QuerySpec<TestPerson, Boolean> minSubquery() {
        return p -> p.salary >= Subqueries.min(TestPerson.class, q -> q.salary);
    }

    // ==================== SCALAR SUM SUBQUERIES ====================

    /**
     * SUM subquery: p.budget > Subqueries.sum(Department.class, d -> d.budget)
     */
    public static QuerySpec<TestPerson, Boolean> sumSubquery() {
        return p -> p.budget > Subqueries.sum(TestDepartment.class, d -> d.budget);
    }

    // ==================== COUNT SUBQUERIES ====================

    /**
     * COUNT subquery: p.age > Subqueries.count(Person.class)
     */
    public static QuerySpec<TestPerson, Boolean> countSubquery() {
        return p -> p.age > Subqueries.count(TestPerson.class);
    }

    // ==================== EXISTS SUBQUERIES ====================

    /**
     * EXISTS subquery: Subqueries.exists(Phone.class, ph -> ph.ownerId == p.id)
     */
    public static QuerySpec<TestPerson, Boolean> existsSubquery() {
        return p -> Subqueries.exists(TestPhone.class, ph -> ph.ownerId.equals(p.id));
    }

    /**
     * NOT EXISTS subquery: Subqueries.notExists(Phone.class, ph -> ph.ownerId == p.id)
     */
    public static QuerySpec<TestPerson, Boolean> notExistsSubquery() {
        return p -> Subqueries.notExists(TestPhone.class, ph -> ph.ownerId.equals(p.id));
    }

    // ==================== IN SUBQUERIES ====================

    /**
     * IN subquery: Subqueries.in(p.departmentId, Department.class, d -> d.id)
     */
    public static QuerySpec<TestPerson, Boolean> inSubquery() {
        return p -> Subqueries.in(p.departmentId, TestDepartment.class, d -> d.id);
    }

    /**
     * NOT IN subquery: Subqueries.notIn(p.departmentId, Department.class, d -> d.id)
     */
    public static QuerySpec<TestPerson, Boolean> notInSubquery() {
        return p -> Subqueries.notIn(p.departmentId, TestDepartment.class, d -> d.id);
    }

    // ==================== ADVANCED SUBQUERIES ====================

    /**
     * AVG subquery with predicate
     */
    public static QuerySpec<TestPerson, Boolean> avgSubqueryWithPredicate() {
        return p -> p.salary > Subqueries.avg(TestPerson.class, q -> q.salary, q -> q.active);
    }

    /**
     * IN subquery with predicate
     */
    public static QuerySpec<TestPerson, Boolean> inSubqueryWithPredicate() {
        return p -> Subqueries.in(p.departmentId, TestDepartment.class, d -> d.id, d -> d.budget > 1000000);
    }

    /**
     * Subquery combined with AND predicate
     */
    public static QuerySpec<TestPerson, Boolean> subqueryWithAndPredicate() {
        return p -> p.active && p.salary > Subqueries.avg(TestPerson.class, q -> q.salary);
    }

    /**
     * Subquery combined with OR predicate
     */
    public static QuerySpec<TestPerson, Boolean> subqueryWithOrPredicate() {
        return p -> p.age > 50 || Subqueries.exists(TestPhone.class, ph -> ph.ownerId.equals(p.id));
    }
}
