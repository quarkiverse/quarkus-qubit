package io.quarkiverse.qubit.deployment.testutil;

import io.quarkiverse.qubit.QubitEntity;
import io.quarkiverse.qubit.QuerySpec;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Source class containing pre-compiled lambda expressions for bytecode analysis testing.
 *
 * <p>Each method in this class contains a lambda expression that will be compiled
 * by javac into invokedynamic bytecode. The bytecode analysis tests will load
 * this compiled class and analyze the synthetic lambda methods.
 *
 * <p><strong>Pattern</strong>: Each method returns a QuerySpec that will never be
 * executed - it's purely for bytecode generation. The method names match the
 * test names for easy mapping.
 */
public class LambdaTestSources {

    // Test entities - using primitives for comparisons
    @Entity
    public static class TestPerson extends QubitEntity {
        public int age;
        public long employeeId;
        public float height;
        public double salary;
        public String firstName;
        public String lastName;
        public String email;
        public boolean active;
        public LocalDate birthDate;
        public LocalDateTime createdAt;
        public LocalTime startTime;
    }

    // Test entity with wrapper types for null checks
    @Entity
    public static class TestPersonNullable extends QubitEntity {
        public Integer age;
        public Long employeeId;
        public Float height;
        public Double salary;
        public String email;
        public boolean active;
        public LocalDate birthDate;
        public LocalDateTime createdAt;
        public LocalTime startTime;
    }

    @Entity
    public static class TestProduct extends QubitEntity {
        public BigDecimal price;
        public String name;
        public String description;
        public boolean available;
        public int stockQuantity;
        public double rating;
    }

    // ==================== INTEGER COMPARISONS ====================

    public static QuerySpec<TestPerson, Boolean> integerGreaterThan() {
        return p -> p.age > 30;
    }

    public static QuerySpec<TestPerson, Boolean> integerGreaterThanOrEqual() {
        return p -> p.age >= 30;
    }

    public static QuerySpec<TestPerson, Boolean> integerLessThan() {
        return p -> p.age < 30;
    }

    public static QuerySpec<TestPerson, Boolean> integerLessThanOrEqual() {
        return p -> p.age <= 30;
    }

    public static QuerySpec<TestPerson, Boolean> integerNotEquals() {
        return p -> p.age != 30;
    }

    // ==================== LONG COMPARISONS ====================

    public static QuerySpec<TestPerson, Boolean> longGreaterThan() {
        return p -> p.employeeId > 1000003L;
    }

    public static QuerySpec<TestPerson, Boolean> longGreaterThanOrEqual() {
        return p -> p.employeeId >= 1000002L;
    }

    public static QuerySpec<TestPerson, Boolean> longLessThan() {
        return p -> p.employeeId < 1000003L;
    }

    public static QuerySpec<TestPerson, Boolean> longLessThanOrEqual() {
        return p -> p.employeeId <= 1000003L;
    }

    public static QuerySpec<TestPerson, Boolean> longNotEquals() {
        return p -> p.employeeId != 1000001L;
    }

    // ==================== FLOAT COMPARISONS ====================

    public static QuerySpec<TestPerson, Boolean> floatGreaterThan() {
        return p -> p.height > 1.70f;
    }

    public static QuerySpec<TestPerson, Boolean> floatGreaterThanOrEqual() {
        return p -> p.height >= 1.70f;
    }

    public static QuerySpec<TestPerson, Boolean> floatLessThan() {
        return p -> p.height < 1.70f;
    }

    public static QuerySpec<TestPerson, Boolean> floatLessThanOrEqual() {
        return p -> p.height <= 1.75f;
    }

    public static QuerySpec<TestPerson, Boolean> floatNotEquals() {
        return p -> p.height != 1.75f;
    }

    // ==================== DOUBLE COMPARISONS ====================

    public static QuerySpec<TestPerson, Boolean> doubleGreaterThan() {
        return p -> p.salary > 70000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleGreaterThanOrEqual() {
        return p -> p.salary >= 75000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleLessThan() {
        return p -> p.salary < 80000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleLessThanOrEqual() {
        return p -> p.salary <= 75000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleNotEquals() {
        return p -> p.salary != 75000.0;
    }

    // ==================== BIGDECIMAL COMPARISONS ====================

    public static QuerySpec<TestProduct, Boolean> bigDecimalGreaterThan() {
        return p -> p.price.compareTo(new BigDecimal("500")) > 0;
    }

    public static QuerySpec<TestProduct, Boolean> bigDecimalGreaterThanOrEqual() {
        return p -> p.price.compareTo(new BigDecimal("500")) >= 0;
    }

    public static QuerySpec<TestProduct, Boolean> bigDecimalLessThan() {
        return p -> p.price.compareTo(new BigDecimal("1000")) < 0;
    }

    public static QuerySpec<TestProduct, Boolean> bigDecimalLessThanOrEqual() {
        return p -> p.price.compareTo(new BigDecimal("300")) <= 0;
    }

    public static QuerySpec<TestProduct, Boolean> bigDecimalNotEquals() {
        return p -> p.price.compareTo(new BigDecimal("899.99")) != 0;
    }

    // ==================== TEMPORAL COMPARISONS ====================

    public static QuerySpec<TestPerson, Boolean> localDateAfter() {
        return p -> p.birthDate.isAfter(LocalDate.of(1990, 1, 1));
    }

    public static QuerySpec<TestPerson, Boolean> localDateBefore() {
        return p -> p.birthDate.isBefore(LocalDate.of(1990, 1, 1));
    }

    public static QuerySpec<TestPerson, Boolean> localDateTimeAfter() {
        return p -> p.createdAt.isAfter(LocalDateTime.of(2024, 3, 1, 0, 0));
    }

    public static QuerySpec<TestPerson, Boolean> localDateTimeBefore() {
        return p -> p.createdAt.isBefore(LocalDateTime.of(2024, 3, 1, 0, 0));
    }

    public static QuerySpec<TestPerson, Boolean> localTimeAfter() {
        return p -> p.startTime.isAfter(LocalTime.of(9, 0));
    }

    public static QuerySpec<TestPerson, Boolean> localTimeBefore() {
        return p -> p.startTime.isBefore(LocalTime.of(9, 0));
    }

    // ==================== RANGE QUERIES ====================

    public static QuerySpec<TestPerson, Boolean> integerRangeQuery() {
        return p -> p.age >= 25 && p.age <= 35;
    }

    public static QuerySpec<TestPerson, Boolean> longRangeQuery() {
        return p -> p.employeeId >= 1000002L && p.employeeId <= 1000004L;
    }

    public static QuerySpec<TestPerson, Boolean> floatRangeQuery() {
        return p -> p.height >= 1.65f && p.height <= 1.80f;
    }

    public static QuerySpec<TestProduct, Boolean> bigDecimalRangeQuery() {
        return p -> p.price.compareTo(new BigDecimal("800.00")) >= 0 &&
                    p.price.compareTo(new BigDecimal("1500.00")) <= 0;
    }

    // ==================== EQUALITY OPERATIONS ====================

    public static QuerySpec<TestPerson, Boolean> stringEquality() {
        return p -> p.firstName.equals("John");
    }

    public static QuerySpec<TestPerson, Boolean> integerEquality() {
        return p -> p.age == 30;
    }

    public static QuerySpec<TestPerson, Boolean> booleanEqualityTrue() {
        return p -> p.active == true;
    }

    public static QuerySpec<TestPerson, Boolean> booleanEqualityFalse() {
        return p -> p.active == false;
    }

    public static QuerySpec<TestPerson, Boolean> booleanImplicit() {
        return p -> p.active;
    }

    // Additional boolean edge cases for mutation testing
    public static QuerySpec<TestPerson, Boolean> booleanNotEqualityTrue() {
        return p -> p.active != true;
    }

    public static QuerySpec<TestPerson, Boolean> booleanNotEqualityFalse() {
        return p -> p.active != false;
    }

    public static QuerySpec<TestPerson, Boolean> longEquality() {
        return p -> p.employeeId == 1000001L;
    }

    public static QuerySpec<TestPerson, Boolean> floatEquality() {
        return p -> p.height == 1.75f;
    }

    public static QuerySpec<TestPerson, Boolean> doubleEquality() {
        return p -> p.salary == 75000.0;
    }

    public static QuerySpec<TestPerson, Boolean> localDateEquality() {
        return p -> p.birthDate.isEqual(LocalDate.of(1993, 5, 15));
    }

    public static QuerySpec<TestPerson, Boolean> localDateTimeEquality() {
        return p -> p.createdAt.isEqual(LocalDateTime.of(2024, 1, 15, 9, 30));
    }

    public static QuerySpec<TestPerson, Boolean> localTimeEquality() {
        return p -> p.startTime.equals(LocalTime.of(9, 0));
    }

    public static QuerySpec<TestProduct, Boolean> bigDecimalEquality() {
        return p -> p.price.compareTo(new BigDecimal("899.99")) == 0;
    }

    // ==================== NULL CHECKS ====================

    public static QuerySpec<TestPerson, Boolean> stringNullCheck() {
        return p -> p.email == null;
    }

    public static QuerySpec<TestPerson, Boolean> stringNotNullCheck() {
        return p -> p.email != null;
    }

    public static QuerySpec<TestPersonNullable, Boolean> doubleNullCheck() {
        return p -> p.salary == null;
    }

    public static QuerySpec<TestPersonNullable, Boolean> longNullCheck() {
        return p -> p.employeeId == null;
    }

    public static QuerySpec<TestPersonNullable, Boolean> floatNullCheck() {
        return p -> p.height == null;
    }

    public static QuerySpec<TestPerson, Boolean> localDateNullCheck() {
        return p -> p.birthDate == null;
    }

    public static QuerySpec<TestPerson, Boolean> localDateTimeNullCheck() {
        return p -> p.createdAt == null;
    }

    public static QuerySpec<TestPerson, Boolean> localTimeNullCheck() {
        return p -> p.startTime == null;
    }

    // ==================== CHAINED NULL CHECKS ====================

    public static QuerySpec<TestPerson, Boolean> nullCheckWithAnd() {
        return p -> p.email != null && p.firstName != null;
    }

    public static QuerySpec<TestPerson, Boolean> nullCheckWithCondition() {
        return p -> p.email != null && p.age > 30;
    }

    public static QuerySpec<TestPerson, Boolean> nullCheckWithOr() {
        return p -> p.email == null || p.firstName == null;
    }

    // ==================== LOGICAL OPERATIONS - AND ====================

    public static QuerySpec<TestPerson, Boolean> twoConditionAnd() {
        return p -> p.age > 25 && p.active;
    }

    public static QuerySpec<TestPersonNullable, Boolean> threeConditionAnd() {
        return p -> p.age >= 35 && p.active && p.salary != null;
    }

    public static QuerySpec<TestPersonNullable, Boolean> fourConditionAnd() {
        return p -> p.age >= 35 && p.active && p.salary != null && p.salary > 85000.0;
    }

    public static QuerySpec<TestPersonNullable, Boolean> fiveConditionAnd() {
        return p -> p.age >= 30 && p.active && p.salary != null &&
                    p.salary > 70000.0 && p.email.contains("@");
    }

    public static QuerySpec<TestPersonNullable, Boolean> longAndChain() {
        return p -> p.age >= 25 && p.age <= 45 && p.active && p.salary != null &&
                    p.salary > 60000.0 && p.email.contains("@") &&
                    p.height != null && p.height > 1.6f;
    }

    // ==================== LOGICAL OPERATIONS - OR ====================

    public static QuerySpec<TestPerson, Boolean> simpleOr() {
        return p -> p.age < 26 || p.age > 40;
    }

    public static QuerySpec<TestPerson, Boolean> orWithStringOperations() {
        return p -> p.firstName.startsWith("A") || p.age > 40;
    }

    public static QuerySpec<TestPerson, Boolean> threeWayOr() {
        return p -> p.age < 26 || p.age > 44 || p.firstName.equals("John");
    }

    public static QuerySpec<TestPerson, Boolean> fourWayOr() {
        return p -> p.age < 27 || p.age > 43 || p.firstName.equals("Alice") ||
                    p.email.contains("@example.com");
    }

    // ==================== LOGICAL OPERATIONS - NOT ====================

    public static QuerySpec<TestPerson, Boolean> simpleNot() {
        return p -> !p.active;
    }

    public static QuerySpec<TestPerson, Boolean> notWithAnd() {
        return p -> !p.active && p.age > 40;
    }

    public static QuerySpec<TestPerson, Boolean> notWithComplexOrAnd() {
        return p -> !(p.age < 28 || p.age > 42) && p.active;
    }

    public static QuerySpec<TestPerson, Boolean> stringNotEquals() {
        return p -> !p.firstName.equals("John");
    }

    public static QuerySpec<TestPerson, Boolean> notWithComplexAnd() {
        return p -> !(p.age > 10 && p.salary < 5000);
    }

    public static QuerySpec<TestPerson, Boolean> doubleNegation() {
        return p -> !!p.active;
    }

    public static QuerySpec<TestPerson, Boolean> notWithOr() {
        return p -> !(p.active || p.salary > 90000);
    }

    // ==================== ARITHMETIC OPERATIONS ====================

    public static QuerySpec<TestPerson, Boolean> integerAddition() {
        return p -> p.age + 5 > 35;
    }

    public static QuerySpec<TestPerson, Boolean> integerSubtraction() {
        return p -> p.age - 5 > 20;
    }

    public static QuerySpec<TestPerson, Boolean> integerMultiplication() {
        return p -> p.age * 2 > 60;
    }

    public static QuerySpec<TestPerson, Boolean> integerDivision() {
        return p -> p.age / 2 > 15;
    }

    public static QuerySpec<TestPerson, Boolean> integerModulo() {
        return p -> p.age % 10 == 0;
    }

    public static QuerySpec<TestPerson, Boolean> longAddition() {
        return p -> p.employeeId + 10L > 1000010L;
    }

    public static QuerySpec<TestPerson, Boolean> longSubtraction() {
        return p -> p.employeeId - 10L < 1000000L;
    }

    public static QuerySpec<TestPerson, Boolean> longMultiplication() {
        return p -> p.employeeId * 2L > 2000000L;
    }

    public static QuerySpec<TestPerson, Boolean> longDivision() {
        return p -> p.employeeId / 2L < 500002L;
    }

    public static QuerySpec<TestPerson, Boolean> longModulo() {
        return p -> p.employeeId % 2L == 1L;
    }

    public static QuerySpec<TestPerson, Boolean> floatAddition() {
        return p -> p.height + 0.10f > 1.85f;
    }

    public static QuerySpec<TestPerson, Boolean> floatSubtraction() {
        return p -> p.height - 0.05f < 1.70f;
    }

    public static QuerySpec<TestPerson, Boolean> floatMultiplication() {
        return p -> p.height * 2.0f > 3.5f;
    }

    public static QuerySpec<TestPerson, Boolean> floatDivision() {
        return p -> p.height / 2.0f < 0.85f;
    }

    public static QuerySpec<TestPerson, Boolean> doubleAddition() {
        return p -> p.salary + 5000.0 > 80000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleSubtraction() {
        return p -> p.salary - 10000.0 < 70000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleMultiplication() {
        return p -> p.salary * 1.1 > 80000.0;
    }

    public static QuerySpec<TestPerson, Boolean> doubleDivision() {
        return p -> p.salary / 1000.0 > 75.0;
    }

    // Field-field arithmetic
    public static QuerySpec<TestPerson, Boolean> longFieldFieldAddition() {
        return p -> p.employeeId + p.employeeId > 2000000L;
    }

    public static QuerySpec<TestPerson, Boolean> longFieldFieldSubtraction() {
        return p -> p.employeeId - p.employeeId == 0L;
    }

    // ==================== STRING OPERATIONS ====================

    public static QuerySpec<TestPerson, Boolean> stringStartsWith() {
        return p -> p.firstName.startsWith("J");
    }

    public static QuerySpec<TestPerson, Boolean> stringEndsWith() {
        return p -> p.email.endsWith("@example.com");
    }

    public static QuerySpec<TestPerson, Boolean> stringContains() {
        return p -> p.email.contains("john");
    }

    public static QuerySpec<TestPerson, Boolean> stringLength() {
        return p -> p.firstName.length() > 4;
    }

    public static QuerySpec<TestPerson, Boolean> stringToLowerCase() {
        return p -> p.firstName.toLowerCase().equals("john");
    }

    public static QuerySpec<TestPerson, Boolean> stringToUpperCase() {
        return p -> p.firstName.toUpperCase().equals("JANE");
    }

    public static QuerySpec<TestPerson, Boolean> stringTrim() {
        return p -> p.email.trim().equals("david.miller@example.com");
    }

    public static QuerySpec<TestPerson, Boolean> stringIsEmpty() {
        return p -> p.email.isEmpty();
    }

    public static QuerySpec<TestPerson, Boolean> stringIsBlank() {
        return p -> p.email.isBlank();
    }

    public static QuerySpec<TestPerson, Boolean> stringSubstring() {
        return p -> p.firstName.substring(0, 4).equals("John");
    }

    public static QuerySpec<TestPerson, Boolean> stringMethodChaining() {
        return p -> p.email.toLowerCase().contains("example");
    }

    public static QuerySpec<TestPerson, Boolean> stringComplexConditions() {
        return p -> p.email != null && p.email.contains("@") && p.email.endsWith(".com");
    }

    // ==================== COMPLEX EXPRESSIONS ====================

    public static QuerySpec<TestPerson, Boolean> nestedAndOrExpression() {
        return p -> (p.age > 25 && p.age < 35) || p.salary > 80000;
    }

    public static QuerySpec<TestPerson, Boolean> andWithNestedOr() {
        return p -> p.active && (p.age < 30 || p.salary > 80000);
    }

    public static QuerySpec<TestPerson, Boolean> complexNestedOrAnd() {
        return p -> (p.age < 30 || p.age > 40) && (p.active || p.salary > 70000);
    }

    public static QuerySpec<TestPerson, Boolean> tripleAndWithOr() {
        return p -> (p.age >= 25 && p.age <= 30 && p.active) || p.salary > 88000;
    }

    public static QuerySpec<TestPerson, Boolean> deeplyNestedMultipleOrGroups() {
        return p -> ((p.age > 25 && p.age < 40) || p.salary > 85000) &&
                    (p.active || p.firstName.startsWith("B"));
    }

    public static QuerySpec<TestPerson, Boolean> arithmeticInOrGroups() {
        return p -> (p.age + 10 > 40) || (p.age * 2 < 60);
    }

    public static QuerySpec<TestPerson, Boolean> complexArithmeticInOr() {
        return p -> (p.age * 2 - 10 > 50) || (p.age + 15 < 50);
    }

    /**
     * Pattern: (A && B) || (C && D)
     * Two AND groups connected by OR - this is a critical pattern that must be handled correctly.
     */
    public static QuerySpec<TestPerson, Boolean> twoAndGroupsWithOr() {
        return p -> (p.active && p.age > 0) || (p.salary > 50000 && p.height > 1.70f);
    }

    public static QuerySpec<TestPerson, Boolean> complexNestedConditions() {
        return p -> (p.firstName.equals("John") || p.firstName.equals("Jane")) &&
                    p.age >= 25 && p.active;
    }

    // ==================== CAPTURED VARIABLES ====================

    public static QuerySpec<TestPerson, Boolean> capturedStringVariable() {
        String searchName = "John";
        return p -> p.firstName.equals(searchName);
    }

    public static QuerySpec<TestPerson, Boolean> capturedIntVariable() {
        int minAge = 25;
        return p -> p.age > minAge;
    }

    public static QuerySpec<TestPerson, Boolean> capturedDoubleVariable() {
        double minSalary = 50000.0;
        return p -> p.salary >= minSalary;
    }

    public static QuerySpec<TestPerson, Boolean> capturedStringStartsWith() {
        String prefix = "J";
        return p -> p.firstName.startsWith(prefix);
    }

    public static QuerySpec<TestPerson, Boolean> multipleCapturedVariables() {
        String searchName = "John";
        int minAge = 25;
        return p -> p.firstName.equals(searchName) && p.age > minAge;
    }

    public static QuerySpec<TestPerson, Boolean> capturedVariableInComplexExpression() {
        int threshold = 30;
        return p -> (p.age > threshold && p.active) || p.salary > 80000;
    }

    public static QuerySpec<TestPerson, Boolean> capturedBooleanVariable() {
        boolean isActive = true;
        return p -> p.active == isActive;
    }

    public static QuerySpec<TestPerson, Boolean> capturedLongVariable() {
        long employeeId = 1000001L;
        return p -> p.employeeId == employeeId;
    }
}
