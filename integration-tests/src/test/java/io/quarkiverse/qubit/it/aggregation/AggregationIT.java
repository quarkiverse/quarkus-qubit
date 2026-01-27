package io.quarkiverse.qubit.it.aggregation;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for aggregation queries using static entity methods.
 * <p>
 * Inherits Person aggregation tests from AbstractAggregationTest
 * and adds Product-specific aggregation tests.
 */
@QuarkusTest
class AggregationIT extends AbstractAggregationTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }

    @BeforeEach
    @Transactional
    @Override
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // ========== PRODUCT AGGREGATIONS (6 tests) ==========

    @Test
    void productMinStockQuantity() {
        Integer minStock = Product.min((Product p) -> p.stockQuantity).getSingleResult();

        // Stock quantities: 50 (Laptop), 100 (Smartphone), 25 (Chair), 0 (Coffee Maker), 30 (Monitor)
        assertThat(minStock).isEqualTo(0); // Coffee Maker
    }

    @Test
    void productMaxStockQuantity() {
        Integer maxStock = Product.max((Product p) -> p.stockQuantity).getSingleResult();

        assertThat(maxStock).isEqualTo(100); // Smartphone
    }

    @Test
    void productAvgRating() {
        Double avgRating = Product.avg((Product p) -> p.rating).getSingleResult();

        // Ratings: 4.5, 4.7, 4.2, 4.0, 4.6 → average = 22 / 5 = 4.4
        assertThat(avgRating).isEqualTo(4.4);
    }

    @Test
    void productSumStockQuantity() {
        Long totalStock = Product.sumInteger((Product p) -> p.stockQuantity).getSingleResult();

        // Sum: 50 + 100 + 25 + 0 + 30 = 205
        assertThat(totalStock).isEqualTo(205L);
    }

    @Test
    void productMinRatingWithPredicate() {
        Double minRating = Product.where((Product p) -> p.available)
                .min((Product p) -> p.rating).getSingleResult();

        // Available products: Laptop (4.5), Smartphone (4.7), Chair (4.2), Monitor (4.6)
        assertThat(minRating).isEqualTo(4.2); // Chair
    }

    @Test
    void productMaxStockQuantityWithPredicate() {
        Integer maxStock = Product.where((Product p) -> p.category.equals("Electronics"))
                .max((Product p) -> p.stockQuantity).getSingleResult();

        // Electronics: Laptop (50), Smartphone (100), Monitor (30)
        assertThat(maxStock).isEqualTo(100); // Smartphone
    }

    // ========== NULL HANDLING (3 tests) ==========

    @Test
    @Transactional
    void minHeight_withNullValues_skipsNulls() {
        Person personWithNullHeight = new Person();
        personWithNullHeight.firstName = "NullHeight";
        personWithNullHeight.lastName = "Person";
        personWithNullHeight.age = 40;
        personWithNullHeight.height = null;
        personWithNullHeight.persist();

        Float minHeight = personOps().min((Person p) -> p.height).getSingleResult();

        assertThat(minHeight).isEqualTo(1.65f);
    }

    @Test
    @Transactional
    void avgSalary_withNullValues_skipsNulls() {
        Person personWithNullSalary = new Person();
        personWithNullSalary.firstName = "NullSalary";
        personWithNullSalary.lastName = "Person";
        personWithNullSalary.age = 40;
        personWithNullSalary.salary = null;
        personWithNullSalary.persist();

        Double avgSalary = personOps().avg((Person p) -> p.salary).getSingleResult();

        assertThat(avgSalary).isEqualTo(74000.0);
    }

    @Test
    @Transactional
    void sumLongEmployeeId_withNullValues_skipsNulls() {
        Person personWithNullEmployeeId = new Person();
        personWithNullEmployeeId.firstName = "NullEmpId";
        personWithNullEmployeeId.lastName = "Person";
        personWithNullEmployeeId.age = 40;
        personWithNullEmployeeId.employeeId = null;
        personWithNullEmployeeId.persist();

        Long sumEmployeeId = personOps().sumLong((Person p) -> p.employeeId).getSingleResult();

        assertThat(sumEmployeeId).isEqualTo(5000015L);
    }

    // ========== EMPTY RESULT SETS (3 tests) ==========

    @Test
    @Transactional
    void minAge_emptyResultSet_returnsNull() {
        Person.deleteAll();

        Integer minAge = personOps().min((Person p) -> p.age).getSingleResult();

        assertThat(minAge).isNull();
    }

    @Test
    @Transactional
    void avgSalary_emptyResultSet_returnsNull() {
        Person.deleteAll();

        Double avgSalary = personOps().avg((Person p) -> p.salary).getSingleResult();

        assertThat(avgSalary).isNull();
    }

    @Test
    @Transactional
    void sumDoubleSalary_emptyResultSet_returnsNull() {
        Person.deleteAll();

        Double sumSalary = personOps().sumDouble((Person p) -> p.salary).getSingleResult();

        assertThat(sumSalary).isNull();
    }
}
