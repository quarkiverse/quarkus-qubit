package io.quarkiverse.qubit.it.native_;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for ProductResource that run against the native image.
 * Uses RestAssured to verify Qubit query operations work correctly in native mode.
 */
@QuarkusIntegrationTest
public class ProductResourceIT {

    // =============================================================================================
    // BASIC QUERIES
    // =============================================================================================

    @Test
    void getAll_returnsAllProducts() {
        given()
                .when().get("/api/products")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(5)));
    }

    @Test
    void count_returnsCorrectCount() {
        given()
                .when().get("/api/products/count")
                .then()
                .statusCode(200)
                .body(greaterThanOrEqualTo("5"));
    }

    @Test
    void getAvailable_returnsOnlyAvailableProducts() {
        given()
                .when().get("/api/products/available")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("available", everyItem(is(true)));
    }

    @Test
    void getUnavailable_returnsOnlyUnavailableProducts() {
        given()
                .when().get("/api/products/unavailable")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("available", everyItem(is(false)));
    }

    @Test
    void getInStock_returnsProductsWithStock() {
        given()
                .when().get("/api/products/in-stock")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("stockQuantity", everyItem(greaterThan(0)));
    }

    // =============================================================================================
    // BIGDECIMAL OPERATIONS
    // =============================================================================================

    @Test
    void getPriceGreaterThan_filtersCorrectly() {
        given()
                .when().get("/api/products/price-greater-than/500")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    @Test
    void getPriceLessThan_filtersCorrectly() {
        given()
                .when().get("/api/products/price-less-than/500")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    @Test
    void getPriceBetween_filtersCorrectly() {
        given()
                .when().get("/api/products/price-between/100/500")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    // =============================================================================================
    // CATEGORY QUERIES
    // =============================================================================================

    @Test
    void getByCategory_filtersCorrectly() {
        given()
                .when().get("/api/products/category/Electronics")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("category", everyItem(equalTo("Electronics")));
    }

    @Test
    void getCategoryContains_filtersCorrectly() {
        given()
                .when().get("/api/products/category-contains/Electro")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("category", everyItem(containsString("Electro")));
    }

    // =============================================================================================
    // LOGICAL OPERATIONS
    // =============================================================================================

    @Test
    void getAvailableAndInStock_combinesConditions() {
        given()
                .when().get("/api/products/available-and-in-stock")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("available", everyItem(is(true)))
                .body("stockQuantity", everyItem(greaterThan(0)));
    }

    @Test
    void getAvailableOrLowPrice_combinesWithOr() {
        given()
                .when().get("/api/products/available-or-low-price")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @Test
    void getSortedByPrice_sortsAscending() {
        var prices = given()
                .when().get("/api/products/sorted-by-price")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("price", Float.class);

        for (int i = 1; i < prices.size(); i++) {
            assert prices.get(i - 1) <= prices.get(i) : "Prices should be sorted ascending";
        }
    }

    @Test
    void getSortedByPriceDesc_sortsDescending() {
        var prices = given()
                .when().get("/api/products/sorted-by-price-desc")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("price", Float.class);

        for (int i = 1; i < prices.size(); i++) {
            assert prices.get(i - 1) >= prices.get(i) : "Prices should be sorted descending";
        }
    }

    @Test
    void getSortedByName_sortsAlphabetically() {
        var names = given()
                .when().get("/api/products/sorted-by-name")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("name", String.class);

        for (int i = 1; i < names.size(); i++) {
            assert names.get(i - 1).compareTo(names.get(i)) <= 0 : "Names should be sorted alphabetically";
        }
    }

    // =============================================================================================
    // PROJECTIONS
    // =============================================================================================

    @Test
    void getNames_projectsCorrectly() {
        given()
                .when().get("/api/products/names")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("$", everyItem(instanceOf(String.class)));
    }

    @Test
    void getCategories_returnsDistinct() {
        var categories = given()
                .when().get("/api/products/categories")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("$", String.class);

        // Verify no duplicates
        long uniqueCount = categories.stream().distinct().count();
        assert uniqueCount == categories.size() : "Should not have duplicates";
    }

    // =============================================================================================
    // AGGREGATIONS
    // =============================================================================================

    @Test
    void getMinPrice_returnsMinimum() {
        given()
                .when().get("/api/products/min-price")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getMaxPrice_returnsMaximum() {
        given()
                .when().get("/api/products/max-price")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getAvgRating_returnsAverage() {
        given()
                .when().get("/api/products/avg-rating")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getSumStock_returnsSum() {
        given()
                .when().get("/api/products/sum-stock")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    // =============================================================================================
    // CHAINED OPERATIONS
    // =============================================================================================

    @Test
    void getAvailableSortedByPrice_combinesFilterAndSort() {
        var prices = given()
                .when().get("/api/products/available-sorted-by-price")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("available", everyItem(is(true)))
                .extract().jsonPath().getList("price", Float.class);

        for (int i = 1; i < prices.size(); i++) {
            assert prices.get(i - 1) <= prices.get(i) : "Prices should be sorted ascending";
        }
    }

    @Test
    void getAvailableTopByPrice_combinesFilterSortLimit() {
        given()
                .when().get("/api/products/available-top/2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(lessThanOrEqualTo(2)))
                .body("available", everyItem(is(true)));
    }

    @Test
    void getCategorySortedByPrice_filtersByCategory() {
        given()
                .when().get("/api/products/category/Electronics/sorted-by-price")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("category", everyItem(equalTo("Electronics")));
    }

    // =============================================================================================
    // SINGLE RESULT OPERATIONS
    // =============================================================================================

    @Test
    void getFirstAvailable_returnsSingleProduct() {
        given()
                .when().get("/api/products/first-available")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("available", is(true));
    }

    @Test
    void existsInCategory_returnsTrue() {
        given()
                .when().get("/api/products/exists-category/Electronics")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    @Test
    void getCheapest_returnsCheapestProduct() {
        given()
                .when().get("/api/products/cheapest")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", notNullValue());
    }

    @Test
    void getMostExpensive_returnsMostExpensiveProduct() {
        given()
                .when().get("/api/products/most-expensive")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", notNullValue());
    }
}
