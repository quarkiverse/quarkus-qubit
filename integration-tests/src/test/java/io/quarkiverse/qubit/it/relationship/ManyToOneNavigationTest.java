package io.quarkiverse.qubit.it.relationship;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for @ManyToOne relationship navigation.
 * <p>
 * Tests the PathExpression functionality that allows navigation through
 * relationships at multiple levels:
 * <ul>
 *   <li>Two-level: {@code phone.owner.firstName}</li>
 *   <li>Three-level: {@code phone.owner.department.name}</li>
 * </ul>
 * <p>
 * Test data setup:
 * <ul>
 *   <li>John Doe: 2 phones (555-0101 mobile, 555-0102 work), Department: Engineering</li>
 *   <li>Jane Smith: 1 phone (555-0201 mobile), Department: Sales</li>
 *   <li>Bob Johnson: 3 phones (555-0301 mobile, 555-0302 home, 555-0303 work), Department: Human Resources</li>
 *   <li>Alice Williams: 2 phones (555-0401 mobile, 555-0402 work), Department: Engineering</li>
 *   <li>Charlie Brown: 1 phone (555-0501 mobile), Department: Sales</li>
 * </ul>
 * <p>
 * Departments:
 * <ul>
 *   <li>Engineering: code=ENG, budget=500000</li>
 *   <li>Sales: code=SLS, budget=300000</li>
 *   <li>Human Resources: code=HR, budget=150000</li>
 * </ul>
 */
@QuarkusTest
class ManyToOneNavigationTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    // ========== WHERE CLAUSE WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void whereByOwnerFirstName() {
        // Navigate through @ManyToOne: phone.owner.firstName
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("John")
        ).toList();

        assertThat(phones)
                .hasSize(2) // John has 2 phones
                .allMatch(ph -> ph.getOwner().getFirstName().equals("John"));
    }

    @Test
    void whereByOwnerLastName() {
        // Navigate through @ManyToOne: phone.owner.lastName
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.lastName.equals("Johnson")
        ).toList();

        assertThat(phones)
                .hasSize(3) // Bob Johnson has 3 phones
                .allMatch(ph -> ph.getOwner().getLastName().equals("Johnson"));
    }

    @Test
    void whereByOwnerAge() {
        // Navigate through @ManyToOne: phone.owner.age
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.age >= 40
        ).toList();

        assertThat(phones)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getOwner().getAge() >= 40);
    }

    @Test
    void whereByOwnerActive() {
        // Navigate through @ManyToOne: phone.owner.active
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.active
        ).toList();

        assertThat(phones)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getOwner().isActive());
    }

    @Test
    void whereByOwnerInactive() {
        // Navigate through @ManyToOne: phone.owner.active == false
        List<Phone> phones = Phone.where((Phone ph) ->
            !ph.owner.active
        ).toList();

        // Bob Johnson is inactive (active = false)
        assertThat(phones)
                .hasSize(3) // Bob has 3 phones
                .allMatch(ph -> !ph.getOwner().isActive());
    }

    @Test
    void whereByOwnerNumericComparison() {
        // Navigate through @ManyToOne with numeric comparison: phone.owner.age > 35
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.age > 35
        ).toList();

        assertThat(phones)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getOwner().getAge() > 35);
    }

    @Test
    void whereByOwnerEmail() {
        // Navigate through @ManyToOne with String pattern: phone.owner.email
        // Note: "john" appears in both john.doe@example.com AND bob.johnson@example.com
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.email.contains("john")
        ).toList();

        assertThat(phones)
                .hasSize(5) // John Doe (2 phones) + Bob Johnson (3 phones) both contain "john"
                .allMatch(ph -> ph.getOwner().getEmail().contains("john"));
    }

    @Test
    void whereByOwnerEmailStartsWith() {
        // Navigate through @ManyToOne with String startsWith: phone.owner.email
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.email.startsWith("jane")
        ).toList();

        assertThat(phones)
                .hasSize(1) // Jane Smith has 1 phone
                .allMatch(ph -> ph.getOwner().getEmail().startsWith("jane"));
    }

    // ========== COMBINED CONDITIONS WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void whereCombinedLocalAndRelationship() {
        // Combine local field and relationship navigation
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.type.equals("mobile") && ph.owner.firstName.equals("John")
        ).toList();

        assertThat(phones)
                .hasSize(1) // John's mobile phone
                .allMatch(ph -> ph.getType().equals("mobile"))
                .allMatch(ph -> ph.getOwner().getFirstName().equals("John"));
    }

    @Test
    void whereCombinedMultipleRelationshipFields() {
        // Multiple relationship field accesses in same predicate
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("Bob") && ph.owner.lastName.equals("Johnson")
        ).toList();

        assertThat(phones)
                .hasSize(3) // Bob Johnson has 3 phones
                .allMatch(ph -> ph.getOwner().getFirstName().equals("Bob"))
                .allMatch(ph -> ph.getOwner().getLastName().equals("Johnson"));
    }

    @Test
    void whereOrConditionWithRelationship() {
        // OR condition with relationship navigation
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("John") || ph.owner.firstName.equals("Jane")
        ).toList();

        assertThat(phones)
                .hasSize(3) // John has 2, Jane has 1
                .allMatch(ph ->
                    ph.getOwner().getFirstName().equals("John") ||
                    ph.getOwner().getFirstName().equals("Jane"));
    }

    @Test
    void whereComparisonWithRelationship() {
        // Comparison operators with relationship navigation
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.age > 30 && ph.owner.age < 50
        ).toList();

        assertThat(phones)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getOwner().getAge() > 30 && ph.getOwner().getAge() < 50);
    }

    // ========== SELECT/PROJECTION WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void selectOwnerFirstName() {
        // Project relationship field: phone.owner.firstName
        List<String> ownerNames = Phone.select((Phone ph) ->
            ph.owner.firstName
        ).toList();

        assertThat(ownerNames)
                .hasSize(9) // Total 9 phones
                .contains("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void selectOwnerLastName() {
        // Project relationship field: phone.owner.lastName
        List<String> lastNames = Phone.select((Phone ph) ->
            ph.owner.lastName
        ).toList();

        assertThat(lastNames)
                .hasSize(9) // Total 9 phones
                .contains("Doe", "Smith", "Johnson", "Williams", "Brown");
    }

    @Test
    void selectOwnerAge() {
        // Project numeric relationship field: phone.owner.age
        List<Integer> ages = Phone.select((Phone ph) ->
            ph.owner.age
        ).toList();

        assertThat(ages)
                .hasSize(9)
                .allMatch(age -> age >= 25 && age <= 50);
    }

    @Test
    void selectOwnerEmail() {
        // Project String relationship field: phone.owner.email
        List<String> emails = Phone.select((Phone ph) ->
            ph.owner.email
        ).toList();

        assertThat(emails)
                .hasSize(9)
                .allMatch(email -> email.contains("@example.com"));
    }

    @Test
    void selectWithWhereOnRelationship() {
        // Combine where and select with relationship navigation
        List<String> ownerNames = Phone
                .where((Phone ph) -> ph.type.equals("mobile"))
                .select((Phone ph) -> ph.owner.firstName)
                .toList();

        assertThat(ownerNames)
                .hasSize(5) // 5 mobile phones
                .contains("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void selectWithWhereOnMultipleFields() {
        // Complex query: filter by local + relationship, project relationship
        List<String> ownerLastNames = Phone
                .where((Phone ph) -> ph.type.equals("work") && ph.owner.active)
                .select((Phone ph) -> ph.owner.lastName)
                .toList();

        assertThat(ownerLastNames)
                .hasSizeGreaterThan(0);
    }

    // ========== SORTING WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void sortByOwnerFirstName() {
        // Sort by relationship field: phone.owner.firstName
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.firstName
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(ph -> ph.getOwner().getFirstName()));
    }

    @Test
    void sortByOwnerLastName() {
        // Sort by relationship field: phone.owner.lastName
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.lastName
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(ph -> ph.getOwner().getLastName()));
    }

    @Test
    void sortByOwnerAge() {
        // Sort by numeric relationship field: phone.owner.age
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.age
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(ph -> ph.getOwner().getAge()));
    }

    @Test
    void sortDescendingByOwnerAge() {
        // Sort descending by relationship field: phone.owner.age
        List<Phone> phones = Phone.sortedDescendingBy((Phone ph) ->
            ph.owner.age
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(
                    (Phone ph) -> ph.getOwner().getAge()).reversed());
    }

    @Test
    void sortByOwnerSalary() {
        // Sort by Double relationship field: phone.owner.salary
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.salary
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(ph -> ph.getOwner().getSalary()));
    }

    @Test
    void sortWithWhereOnRelationship() {
        // Combine where and sort with relationship navigation
        List<Phone> phones = Phone
                .where((Phone ph) -> ph.type.equals("mobile"))
                .sortedBy((Phone ph) -> ph.owner.firstName)
                .toList();

        assertThat(phones)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(ph -> ph.getOwner().getFirstName()));
    }

    // ========== COUNT/EXISTS WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void countByOwnerFirstName() {
        // Count with relationship navigation
        long count = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("Bob")
        ).count();

        assertThat(count).isEqualTo(3); // Bob has 3 phones
    }

    @Test
    void existsByOwnerFirstName() {
        // Exists with relationship navigation
        boolean exists = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("John")
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void notExistsByOwnerFirstName() {
        // Not exists with relationship navigation
        boolean exists = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("NonExistent")
        ).exists();

        assertThat(exists).isFalse();
    }

    // ========== PAGINATION WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void paginationWithRelationshipSort() {
        // Paginate results sorted by relationship field
        List<Phone> phones = Phone
                .sortedBy((Phone ph) -> ph.owner.firstName)
                .limit(3)
                .toList();

        assertThat(phones).hasSize(3);
    }

    @Test
    void paginationWithRelationshipWhere() {
        // Paginate filtered results with relationship navigation
        List<Phone> phones = Phone
                .where((Phone ph) -> ph.owner.active)
                .limit(2)
                .toList();

        assertThat(phones).hasSize(2);
    }

    // ========== DISTINCT WITH RELATIONSHIP NAVIGATION ==========

    @Test
    void distinctOwnerFirstNames() {
        // Distinct projection of relationship field
        List<String> distinctNames = Phone
                .select((Phone ph) -> ph.owner.firstName)
                .distinct()
                .toList();

        assertThat(distinctNames)
                .hasSize(5) // 5 distinct owners
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void distinctOwnerLastNames() {
        // Distinct projection of relationship field
        List<String> distinctLastNames = Phone
                .select((Phone ph) -> ph.owner.lastName)
                .distinct()
                .toList();

        assertThat(distinctLastNames)
                .hasSize(5) // 5 distinct owners
                .containsExactlyInAnyOrder("Doe", "Smith", "Johnson", "Williams", "Brown");
    }

    // ========== EDGE CASES ==========

    @Test
    void mobilePhoneOwnersOver30() {
        // Complex query combining multiple operations
        List<String> result = Phone
                .where((Phone ph) -> ph.type.equals("mobile") && ph.owner.age > 30)
                .select((Phone ph) -> ph.owner.firstName)
                .distinct()
                .toList();

        assertThat(result)
                .hasSizeGreaterThan(0)
                .allMatch(name -> {
                    Person person = Person.where((Person p) -> p.firstName.equals(name)).findFirst().orElse(null);
                    return person != null && person.getAge() > 30;
                });
    }

    @Test
    void workPhoneOwnerEmails() {
        // Query work phones and get owner emails
        List<String> emails = Phone
                .where((Phone ph) -> ph.type.equals("work"))
                .select((Phone ph) -> ph.owner.email)
                .toList();

        assertThat(emails)
                .hasSizeGreaterThan(0)
                .allMatch(email -> email.contains("@example.com"));
    }

    // ========== THREE-LEVEL RELATIONSHIP NAVIGATION (phone.owner.department.*) ==========

    @Test
    void whereByOwnerDepartmentName() {
        // Three-level navigation: phone.owner.department.name
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.name.equals("Engineering")
        ).toList();

        // John (2 phones) + Alice (2 phones) = 4 phones in Engineering
        assertThat(phones)
                .hasSize(4)
                .allMatch(ph -> ph.getOwner().getDepartment().getName().equals("Engineering"));
    }

    @Test
    void whereByOwnerDepartmentCode() {
        // Three-level navigation: phone.owner.department.code
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.code.equals("SLS")
        ).toList();

        // Jane (1 phone) + Charlie (1 phone) = 2 phones in Sales
        assertThat(phones)
                .hasSize(2)
                .allMatch(ph -> ph.getOwner().getDepartment().getCode().equals("SLS"));
    }

    @Test
    void whereByOwnerDepartmentBudget() {
        // Three-level navigation: phone.owner.department.budget
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.budget >= 300000
        ).toList();

        // Engineering (500k): 4 phones, Sales (300k): 2 phones = 6 phones
        assertThat(phones)
                .hasSize(6)
                .allMatch(ph -> ph.getOwner().getDepartment().getBudget() >= 300000);
    }

    @Test
    void whereByOwnerDepartmentBudgetLessThan() {
        // Three-level navigation: phone.owner.department.budget < threshold
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.budget < 200000
        ).toList();

        // HR (150k): Bob has 3 phones
        assertThat(phones)
                .hasSize(3)
                .allMatch(ph -> ph.getOwner().getDepartment().getBudget() < 200000);
    }

    @Test
    void whereByOwnerDepartmentNameStartsWith() {
        // Three-level navigation with string operations: phone.owner.department.name.startsWith()
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.name.startsWith("Human")
        ).toList();

        // Bob in HR has 3 phones
        assertThat(phones)
                .hasSize(3)
                .allMatch(ph -> ph.getOwner().getDepartment().getName().startsWith("Human"));
    }

    @Test
    void whereByOwnerDepartmentNameContains() {
        // Three-level navigation with string operations: phone.owner.department.name.contains()
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.name.contains("Sales")
        ).toList();

        // Jane + Charlie in Sales = 2 phones
        assertThat(phones)
                .hasSize(2)
                .allMatch(ph -> ph.getOwner().getDepartment().getName().contains("Sales"));
    }

    @Test
    void selectOwnerDepartmentName() {
        // Three-level projection: phone.owner.department.name
        List<String> departmentNames = Phone.select((Phone ph) ->
            ph.owner.department.name
        ).toList();

        assertThat(departmentNames)
                .hasSize(9) // Total 9 phones
                .contains("Engineering", "Sales", "Human Resources");
    }

    @Test
    void selectOwnerDepartmentCode() {
        // Three-level projection: phone.owner.department.code
        List<String> departmentCodes = Phone.select((Phone ph) ->
            ph.owner.department.code
        ).toList();

        assertThat(departmentCodes)
                .hasSize(9)
                .contains("ENG", "SLS", "HR");
    }

    @Test
    void selectOwnerDepartmentBudget() {
        // Three-level projection: phone.owner.department.budget
        List<Integer> budgets = Phone.select((Phone ph) ->
            ph.owner.department.budget
        ).toList();

        assertThat(budgets)
                .hasSize(9)
                .contains(500000, 300000, 150000);
    }

    @Test
    void selectDistinctOwnerDepartmentNames() {
        // Three-level projection with distinct: distinct(phone.owner.department.name)
        List<String> distinctDepts = Phone
                .select((Phone ph) -> ph.owner.department.name)
                .distinct()
                .toList();

        assertThat(distinctDepts)
                .hasSize(3) // 3 distinct departments
                .containsExactlyInAnyOrder("Engineering", "Sales", "Human Resources");
    }

    @Test
    void selectDistinctOwnerDepartmentCodes() {
        // Three-level projection with distinct: distinct(phone.owner.department.code)
        List<String> distinctCodes = Phone
                .select((Phone ph) -> ph.owner.department.code)
                .distinct()
                .toList();

        assertThat(distinctCodes)
                .hasSize(3)
                .containsExactlyInAnyOrder("ENG", "SLS", "HR");
    }

    @Test
    void sortByOwnerDepartmentName() {
        // Three-level sort: sortedBy(phone.owner.department.name)
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.department.name
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(
                    ph -> ph.getOwner().getDepartment().getName()));
    }

    @Test
    void sortByOwnerDepartmentCode() {
        // Three-level sort: sortedBy(phone.owner.department.code)
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.department.code
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(
                    ph -> ph.getOwner().getDepartment().getCode()));
    }

    @Test
    void sortByOwnerDepartmentBudget() {
        // Three-level sort: sortedBy(phone.owner.department.budget)
        List<Phone> phones = Phone.sortedBy((Phone ph) ->
            ph.owner.department.budget
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(
                    ph -> ph.getOwner().getDepartment().getBudget()));
    }

    @Test
    void sortDescendingByOwnerDepartmentBudget() {
        // Three-level sort descending: sortedDescendingBy(phone.owner.department.budget)
        List<Phone> phones = Phone.sortedDescendingBy((Phone ph) ->
            ph.owner.department.budget
        ).toList();

        assertThat(phones)
                .hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(
                    (Phone ph) -> ph.getOwner().getDepartment().getBudget()).reversed());
    }

    @Test
    void countByOwnerDepartmentName() {
        // Three-level count: count where phone.owner.department.name = 'Engineering'
        long count = Phone.where((Phone ph) ->
            ph.owner.department.name.equals("Engineering")
        ).count();

        // John (2) + Alice (2) = 4 phones in Engineering
        assertThat(count).isEqualTo(4);
    }

    @Test
    void existsByOwnerDepartmentCode() {
        // Three-level exists: exists where phone.owner.department.code = 'HR'
        boolean exists = Phone.where((Phone ph) ->
            ph.owner.department.code.equals("HR")
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void notExistsByOwnerDepartmentCode() {
        // Three-level not exists: not exists where phone.owner.department.code = 'INVALID'
        boolean exists = Phone.where((Phone ph) ->
            ph.owner.department.code.equals("INVALID")
        ).exists();

        assertThat(exists).isFalse();
    }

    // ========== COMBINED TWO AND THREE LEVEL NAVIGATION ==========

    @Test
    void combinedTwoAndThreeLevelWhereConditions() {
        // Combine two-level and three-level navigation in same predicate
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.firstName.equals("John") && ph.owner.department.name.equals("Engineering")
        ).toList();

        assertThat(phones)
                .hasSize(2) // John's 2 phones
                .allMatch(ph -> ph.getOwner().getFirstName().equals("John"))
                .allMatch(ph -> ph.getOwner().getDepartment().getName().equals("Engineering"));
    }

    @Test
    void combinedLocalTwoAndThreeLevelConditions() {
        // Combine local, two-level, and three-level in same predicate
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.type.equals("mobile") &&
            ph.owner.active &&
            ph.owner.department.budget > 200000
        ).toList();

        // Mobile phones of active users in departments with budget > 200k
        assertThat(phones)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("mobile"))
                .allMatch(ph -> ph.getOwner().isActive())
                .allMatch(ph -> ph.getOwner().getDepartment().getBudget() > 200000);
    }

    @Test
    void selectThreeLevelWithWhereOnTwoLevel() {
        // Select three-level field, filter on two-level field
        List<String> deptNames = Phone
                .where((Phone ph) -> ph.owner.age > 30)
                .select((Phone ph) -> ph.owner.department.name)
                .distinct()
                .toList();

        assertThat(deptNames)
                .hasSizeGreaterThan(0);
    }

    @Test
    void selectTwoLevelWithWhereOnThreeLevel() {
        // Select two-level field, filter on three-level field
        List<String> ownerNames = Phone
                .where((Phone ph) -> ph.owner.department.budget >= 300000)
                .select((Phone ph) -> ph.owner.firstName)
                .distinct()
                .toList();

        // Engineering (500k): John, Alice; Sales (300k): Jane, Charlie
        assertThat(ownerNames)
                .containsExactlyInAnyOrder("John", "Alice", "Jane", "Charlie");
    }

    @Test
    void complexThreeLevelQuery() {
        // Complex query: mobile phones of active users in Engineering, sorted by owner name
        List<Phone> phones = Phone
                .where((Phone ph) ->
                    ph.type.equals("mobile") &&
                    ph.owner.active &&
                    ph.owner.department.name.equals("Engineering")
                )
                .sortedBy((Phone ph) -> ph.owner.firstName)
                .toList();

        assertThat(phones)
                .hasSize(2) // John and Alice each have a mobile phone in Engineering
                .isSortedAccordingTo(Comparator.comparing(ph -> ph.getOwner().getFirstName()))
                .allMatch(ph -> ph.getType().equals("mobile"))
                .allMatch(ph -> ph.getOwner().isActive())
                .allMatch(ph -> ph.getOwner().getDepartment().getName().equals("Engineering"));
    }

    @Test
    void paginationWithThreeLevelSort() {
        // Paginate results sorted by three-level field
        List<Phone> phones = Phone
                .sortedBy((Phone ph) -> ph.owner.department.name)
                .limit(5)
                .toList();

        assertThat(phones).hasSize(5);
    }

    @Test
    void skipWithThreeLevelSort() {
        // Skip with three-level sort
        List<Phone> allPhones = Phone
                .sortedBy((Phone ph) -> ph.owner.department.name)
                .toList();

        List<Phone> skippedPhones = Phone
                .sortedBy((Phone ph) -> ph.owner.department.name)
                .skip(3)
                .toList();

        assertThat(skippedPhones).hasSize(allPhones.size() - 3);
    }

    @Test
    void orConditionWithThreeLevelNavigation() {
        // OR condition with three-level navigation
        List<Phone> phones = Phone.where((Phone ph) ->
            ph.owner.department.name.equals("Engineering") ||
            ph.owner.department.name.equals("Sales")
        ).toList();

        // Engineering: 4 phones, Sales: 2 phones = 6 phones
        assertThat(phones)
                .hasSize(6)
                .allMatch(ph -> {
                    String deptName = ph.getOwner().getDepartment().getName();
                    return deptName.equals("Engineering") || deptName.equals("Sales");
                });
    }
}
