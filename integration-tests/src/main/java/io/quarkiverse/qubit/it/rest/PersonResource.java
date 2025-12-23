package io.quarkiverse.qubit.it.rest;

import io.quarkiverse.qubit.it.Person;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * REST resource for Person queries to test Qubit extension in native mode.
 * Each endpoint exercises specific Qubit query operations.
 */
@Path("/api/persons")
@Produces(MediaType.APPLICATION_JSON)
public class PersonResource {

    // =============================================================================================
    // BASIC QUERIES - where(), toList(), count()
    // =============================================================================================

    @GET
    public List<Person> getAll() {
        return Person.listAll();
    }

    @GET
    @Path("/count")
    public long count() {
        return Person.count();
    }

    @GET
    @Path("/active")
    public List<Person> getActive() {
        return Person.where((Person p) -> p.active).toList();
    }

    @GET
    @Path("/active/count")
    public long countActive() {
        return Person.where((Person p) -> p.active).count();
    }

    @GET
    @Path("/inactive")
    public List<Person> getInactive() {
        return Person.where((Person p) -> !p.active).toList();
    }

    // =============================================================================================
    // COMPARISON OPERATIONS - >, <, >=, <=, !=
    // =============================================================================================

    @GET
    @Path("/age-greater-than/{age}")
    public List<Person> getAgeGreaterThan(@PathParam("age") int age) {
        return Person.where((Person p) -> p.age > age).toList();
    }

    @GET
    @Path("/age-greater-or-equal/{age}")
    public List<Person> getAgeGreaterOrEqual(@PathParam("age") int age) {
        return Person.where((Person p) -> p.age >= age).toList();
    }

    @GET
    @Path("/age-less-than/{age}")
    public List<Person> getAgeLessThan(@PathParam("age") int age) {
        return Person.where((Person p) -> p.age < age).toList();
    }

    @GET
    @Path("/age-less-or-equal/{age}")
    public List<Person> getAgeLessOrEqual(@PathParam("age") int age) {
        return Person.where((Person p) -> p.age <= age).toList();
    }

    @GET
    @Path("/age-not-equal/{age}")
    public List<Person> getAgeNotEqual(@PathParam("age") int age) {
        return Person.where((Person p) -> p.age != age).toList();
    }

    @GET
    @Path("/age-between/{min}/{max}")
    public List<Person> getAgeBetween(@PathParam("min") int min, @PathParam("max") int max) {
        return Person.where((Person p) -> p.age >= min && p.age <= max).toList();
    }

    // =============================================================================================
    // LOGICAL OPERATIONS - AND, OR, NOT
    // =============================================================================================

    @GET
    @Path("/active-and-age-over/{age}")
    public List<Person> getActiveAndAgeOver(@PathParam("age") int age) {
        return Person.where((Person p) -> p.active && p.age > age).toList();
    }

    @GET
    @Path("/active-or-age-over/{age}")
    public List<Person> getActiveOrAgeOver(@PathParam("age") int age) {
        return Person.where((Person p) -> p.active || p.age > age).toList();
    }

    @GET
    @Path("/complex-and")
    public List<Person> getComplexAnd() {
        // Three-condition AND: age >= 30, active, salary > 70000
        return Person.where((Person p) ->
            p.age >= 30 && p.active && p.salary != null && p.salary > 70000.0
        ).toList();
    }

    @GET
    @Path("/complex-or")
    public List<Person> getComplexOr() {
        // OR with multiple conditions
        return Person.where((Person p) ->
            p.age < 26 || p.age > 40 || !p.active
        ).toList();
    }

    @GET
    @Path("/complex-mixed")
    public List<Person> getComplexMixed() {
        // Mixed AND/OR: (active AND age > 30) OR salary > 80000
        return Person.where((Person p) ->
            (p.active && p.age > 30) || (p.salary != null && p.salary > 80000.0)
        ).toList();
    }

    // =============================================================================================
    // STRING OPERATIONS - startsWith, contains, endsWith
    // =============================================================================================

    @GET
    @Path("/first-name-starts-with/{prefix}")
    public List<Person> getFirstNameStartsWith(@PathParam("prefix") String prefix) {
        return Person.where((Person p) -> p.firstName.startsWith(prefix)).toList();
    }

    @GET
    @Path("/email-contains/{text}")
    public List<Person> getEmailContains(@PathParam("text") String text) {
        return Person.where((Person p) -> p.email.contains(text)).toList();
    }

    @GET
    @Path("/last-name-ends-with/{suffix}")
    public List<Person> getLastNameEndsWith(@PathParam("suffix") String suffix) {
        return Person.where((Person p) -> p.lastName.endsWith(suffix)).toList();
    }

    // =============================================================================================
    // SORTING - sortedBy, sortedDescendingBy
    // =============================================================================================

    @GET
    @Path("/sorted-by-age")
    public List<Person> getSortedByAge() {
        return Person.sortedBy((Person p) -> Integer.valueOf(p.age)).toList();
    }

    @GET
    @Path("/sorted-by-age-desc")
    public List<Person> getSortedByAgeDesc() {
        return Person.sortedDescendingBy((Person p) -> Integer.valueOf(p.age)).toList();
    }

    @GET
    @Path("/sorted-by-first-name")
    public List<Person> getSortedByFirstName() {
        return Person.sortedBy((Person p) -> p.firstName).toList();
    }

    @GET
    @Path("/sorted-by-salary-desc")
    public List<Person> getSortedBySalaryDesc() {
        return Person.where((Person p) -> p.salary != null)
                .sortedDescendingBy((Person p) -> p.salary)
                .toList();
    }

    // =============================================================================================
    // PAGINATION - skip, limit
    // =============================================================================================

    @GET
    @Path("/paginated/{offset}/{limit}")
    public List<Person> getPaginated(@PathParam("offset") int offset, @PathParam("limit") int limit) {
        return Person.sortedBy((Person p) -> Long.valueOf(p.id))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @GET
    @Path("/first/{count}")
    public List<Person> getFirst(@PathParam("count") int count) {
        return Person.sortedBy((Person p) -> Long.valueOf(p.id))
                .limit(count)
                .toList();
    }

    // =============================================================================================
    // PROJECTIONS - select
    // =============================================================================================

    @GET
    @Path("/first-names")
    public List<String> getFirstNames() {
        return Person.select((Person p) -> p.firstName).toList();
    }

    @GET
    @Path("/emails")
    public List<String> getEmails() {
        return Person.where((Person p) -> p.email != null && !p.email.isEmpty())
                .select((Person p) -> p.email)
                .toList();
    }

    @GET
    @Path("/ages")
    public List<Integer> getAges() {
        return Person.select((Person p) -> Integer.valueOf(p.age)).toList();
    }

    @GET
    @Path("/distinct-last-names")
    public List<String> getDistinctLastNames() {
        return Person.select((Person p) -> p.lastName)
                .distinct()
                .toList();
    }

    // =============================================================================================
    // AGGREGATIONS - min, max, avg, sum
    // Aggregations must use getSingleResult(), not findFirst() - findFirst() goes through toList()
    // =============================================================================================

    @GET
    @Path("/min-age")
    public Integer getMinAge() {
        return Person.min((Person p) -> p.age).getSingleResult();
    }

    @GET
    @Path("/max-age")
    public Integer getMaxAge() {
        return Person.max((Person p) -> p.age).getSingleResult();
    }

    @GET
    @Path("/avg-age")
    public Double getAvgAge() {
        return Person.avg((Person p) -> p.age).getSingleResult();
    }

    @GET
    @Path("/sum-age")
    public Long getSumAge() {
        return Person.sumInteger((Person p) -> p.age).getSingleResult();
    }

    @GET
    @Path("/avg-salary")
    public Double getAvgSalary() {
        return Person.where((Person p) -> p.salary != null)
                .avg((Person p) -> p.salary)
                .getSingleResult();
    }

    @GET
    @Path("/sum-salary")
    public Double getSumSalary() {
        return Person.where((Person p) -> p.salary != null)
                .sumDouble((Person p) -> p.salary)
                .getSingleResult();
    }

    // =============================================================================================
    // CHAINED OPERATIONS - combining where, sort, pagination
    // =============================================================================================

    @GET
    @Path("/active-sorted-by-age")
    public List<Person> getActiveSortedByAge() {
        return Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> Integer.valueOf(p.age))
                .toList();
    }

    @GET
    @Path("/active-top/{count}")
    public List<Person> getActiveTop(@PathParam("count") int count) {
        return Person.where((Person p) -> p.active)
                .sortedDescendingBy((Person p) -> p.salary)
                .limit(count)
                .toList();
    }

    @GET
    @Path("/active-age-range/{min}/{max}")
    public List<Person> getActiveInAgeRange(@PathParam("min") int min, @PathParam("max") int max) {
        return Person.where((Person p) -> p.active)
                .where((Person p) -> p.age >= min && p.age <= max)
                .sortedBy((Person p) -> Integer.valueOf(p.age))
                .toList();
    }

    // =============================================================================================
    // SINGLE RESULT OPERATIONS - findFirst, exists
    // =============================================================================================

    @GET
    @Path("/first-active")
    public Person getFirstActive() {
        return Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> Long.valueOf(p.id))
                .findFirst()
                .orElse(null);
    }

    @GET
    @Path("/exists-age/{age}")
    public boolean existsWithAge(@PathParam("age") int age) {
        return Person.where((Person p) -> p.age == age).exists();
    }

    @GET
    @Path("/exists-active")
    public boolean existsActive() {
        return Person.where((Person p) -> p.active).exists();
    }
}
