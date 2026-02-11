package io.quarkiverse.qubit.it.property;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based integration tests for pagination operations.
 *
 * <p>
 * This class uses parameterized tests to verify mathematical invariants
 * that must hold for various skip/limit combinations.
 *
 * <p>
 * <strong>Properties Tested:</strong>
 * <ul>
 * <li><strong>Skip invariants</strong>: skip(0) returns all, skip(n) returns size-n</li>
 * <li><strong>Limit invariants</strong>: limit(n) returns at most n results</li>
 * <li><strong>Combined invariants</strong>: skip(a).limit(b) returns min(b, max(0, total-a))</li>
 * <li><strong>Ordering preservation</strong>: pagination preserves sort order</li>
 * <li><strong>No overlap property</strong>: adjacent pages have no common elements</li>
 * <li><strong>Complete coverage property</strong>: all pages combined equal total</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.it.fluent.PaginationTest
 */
@QuarkusTest
class PaginationPropertyIT {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAdditionalPersons(); // Creates 6 persons for better pagination testing
    }

    // ======================================================================
    // Skip Invariant Properties
    // ======================================================================

    @Test
    @Transactional
    void skipZeroReturnsAll() {
        List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
        List<Person> skipped = Person.sortedBy((Person p) -> p.id).skip(0).toList();

        assertThat(skipped)
                .as("skip(0) should return all results")
                .hasSize(all.size());
    }

    @ParameterizedTest(name = "skip({0}) returns correct count")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 10 })
    @Transactional
    void skipNReturnsCorrectCount(int skipAmount) {
        List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
        List<Person> skipped = Person.sortedBy((Person p) -> p.id).skip(skipAmount).toList();

        int expectedSize = Math.max(0, all.size() - skipAmount);
        assertThat(skipped)
                .as("skip(%d) should return %d results when total is %d", skipAmount, expectedSize, all.size())
                .hasSize(expectedSize);
    }

    @ParameterizedTest(name = "skip({0}) returns last (total - {0}) elements")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
    @Transactional
    void skipNReturnsLastElements(int skipAmount) {
        List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
        List<Person> skipped = Person.sortedBy((Person p) -> p.id).skip(skipAmount).toList();

        if (skipAmount < all.size()) {
            assertThat(skipped.getFirst().id)
                    .as("First element after skip(%d) should be element at index %d", skipAmount, skipAmount)
                    .isEqualTo(all.get(skipAmount).id);
        }
    }

    // ======================================================================
    // Limit Invariant Properties
    // ======================================================================

    @ParameterizedTest(name = "limit({0}) returns at most {0} results")
    @ValueSource(ints = { 1, 2, 3, 4, 5, 10, 20 })
    @Transactional
    void limitNReturnsAtMostN(int limitAmount) {
        List<Person> limited = Person.sortedBy((Person p) -> p.id).limit(limitAmount).toList();

        assertThat(limited)
                .as("limit(%d) should return at most %d results", limitAmount, limitAmount)
                .hasSizeLessThanOrEqualTo(limitAmount);
    }

    @ParameterizedTest(name = "limit({0}) returns min({0}, total) results")
    @ValueSource(ints = { 1, 2, 3, 4, 5, 10, 20 })
    @Transactional
    void limitNReturnsMinOfNAndTotal(int limitAmount) {
        List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
        List<Person> limited = Person.sortedBy((Person p) -> p.id).limit(limitAmount).toList();

        int expectedSize = Math.min(limitAmount, all.size());
        assertThat(limited)
                .as("limit(%d) should return %d results when total is %d", limitAmount, expectedSize, all.size())
                .hasSize(expectedSize);
    }

    @ParameterizedTest(name = "limit({0}) returns first {0} elements")
    @ValueSource(ints = { 1, 2, 3, 4, 5 })
    @Transactional
    void limitNReturnsFirstElements(int limitAmount) {
        List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
        List<Person> limited = Person.sortedBy((Person p) -> p.id).limit(limitAmount).toList();

        for (int i = 0; i < limited.size(); i++) {
            assertThat(limited.get(i).id)
                    .as("Element %d of limit(%d) should match element %d of all", i, limitAmount, i)
                    .isEqualTo(all.get(i).id);
        }
    }

    // ======================================================================
    // Combined Skip + Limit Properties
    // ======================================================================

    @Test
    @Transactional
    void skipAndLimitReturnCorrectCount() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 3 }, { 3, 2 }, { 5, 3 }, { 10, 2 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
            List<Person> paged = Person.sortedBy((Person p) -> p.id)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            int expectedSize = Math.min(limitAmount, Math.max(0, all.size() - skipAmount));
            assertThat(paged)
                    .as("skip(%d).limit(%d) should return %d results when total is %d",
                            skipAmount, limitAmount, expectedSize, all.size())
                    .hasSize(expectedSize);
        }
    }

    @Test
    @Transactional
    void skipAndLimitReturnCorrectWindow() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 2 }, { 0, 4 }, { 1, 3 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> all = Person.sortedBy((Person p) -> p.id).toList();
            List<Person> paged = Person.sortedBy((Person p) -> p.id)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            for (int i = 0; i < paged.size(); i++) {
                int expectedIndex = skipAmount + i;
                assertThat(paged.get(i).id)
                        .as("Element %d of skip(%d).limit(%d) should match element %d of all",
                                i, skipAmount, limitAmount, expectedIndex)
                        .isEqualTo(all.get(expectedIndex).id);
            }
        }
    }

    // ======================================================================
    // No Overlap Property (Adjacent Pages)
    // ======================================================================

    @ParameterizedTest(name = "adjacent pages (size={0}) have no common elements")
    @ValueSource(ints = { 1, 2, 3 })
    @Transactional
    void adjacentPagesNoOverlap(int pageSize) {
        List<Person> page1 = Person.sortedBy((Person p) -> p.id)
                .skip(0)
                .limit(pageSize)
                .toList();

        List<Person> page2 = Person.sortedBy((Person p) -> p.id)
                .skip(pageSize)
                .limit(pageSize)
                .toList();

        List<Long> page1Ids = page1.stream().map(p -> p.id).toList();
        List<Long> page2Ids = page2.stream().map(p -> p.id).toList();

        assertThat(page1Ids)
                .as("Page 1 and Page 2 should have no common elements")
                .doesNotContainAnyElementsOf(page2Ids);
    }

    // ======================================================================
    // Complete Coverage Property (All Pages Combined)
    // ======================================================================

    @ParameterizedTest(name = "all pages (size={0}) combined equal total results")
    @ValueSource(ints = { 1, 2, 3, 4 })
    @Transactional
    void allPagesCombinedEqualTotal(int pageSize) {
        List<Person> all = Person.sortedBy((Person p) -> p.id).toList();

        int totalFromPages = 0;
        int pageNumber = 0;

        while (true) {
            List<Person> page = Person.sortedBy((Person p) -> p.id)
                    .skip(pageNumber * pageSize)
                    .limit(pageSize)
                    .toList();

            if (page.isEmpty()) {
                break;
            }

            totalFromPages += page.size();
            pageNumber++;

            if (pageNumber > 100) {
                break;
            }
        }

        assertThat(totalFromPages)
                .as("Sum of all page sizes should equal total count")
                .isEqualTo(all.size());
    }

    // ======================================================================
    // Ordering Preservation Properties
    // ======================================================================

    @ParameterizedTest(name = "skip({0}) preserves sort order")
    @ValueSource(ints = { 0, 1, 2, 3, 4 })
    @Transactional
    void skipPreservesSortOrder(int skipAmount) {
        List<Person> skipped = Person.sortedBy((Person p) -> p.id).skip(skipAmount).toList();

        for (int i = 1; i < skipped.size(); i++) {
            assertThat(skipped.get(i).id)
                    .as("Elements should remain in ascending order after skip")
                    .isGreaterThan(skipped.get(i - 1).id);
        }
    }

    @ParameterizedTest(name = "limit({0}) preserves sort order")
    @ValueSource(ints = { 1, 2, 3, 4, 5, 6 })
    @Transactional
    void limitPreservesSortOrder(int limitAmount) {
        List<Person> limited = Person.sortedBy((Person p) -> p.id).limit(limitAmount).toList();

        for (int i = 1; i < limited.size(); i++) {
            assertThat(limited.get(i).id)
                    .as("Elements should remain in ascending order after limit")
                    .isGreaterThan(limited.get(i - 1).id);
        }
    }

    @Test
    @Transactional
    void skipLimitPreservesDescendingOrder() {
        int[][] testCases = { { 0, 3 }, { 1, 2 }, { 2, 3 }, { 0, 4 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> paged = Person.sortedDescendingBy((Person p) -> p.age)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            for (int i = 1; i < paged.size(); i++) {
                assertThat(paged.get(i).age)
                        .as("Elements should remain in descending order after skip.limit")
                        .isLessThanOrEqualTo(paged.get(i - 1).age);
            }
        }
    }

    // ======================================================================
    // Pagination with Filter Properties
    // ======================================================================

    @Test
    @Transactional
    void paginationRespectsFilter() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 3 }, { 0, 4 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> paged = Person.where((Person p) -> p.active)
                    .sortedBy((Person p) -> p.id)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            assertThat(paged)
                    .as("All paginated results should satisfy the filter predicate")
                    .allMatch(p -> p.active);
        }
    }

    @Test
    @Transactional
    void paginationWithFilterReturnsCorrectCount() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 3 }, { 3, 2 }, { 5, 2 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> allActive = Person.where((Person p) -> p.active)
                    .sortedBy((Person p) -> p.id)
                    .toList();

            List<Person> paged = Person.where((Person p) -> p.active)
                    .sortedBy((Person p) -> p.id)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            int expectedSize = Math.min(limitAmount, Math.max(0, allActive.size() - skipAmount));
            assertThat(paged)
                    .as("Paginated filtered results should have correct count")
                    .hasSize(expectedSize);
        }
    }
}
