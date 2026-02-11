package io.quarkiverse.qubit.it.native_;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for PersonResource that run against the native image.
 * Uses RestAssured to verify Qubit query operations work correctly in native mode.
 */
@QuarkusIntegrationTest
public class PersonResourceIT {

    // =============================================================================================
    // BASIC QUERIES
    // =============================================================================================

    @Test
    void getAll_returnsAllPersons() {
        given()
                .when().get("/api/persons")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(5)));
    }

    @Test
    void count_returnsCorrectCount() {
        given()
                .when().get("/api/persons/count")
                .then()
                .statusCode(200)
                .body(greaterThanOrEqualTo("5"));
    }

    @Test
    void getActive_returnsOnlyActivePersons() {
        given()
                .when().get("/api/persons/active")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("active", everyItem(is(true)));
    }

    @Test
    void getInactive_returnsOnlyInactivePersons() {
        given()
                .when().get("/api/persons/inactive")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("active", everyItem(is(false)));
    }

    @Test
    void countActive_returnsCorrectCount() {
        given()
                .when().get("/api/persons/active/count")
                .then()
                .statusCode(200)
                .body(greaterThanOrEqualTo("1"));
    }

    // =============================================================================================
    // COMPARISON OPERATIONS
    // =============================================================================================

    @Test
    void getAgeGreaterThan_filtersCorrectly() {
        given()
                .when().get("/api/persons/age-greater-than/30")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", everyItem(greaterThan(30)));
    }

    @Test
    void getAgeGreaterOrEqual_filtersCorrectly() {
        given()
                .when().get("/api/persons/age-greater-or-equal/30")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", everyItem(greaterThanOrEqualTo(30)));
    }

    @Test
    void getAgeLessThan_filtersCorrectly() {
        given()
                .when().get("/api/persons/age-less-than/30")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", everyItem(lessThan(30)));
    }

    @Test
    void getAgeLessOrEqual_filtersCorrectly() {
        given()
                .when().get("/api/persons/age-less-or-equal/30")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", everyItem(lessThanOrEqualTo(30)));
    }

    @Test
    void getAgeNotEqual_filtersCorrectly() {
        given()
                .when().get("/api/persons/age-not-equal/30")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", not(hasItem(30)));
    }

    @Test
    void getAgeBetween_filtersCorrectly() {
        given()
                .when().get("/api/persons/age-between/25/35")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", everyItem(allOf(greaterThanOrEqualTo(25), lessThanOrEqualTo(35))));
    }

    // =============================================================================================
    // LOGICAL OPERATIONS
    // =============================================================================================

    @Test
    void getActiveAndAgeOver_combinesConditions() {
        given()
                .when().get("/api/persons/active-and-age-over/25")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("active", everyItem(is(true)))
                .body("age", everyItem(greaterThan(25)));
    }

    @Test
    void getActiveOrAgeOver_combinesWithOr() {
        given()
                .when().get("/api/persons/active-or-age-over/40")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    @Test
    void getComplexAnd_multipleConditions() {
        given()
                .when().get("/api/persons/complex-and")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("age", everyItem(greaterThanOrEqualTo(30)))
                .body("active", everyItem(is(true)))
                .body("salary", everyItem(greaterThan(70000.0f)));
    }

    @Test
    void getComplexOr_multipleConditions() {
        given()
                .when().get("/api/persons/complex-or")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    @Test
    void getComplexMixed_mixedAndOr() {
        given()
                .when().get("/api/persons/complex-mixed")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)));
    }

    // =============================================================================================
    // STRING OPERATIONS
    // =============================================================================================

    @Test
    void getFirstNameStartsWith_filtersCorrectly() {
        given()
                .when().get("/api/persons/first-name-starts-with/J")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("firstName", everyItem(startsWith("J")));
    }

    @Test
    void getEmailContains_filtersCorrectly() {
        given()
                .when().get("/api/persons/email-contains/@example.com")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("email", everyItem(containsString("@example.com")));
    }

    @Test
    void getLastNameEndsWith_filtersCorrectly() {
        given()
                .when().get("/api/persons/last-name-ends-with/son")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("lastName", everyItem(endsWith("son")));
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @Test
    void getSortedByAge_sortsAscending() {
        var ages = given()
                .when().get("/api/persons/sorted-by-age")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("age", Integer.class);

        for (int i = 1; i < ages.size(); i++) {
            assert ages.get(i - 1) <= ages.get(i) : "Ages should be sorted ascending";
        }
    }

    @Test
    void getSortedByAgeDesc_sortsDescending() {
        var ages = given()
                .when().get("/api/persons/sorted-by-age-desc")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("age", Integer.class);

        for (int i = 1; i < ages.size(); i++) {
            assert ages.get(i - 1) >= ages.get(i) : "Ages should be sorted descending";
        }
    }

    @Test
    void getSortedByFirstName_sortsAlphabetically() {
        var names = given()
                .when().get("/api/persons/sorted-by-first-name")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("firstName", String.class);

        for (int i = 1; i < names.size(); i++) {
            assert names.get(i - 1).compareTo(names.get(i)) <= 0 : "Names should be sorted alphabetically";
        }
    }

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    @Test
    void getPaginated_returnsCorrectPage() {
        given()
                .when().get("/api/persons/paginated/0/2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(2));
    }

    @Test
    void getFirst_limitsResults() {
        given()
                .when().get("/api/persons/first/3")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(3));
    }

    // =============================================================================================
    // PROJECTIONS
    // =============================================================================================

    @Test
    void getFirstNames_projectsCorrectly() {
        given()
                .when().get("/api/persons/first-names")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("$", everyItem(instanceOf(String.class)));
    }

    @Test
    void getAges_projectsCorrectly() {
        given()
                .when().get("/api/persons/ages")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("$", everyItem(instanceOf(Integer.class)));
    }

    @Test
    void getDistinctLastNames_removeDuplicates() {
        var lastNames = given()
                .when().get("/api/persons/distinct-last-names")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .extract().jsonPath().getList("$", String.class);

        // Verify no duplicates
        long uniqueCount = lastNames.stream().distinct().count();
        assert uniqueCount == lastNames.size() : "Should not have duplicates";
    }

    // =============================================================================================
    // AGGREGATIONS
    // =============================================================================================

    @Test
    void getMinAge_returnsMinimum() {
        given()
                .when().get("/api/persons/min-age")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getMaxAge_returnsMaximum() {
        given()
                .when().get("/api/persons/max-age")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getAvgAge_returnsAverage() {
        given()
                .when().get("/api/persons/avg-age")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getSumAge_returnsSum() {
        given()
                .when().get("/api/persons/sum-age")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void getAvgSalary_returnsAverageSalary() {
        given()
                .when().get("/api/persons/avg-salary")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    // =============================================================================================
    // CHAINED OPERATIONS
    // =============================================================================================

    @Test
    void getActiveSortedByAge_combinesFilterAndSort() {
        var ages = given()
                .when().get("/api/persons/active-sorted-by-age")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThan(0)))
                .body("active", everyItem(is(true)))
                .extract().jsonPath().getList("age", Integer.class);

        for (int i = 1; i < ages.size(); i++) {
            assert ages.get(i - 1) <= ages.get(i) : "Ages should be sorted ascending";
        }
    }

    @Test
    void getActiveTop_combinesFilterSortLimit() {
        given()
                .when().get("/api/persons/active-top/3")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(lessThanOrEqualTo(3)))
                .body("active", everyItem(is(true)));
    }

    @Test
    void getActiveInAgeRange_multipleFilters() {
        given()
                .when().get("/api/persons/active-age-range/25/35")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("active", everyItem(is(true)))
                .body("age", everyItem(allOf(greaterThanOrEqualTo(25), lessThanOrEqualTo(35))));
    }

    // =============================================================================================
    // SINGLE RESULT OPERATIONS
    // =============================================================================================

    @Test
    void getFirstActive_returnsSinglePerson() {
        given()
                .when().get("/api/persons/first-active")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("active", is(true));
    }

    @Test
    void existsWithAge_returnsTrue() {
        given()
                .when().get("/api/persons/exists-age/30")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    @Test
    void existsActive_returnsTrue() {
        given()
                .when().get("/api/persons/exists-active")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    // =============================================================================================
    // JOIN QUERIES - BiQuerySpec lambda native mode tests
    // Tests join operations between Person and Phone entities using bi-entity predicates
    // =============================================================================================

    @Test
    void joinWithPhones_returnsPersonsWithPhones() {
        given()
                .when().get("/api/persons/join/with-phones")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void joinWithMobilePhones_filtersOnJoinedEntity() {
        given()
                .when().get("/api/persons/join/with-mobile-phones")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void joinWithPhoneType_capturedVariable() {
        given()
                .when().get("/api/persons/join/with-phone-type/work")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void joinActiveWithMobile_combinedPredicates() {
        given()
                .when().get("/api/persons/join/active-with-mobile")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("active", everyItem(is(true)));
    }

    @Test
    void joinCountMobile_returnsCount() {
        given()
                .when().get("/api/persons/join/count-mobile")
                .then()
                .statusCode(200)
                .body(greaterThanOrEqualTo("1"));
    }

    @Test
    void joinExistsWork_returnsTrue() {
        given()
                .when().get("/api/persons/join/exists-work")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    @Test
    void joinSelectJoined_returnsPhones() {
        given()
                .when().get("/api/persons/join/select-joined")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("number", everyItem(notNullValue()));
    }

    @Test
    void joinSelectJoinedMobile_returnsMobilePhones() {
        given()
                .when().get("/api/persons/join/select-joined-mobile")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("type", everyItem(is("mobile")));
    }

    @Test
    void joinProjection_returnsPersonPhoneDTOs() {
        given()
                .when().get("/api/persons/join/projection")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("personName", everyItem(notNullValue()))
                .body("phoneNumber", everyItem(notNullValue()));
    }

    @Test
    void joinProjectionMobile_filtersThenProjects() {
        given()
                .when().get("/api/persons/join/projection-mobile")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("personName", everyItem(notNullValue()))
                .body("phoneNumber", everyItem(notNullValue()));
    }

    @Test
    void leftJoinWithPhones_returnsAllPersons() {
        given()
                .when().get("/api/persons/join/left/with-phones")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void leftJoinWithPhoneType_capturedVariable() {
        given()
                .when().get("/api/persons/join/left/with-phone-type/home")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void joinWithLimit_limitResults() {
        given()
                .when().get("/api/persons/join/with-limit/2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(lessThanOrEqualTo(2)));
    }

    // =============================================================================================
    // GROUP BY QUERIES - GroupQuerySpec lambda native mode tests
    // Tests GROUP BY operations with aggregations using Group<T,K> parameter
    // =============================================================================================

    @Test
    void groupByDepartment_returnsDistinctDepartments() {
        given()
                .when().get("/api/persons/group/by-department")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void groupByDepartmentCount_returnsNumberOfGroups() {
        given()
                .when().get("/api/persons/group/by-department/count")
                .then()
                .statusCode(200)
                .body(greaterThanOrEqualTo("1"));
    }

    @Test
    void groupByDepartmentStats_returnsStatsDTO() {
        given()
                .when().get("/api/persons/group/by-department/stats")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("departmentName", everyItem(notNullValue()))
                .body("employeeCount", everyItem(greaterThanOrEqualTo(1)));
    }

    @Test
    void groupByDepartmentStatsWithAvg_includesAverageSalary() {
        given()
                .when().get("/api/persons/group/by-department/stats-with-avg")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("departmentName", everyItem(notNullValue()))
                .body("employeeCount", everyItem(greaterThanOrEqualTo(1)))
                .body("averageSalary", everyItem(notNullValue()));
    }

    @Test
    void groupByDepartmentHavingCount_filtersGroups() {
        given()
                .when().get("/api/persons/group/by-department/having-count-greater/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void groupByDepartmentHavingAvgSalary_filtersGroupsBySalary() {
        given()
                .when().get("/api/persons/group/by-department/having-avg-salary-greater/50000")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void groupByDepartmentSortedByKey_sortsByDepartmentName() {
        var departments = given()
                .when().get("/api/persons/group/by-department/sorted-by-key")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .extract().jsonPath().getList("$", String.class);

        // Verify alphabetical order
        for (int i = 1; i < departments.size(); i++) {
            assert departments.get(i - 1).compareTo(departments.get(i)) <= 0
                    : "Departments should be sorted alphabetically";
        }
    }

    @Test
    void groupByDepartmentSortedByCountDesc_sortsByEmployeeCount() {
        given()
                .when().get("/api/persons/group/by-department/sorted-by-count-desc")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("departmentName", everyItem(notNullValue()))
                .body("employeeCount", everyItem(greaterThanOrEqualTo(1)));
    }

    @Test
    void groupByDepartmentWithLimit_limitsGroups() {
        given()
                .when().get("/api/persons/group/by-department/limit/2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(lessThanOrEqualTo(2)));
    }

    @Test
    void groupActiveByDepartment_preFiltersBeforeGrouping() {
        given()
                .when().get("/api/persons/group/active/by-department")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void groupActiveByDepartmentStats_combinesWhereAndGroup() {
        given()
                .when().get("/api/persons/group/active/by-department/stats")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("departmentName", everyItem(notNullValue()))
                .body("employeeCount", everyItem(greaterThanOrEqualTo(1)));
    }
}
