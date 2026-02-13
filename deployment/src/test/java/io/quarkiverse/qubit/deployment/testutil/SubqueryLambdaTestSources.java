package io.quarkiverse.qubit.deployment.testutil;

import static io.quarkiverse.qubit.Subqueries.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import io.quarkiverse.qubit.QuerySpec;

/**
 * Source class containing pre-compiled subquery lambda expressions for bytecode analysis testing.
 *
 * <p>
 * Each method in this class contains a lambda that uses Subqueries.* methods.
 * The bytecode analysis tests will load this compiled class and analyze the
 * synthetic lambda methods to verify correct subquery AST generation.
 *
 * <p>
 * <strong>Pattern</strong>: Each method returns a QuerySpec that will never be
 * executed - it's purely for bytecode generation. The method names match the
 * test names for easy mapping.
 *
 * <p>
 * Iteration 8: Subqueries with fluent builder pattern - bytecode analysis verification.
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

    /**
     * AVG subquery: p.salary > subquery(Person.class).avg(q -> q.salary)
     */
    public static QuerySpec<TestPerson, Boolean> avgSubquery() {
        return p -> p.salary > subquery(TestPerson.class).avg(q -> q.salary);
    }

    /**
     * MAX subquery: p.salary == subquery(Person.class).max(q -> q.salary)
     */
    public static QuerySpec<TestPerson, Boolean> maxSubquery() {
        return p -> p.salary == subquery(TestPerson.class).max(q -> q.salary);
    }

    /**
     * MIN subquery: p.salary >= subquery(Person.class).min(q -> q.salary)
     */
    public static QuerySpec<TestPerson, Boolean> minSubquery() {
        return p -> p.salary >= subquery(TestPerson.class).min(q -> q.salary);
    }

    /**
     * SUM subquery: p.budget > subquery(Department.class).sum(d -> d.budget)
     */
    public static QuerySpec<TestPerson, Boolean> sumSubquery() {
        return p -> p.budget > subquery(TestDepartment.class).sum(d -> d.budget);
    }

    /**
     * COUNT subquery: p.age > subquery(Person.class).count()
     */
    public static QuerySpec<TestPerson, Boolean> countSubquery() {
        return p -> p.age > subquery(TestPerson.class).count();
    }

    /**
     * EXISTS subquery: subquery(Phone.class).exists(ph -> ph.ownerId == p.id)
     */
    public static QuerySpec<TestPerson, Boolean> existsSubquery() {
        return p -> subquery(TestPhone.class).exists(ph -> ph.ownerId.equals(p.id));
    }

    /**
     * NOT EXISTS subquery: subquery(Phone.class).notExists(ph -> ph.ownerId == p.id)
     */
    public static QuerySpec<TestPerson, Boolean> notExistsSubquery() {
        return p -> subquery(TestPhone.class).notExists(ph -> ph.ownerId.equals(p.id));
    }

    /**
     * IN subquery: subquery(Department.class).in(p.departmentId, d -> d.id)
     */
    public static QuerySpec<TestPerson, Boolean> inSubquery() {
        return p -> subquery(TestDepartment.class).in(p.departmentId, d -> d.id);
    }

    /**
     * NOT IN subquery: subquery(Department.class).notIn(p.departmentId, d -> d.id)
     */
    public static QuerySpec<TestPerson, Boolean> notInSubquery() {
        return p -> subquery(TestDepartment.class).notIn(p.departmentId, d -> d.id);
    }

    /**
     * AVG subquery with predicate using .where() chaining
     */
    public static QuerySpec<TestPerson, Boolean> avgSubqueryWithPredicate() {
        return p -> p.salary > subquery(TestPerson.class).where(q -> q.active).avg(q -> q.salary);
    }

    /**
     * IN subquery with predicate using .where() chaining
     */
    public static QuerySpec<TestPerson, Boolean> inSubqueryWithPredicate() {
        return p -> subquery(TestDepartment.class).where(d -> d.budget > 1000000).in(p.departmentId, d -> d.id);
    }

    /**
     * Subquery combined with AND predicate
     */
    public static QuerySpec<TestPerson, Boolean> subqueryWithAndPredicate() {
        return p -> p.active && p.salary > subquery(TestPerson.class).avg(q -> q.salary);
    }

    /**
     * Subquery combined with OR predicate
     */
    public static QuerySpec<TestPerson, Boolean> subqueryWithOrPredicate() {
        return p -> p.age > 50 || subquery(TestPhone.class).exists(ph -> ph.ownerId.equals(p.id));
    }
}
