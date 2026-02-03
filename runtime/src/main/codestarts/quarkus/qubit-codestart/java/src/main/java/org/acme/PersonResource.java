package org.acme;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import java.util.List;

/**
 * REST resource demonstrating Qubit lambda-based queries.
 */
@Path("/persons")
public class PersonResource {

    /**
     * Get all persons.
     */
    @GET
    public List<Person> list() {
        return Person.listAll();
    }

    /**
     * Find persons older than the specified age using Qubit lambda syntax.
     *
     * <p>Example: GET /persons/adults?minAge=18
     */
    @GET
    @Path("/adults")
    public List<Person> findAdults(@QueryParam("minAge") int minAge) {
        return Person.where((Person p) -> p.age >= minAge).toList();
    }

    /**
     * Find active persons by name prefix using Qubit lambda syntax.
     *
     * <p>Example: GET /persons/search/Jo
     */
    @GET
    @Path("/search/{prefix}")
    public List<Person> searchByName(@PathParam("prefix") String prefix) {
        return Person.where((Person p) -> p.active && p.name.startsWith(prefix))
                     .sortedBy((Person p) -> p.name)
                     .toList();
    }

    /**
     * Count adults using Qubit lambda syntax.
     */
    @GET
    @Path("/count-adults")
    public long countAdults() {
        return Person.where((Person p) -> p.age >= 18).count();
    }

    /**
     * Create a new person.
     */
    @POST
    @Transactional
    public Person create(Person person) {
        person.persist();
        return person;
    }
}
