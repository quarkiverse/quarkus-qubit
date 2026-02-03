package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class PersonResourceTest {

    @Test
    void list_returns_all_persons() {
        given()
            .when().get("/persons")
            .then()
            .statusCode(200);
    }

    @Test
    void find_adults_filters_by_age() {
        given()
            .queryParam("minAge", 18)
            .when().get("/persons/adults")
            .then()
            .statusCode(200);
    }

    @Test
    void count_adults_returns_number() {
        given()
            .when().get("/persons/count-adults")
            .then()
            .statusCode(200)
            .body(greaterThanOrEqualTo("0"));
    }

    @Test
    void create_person() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"Test Person\",\"age\":25,\"active\":true}")
            .when().post("/persons")
            .then()
            .statusCode(200)
            .body("name", is("Test Person"))
            .body("age", is(25))
            .body("active", is(true));
    }
}
