package io.quarkiverse.qubit.deployment.testutil;

import jakarta.persistence.Entity;

import io.quarkiverse.qubit.BiQuerySpec;
import io.quarkiverse.qubit.QubitEntity;

/**
 * Source class containing pre-compiled bi-entity lambda expressions for bytecode analysis testing.
 *
 * <p>
 * Each method in this class contains a BiQuerySpec lambda that will be compiled
 * by javac into invokedynamic bytecode. The bytecode analysis tests will load
 * this compiled class and analyze the synthetic lambda methods.
 *
 * <p>
 * Bi-entity lambdas are used in join queries where predicates reference both
 * the source entity (first parameter) and the joined entity (second parameter).
 *
 * <p>
 * <strong>Pattern</strong>: Each method returns a BiQuerySpec that will never be
 * executed - it's purely for bytecode generation. The method names match the
 * test names for easy mapping.
 */
public class BiEntityLambdaTestSources {

    // Test entities for bi-entity lambda analysis
    @Entity
    public static class TestPerson extends QubitEntity {
        public String firstName;
        public String lastName;
        public int age;
        public boolean active;
        public double salary;
    }

    @Entity
    public static class TestPhone extends QubitEntity {
        public String number;
        public String type;
        public boolean isPrimary;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityFieldEquals() {
        return (p, ph) -> ph.type.equals("mobile");
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityBooleanField() {
        return (p, ph) -> ph.isPrimary;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityNegatedBooleanField() {
        return (p, ph) -> !ph.isPrimary;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> sourceEntityFieldEquals() {
        return (p, ph) -> p.firstName.equals("John");
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> sourceEntityIntegerComparison() {
        return (p, ph) -> p.age > 30;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> sourceEntityBooleanField() {
        return (p, ph) -> p.active;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> bothEntitiesSimpleAnd() {
        return (p, ph) -> p.active && ph.isPrimary;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> bothEntitiesComplex() {
        return (p, ph) -> p.age >= 30 && ph.type.equals("work");
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> bothEntitiesWithOr() {
        return (p, ph) -> p.age > 50 || ph.type.equals("mobile");
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityStartsWith() {
        return (p, ph) -> ph.number.startsWith("555");
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityContains() {
        return (p, ph) -> ph.number.contains("01");
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityEndsWith() {
        return (p, ph) -> ph.number.endsWith("00");
    }

    public static BiQuerySpec<TestPerson, TestPhone, String> projectJoinedEntityField() {
        return (p, ph) -> ph.number;
    }

    public static BiQuerySpec<TestPerson, TestPhone, String> projectSourceEntityField() {
        return (p, ph) -> p.firstName;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Integer> projectSourceEntityIntField() {
        return (p, ph) -> p.age;
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedEntityWithCapturedVariable() {
        String phoneType = "mobile";
        return (p, ph) -> ph.type.equals(phoneType);
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> bothEntitiesWithCapturedVariable() {
        int minAge = 25;
        String targetType = "work";
        return (p, ph) -> p.age > minAge && ph.type.equals(targetType);
    }

    public static BiQuerySpec<TestPerson, TestPhone, Boolean> joinedMathAbs() {
        return (p, ph) -> Math.abs(p.age) > 5;
    }
}
