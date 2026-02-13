package io.quarkiverse.qubit.it.repository.aggregation;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.aggregation.AbstractAggregationTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for aggregation queries.
 */
@QuarkusTest
class RepositoryAggregationIT extends AbstractAggregationTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }

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
