package io.quarkiverse.qubit.it.relationship;

import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PhoneQueryOperations;
import io.quarkiverse.qubit.it.testutil.TagQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for relationship navigation tests.
 * <p>
 * Tests Phone and Tag entity queries using the fluent API.
 */
public abstract class AbstractRelationshipNavigationTest {

    protected abstract PhoneQueryOperations phoneOps();

    protected abstract TagQueryOperations tagOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAllDataWithRelationships();
    }

    // ========== PHONE ENTITY TESTS ==========

    @Test
    void phonesByType() {
        var results = phoneOps().where((Phone ph) -> ph.type.equals("mobile")).toList();

        assertThat(results)
                .hasSize(5)
                .allMatch(ph -> ph.getType().equals("mobile"));
    }

    @Test
    void phonesByNumber() {
        var results = phoneOps().where((Phone ph) -> ph.number.equals("555-0101")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Phone::getNumber)
                .containsExactly("555-0101");
    }

    @Test
    void phonesByPrimaryStatus() {
        var results = phoneOps().where((Phone ph) -> ph.isPrimaryPhone).toList();

        assertThat(results)
                .hasSize(5)
                .allMatch(Phone::isPrimaryPhone);
    }

    @Test
    void workPhones() {
        var results = phoneOps().where((Phone ph) -> ph.type.equals("work")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("work"));
    }

    @Test
    void homePhones() {
        var results = phoneOps().where((Phone ph) -> ph.type.equals("home")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("home"));
    }

    @Test
    void phoneCountByType() {
        long mobileCount = phoneOps().where((Phone ph) -> ph.type.equals("mobile")).count();
        long workCount = phoneOps().where((Phone ph) -> ph.type.equals("work")).count();

        assertThat(mobileCount).isEqualTo(5);
        assertThat(workCount).isGreaterThan(0);
    }

    @Test
    void phoneExists() {
        boolean exists = phoneOps().where((Phone ph) -> ph.number.startsWith("555-")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void phoneNumberStartsWith() {
        var results = phoneOps().where((Phone ph) -> ph.number.startsWith("555-01")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getNumber().startsWith("555-01"));
    }

    // ========== TAG ENTITY TESTS ==========

    @Test
    void tagsByName() {
        var results = tagOps().where((Tag t) -> t.name.equals("electronics")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Tag::getName)
                .containsExactly("electronics");
    }

    @Test
    void tagsByColor() {
        var results = tagOps().where((Tag t) -> t.color.equals("blue")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Tag::getColor)
                .containsExactly("blue");
    }

    @Test
    void tagsStartingWith() {
        var results = tagOps().where((Tag t) -> t.name.startsWith("b")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(t -> t.getName().startsWith("b"));
    }

    @Test
    void tagCount() {
        long count = tagOps().where((Tag t) -> t.name.contains("-")).count();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void tagExists() {
        boolean exists = tagOps().where((Tag t) -> t.name.equals("premium")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void tagNotExists() {
        boolean exists = tagOps().where((Tag t) -> t.name.equals("nonexistent")).exists();

        assertThat(exists).isFalse();
    }
}
