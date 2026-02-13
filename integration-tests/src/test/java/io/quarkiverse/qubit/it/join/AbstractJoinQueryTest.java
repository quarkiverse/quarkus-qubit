package io.quarkiverse.qubit.it.join;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.dto.PersonPhoneDTO;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for join query tests.
 * <p>
 * Tests join operations between Person and Phone entities using the fluent API.
 * The test data creates 5 persons with phones:
 * <ul>
 * <li>John: 2 phones (mobile, work)</li>
 * <li>Jane: 1 phone (mobile)</li>
 * <li>Bob: 3 phones (mobile, home, work)</li>
 * <li>Alice: 2 phones (mobile, work)</li>
 * <li>Charlie: 1 phone (mobile)</li>
 * </ul>
 */
public abstract class AbstractJoinQueryTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    @Test
    void basicJoinReturnsAllPersonsWithPhones() {
        var results = personOps().join((Person p) -> p.phones)
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void joinWithWhereOnJoinedEntity() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void joinWithWhereOnSpecificPhoneType() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    @Test
    void joinWithWhereOnHomePhone() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("home"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("Bob");
    }

    @Test
    void joinWithCapturedVariable() {
        String phoneType = "mobile";

        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals(phoneType))
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void joinWithCapturedPhoneNumber() {
        String phoneNumber = "555-0101";

        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.number.equals(phoneNumber))
                .toList();

        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("John");
    }

    @Test
    void joinWithWhereOnSourceEntity() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .where((Person p, Phone ph) -> p.active)
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(4)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Alice", "Charlie");
    }

    @Test
    void joinWithWhereOnBothEntities() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> p.age >= 30 && ph.type.equals("work"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    @Test
    void joinWithMultipleWhereClausesOnJoinedEntity() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .where((Person p, Phone ph) -> ph.number.startsWith("555-01"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("John");
    }

    @Test
    void joinCount() {
        long count = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .count();

        assertThat(count).isEqualTo(5);
    }

    @Test
    void joinExists() {
        boolean exists = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .exists();

        assertThat(exists).isTrue();
    }

    @Test
    void joinNotExists() {
        boolean exists = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("fax"))
                .exists();

        assertThat(exists).isFalse();
    }

    @Test
    void joinWithLimit() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void joinWithSkipAndLimit() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .skip(2)
                .limit(2)
                .toList();

        assertThat(results).hasSize(2);
    }

    @Test
    void joinDistinctWithEntities() {
        var results = personOps().join((Person p) -> p.phones)
                .toList();

        var resultsWithDistinct = personOps().join((Person p) -> p.phones)
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
        assertThat(resultsWithDistinct).hasSize(5);

        long count = personOps().join((Person p) -> p.phones).count();
        assertThat(count).isEqualTo(9);
    }

    @Test
    void joinWithStartsWith() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.number.startsWith("555-03"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("Bob");
    }

    @Test
    void joinWithContains() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.number.contains("02"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(4)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice");
    }

    @Test
    void joinWithPrimaryPhone() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.isPrimaryPhone)
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void joinWithNonPrimaryPhone() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> !ph.isPrimaryPhone)
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    @Test
    void selectJoinedReturnsPhones() {
        var phones = personOps().join((Person p) -> p.phones)
                .selectJoined()
                .toList();

        assertThat(phones).hasSize(9);
        assertThat(phones).allMatch(obj -> obj instanceof Phone);
    }

    @Test
    void selectJoinedWithWhere() {
        var phones = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .selectJoined()
                .toList();

        assertThat(phones)
                .hasSize(5)
                .allMatch(obj -> obj instanceof Phone)
                .extracting(obj -> ((Phone) obj).type)
                .containsOnly("mobile");
    }

    @Test
    void selectJoinedWithWhereOnWorkPhones() {
        var phones = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .selectJoined()
                .toList();

        assertThat(phones)
                .hasSize(3)
                .allMatch(obj -> obj instanceof Phone)
                .extracting(obj -> ((Phone) obj).type)
                .containsOnly("work");
    }

    @Test
    void selectJoinedWithLimit() {
        var phones = personOps().join((Person p) -> p.phones)
                .selectJoined()
                .limit(3)
                .toList();

        assertThat(phones).hasSize(3);
    }

    @Test
    void selectJoinedWithDistinct() {
        var phones = personOps().join((Person p) -> p.phones)
                .distinct()
                .selectJoined()
                .toList();

        assertThat(phones).hasSize(9);
    }

    @Test
    void selectWithBiQuerySpecReturnsProjectedDTO() {
        var results = personOps().join((Person p) -> p.phones)
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        assertThat(results).hasSize(9);
        assertThat(results).allMatch(obj -> obj instanceof PersonPhoneDTO);
    }

    @Test
    void selectWithBiQuerySpecAndWhere() {
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        assertThat(results)
                .hasSize(5)
                .allMatch(obj -> obj instanceof PersonPhoneDTO);
    }

    @Test
    void selectWithBiQuerySpecAndLimit() {
        var results = personOps().join((Person p) -> p.phones)
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void selectWithBiQuerySpecAndDistinct() {
        var results = personOps().join((Person p) -> p.phones)
                .distinct()
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        assertThat(results).hasSize(9);
    }

    @Test
    void selectWithBiQuerySpecExtractingScalarField() {
        var phoneNumbers = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .select((Person p, Phone ph) -> ph.number)
                .toList();

        assertThat(phoneNumbers)
                .hasSize(3)
                .allMatch(n -> n instanceof String);
    }

    @Test
    void leftJoinBasic() {
        var results = personOps().leftJoin((Person p) -> p.phones)
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void leftJoinWithWhere() {
        var results = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void leftJoinWithWhereOnWorkPhone() {
        var results = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    @Test
    void leftJoinWithCapturedVariable() {
        String phoneType = "home";

        var results = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals(phoneType))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("Bob");
    }

    @Test
    void leftJoinWithLimit() {
        var results = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void leftJoinWithSkipAndLimit() {
        var results = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .skip(2)
                .limit(2)
                .toList();

        assertThat(results).hasSize(2);
    }

    @Test
    void leftJoinCount() {
        long count = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .count();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void leftJoinExists() {
        boolean exists = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("home"))
                .exists();

        assertThat(exists).isTrue();
    }

    @Test
    void leftJoinNotExists() {
        boolean exists = personOps().leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("fax"))
                .exists();

        assertThat(exists).isFalse();
    }

    @Test
    void personPhoneDTO_gettersReturnCorrectValues() {
        var dtos = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> p.firstName.equals("John") && ph.type.equals("mobile"))
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        assertThat(dtos).hasSize(1);
        var dto = dtos.getFirst();
        assertThat(dto.getPersonName()).isEqualTo("John");
        assertThat(dto.getPhoneNumber()).isEqualTo("555-0101");
    }
}
