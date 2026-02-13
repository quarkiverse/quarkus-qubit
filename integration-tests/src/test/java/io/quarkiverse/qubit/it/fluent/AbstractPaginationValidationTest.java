package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract base class for validation and error handling tests for pagination operations.
 *
 * <p>
 * Tests that invalid inputs to skip() and limit() are properly rejected.
 */
public abstract class AbstractPaginationValidationTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // SKIP VALIDATION TESTS

    @Test
    void skip_negativeValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .skip(-1)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0")
                .hasMessageContaining("-1");
    }

    @Test
    void skip_negativeLargeValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .skip(-999)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0")
                .hasMessageContaining("-999");
    }

    @Test
    void skip_integerMinValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .skip(Integer.MIN_VALUE)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0");
    }

    // LIMIT VALIDATION TESTS

    @Test
    void limit_negativeValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .limit(-1)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit count must be >= 0")
                .hasMessageContaining("-1");
    }

    @Test
    void limit_negativeLargeValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .limit(-100)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit count must be >= 0")
                .hasMessageContaining("-100");
    }

    @Test
    void limit_integerMinValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .limit(Integer.MIN_VALUE)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit count must be >= 0");
    }

    // COMBINED VALIDATION TESTS

    @Test
    void skipAndLimit_bothNegative_throwsIllegalArgumentException() {
        // skip() is called first, so it should throw
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .skip(-1)
                .limit(-1)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0");
    }

    @Test
    void skipAndLimit_skipNegativeLimitPositive_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .skip(-5)
                .limit(10)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0")
                .hasMessageContaining("-5");
    }

    @Test
    void skipAndLimit_skipPositiveLimitNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().sortedBy((Person p) -> p.id)
                .skip(5)
                .limit(-10)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit count must be >= 0")
                .hasMessageContaining("-10");
    }

    // VALIDATION WITH PREDICATES AND PROJECTIONS

    @Test
    void skip_negativeWithPredicate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().where((Person p) -> p.active)
                .skip(-3)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0");
    }

    @Test
    void limit_negativeWithProjection_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().select((Person p) -> p.firstName)
                .limit(-2)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit count must be >= 0");
    }

    @Test
    void skip_negativeInComplexQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> personOps().where((Person p) -> p.salary > 50000.0)
                .select((Person p) -> p.firstName)
                .sortedBy((String name) -> name)
                .skip(-1)
                .limit(5)
                .toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip count must be >= 0");
    }
}
