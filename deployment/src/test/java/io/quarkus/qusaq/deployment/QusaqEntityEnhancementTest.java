package io.quarkus.qusaq.deployment;

import io.quarkus.qusaq.runtime.QusaqEntity;
import io.quarkus.qusaq.runtime.QuerySpec;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Entity;
import jakarta.transaction.Transactional;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Qusaq bytecode enhancement successfully injects query methods
 * into Panache entities.
 *
 * <p><strong>IMPORTANT LIMITATION</strong>: Even with {@link QuarkusUnitTest}
 * and {@link RegisterExtension}, we CANNOT directly call the enhanced methods
 * in test code like {@code TestPerson.findWhere(p -> ...)} because:
 *
 * <ol>
 *   <li>Java compilation happens FIRST (test code compiled)</li>
 *   <li>Bytecode enhancement happens LATER (QuarkusUnitTest runtime)</li>
 *   <li>Compiler fails because {@code TestPerson.findWhere()} doesn't exist yet</li>
 * </ol>
 *
 * <p><strong>SOLUTION</strong>: Tests must use {@link QusaqEntity} utility class:
 * <pre>{@code
 * // In tests: Use QusaqEntity utility class
 * List<TestPerson> results = QusaqEntity.findWhere(TestPerson.class, p -> p.age >= 30);
 *
 * // In application code: Enhanced methods are available at runtime
 * List<TestPerson> results = TestPerson.findWhere(p -> p.age >= 30);
 * }</pre>
 *
 * <p>This test verifies:
 * <ul>
 *   <li>Bytecode enhancement succeeds (via reflection)</li>
 *   <li>Enhanced methods work at runtime (via reflection)</li>
 *   <li>QusaqEntity utility class works in tests</li>
 * </ul>
 */
class QusaqEntityEnhancementTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestPerson.class)
                    .addAsResource("application.properties"));

    @Inject
    EntityManager em;

    /**
     * Clean up database after each test to prevent data accumulation.
     * QuarkusUnitTest shares the same H2 database across tests, so we need
     * explicit cleanup to ensure test isolation.
     */
    @AfterEach
    @Transactional
    void cleanup() {
        TestPerson.deleteAll();
    }

    @Entity
    public static class TestPerson extends QusaqEntity {
        public String name;
        public int age;
        public boolean active;

        public TestPerson() {
        }

        public TestPerson(String name, int age, boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }
    }

    /**
     * Verifies that bytecode enhancement successfully injected the query methods.
     * Uses reflection because we can't call them directly at compile time.
     */
    @Test
    void testEnhancedMethodsExistViaReflection() {
        // Verify findWhere method exists
        Method findWhere = findMethod(TestPerson.class, "findWhere", QuerySpec.class);
        assertThat(findWhere).isNotNull();
        assertThat(findWhere.getReturnType()).isEqualTo(List.class);

        // Verify countWhere method exists
        Method countWhere = findMethod(TestPerson.class, "countWhere", QuerySpec.class);
        assertThat(countWhere).isNotNull();
        assertThat(countWhere.getReturnType()).isEqualTo(long.class);

        // Verify exists method exists
        Method exists = findMethod(TestPerson.class, "exists", QuerySpec.class);
        assertThat(exists).isNotNull();
        assertThat(exists.getReturnType()).isEqualTo(boolean.class);
    }

    /**
     * Demonstrates that tests MUST use QusaqEntity utility class.
     * This is the recommended approach for testing.
     */
    @Test
    @Transactional
    void testUsingQusaqEntityUtilityClass() {
        // Setup test data
        TestPerson john = new TestPerson("John", 30, true);
        TestPerson jane = new TestPerson("Jane", 25, true);
        TestPerson bob = new TestPerson("Bob", 45, false);

        john.persist();
        jane.persist();
        bob.persist();

        em.flush();

        List<TestPerson> adults = TestPerson.findWhere( p -> p.age >= 30);

        assertThat(adults)
                .hasSize(2)
                .extracting(p -> p.name)
                .containsExactlyInAnyOrder("John", "Bob");
    }

    @Test
    @Transactional
    void testCountWhereUsingUtilityClass() {
        new TestPerson("John", 30, true).persist();
        new TestPerson("Jane", 25, true).persist();
        new TestPerson("Bob", 45, false).persist();

        em.flush();

        long activeCount = TestPerson.countWhere( (TestPerson p) -> p.active);

        assertThat(activeCount).isEqualTo(2);
    }

    @Test
    @Transactional
    void testExistsUsingUtilityClass() {
        new TestPerson("John", 30, true).persist();

        em.flush();

        boolean exists = TestPerson.exists( (TestPerson p) -> p.name.equals("John"));
        boolean notExists = TestPerson.exists( (TestPerson p) -> p.name.equals("NonExistent"));

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Transactional
    void testComplexQueryUsingUtilityClass() {
        new TestPerson("John", 30, true).persist();
        new TestPerson("Jane", 25, true).persist();
        new TestPerson("Bob", 45, false).persist();
        new TestPerson("Alice", 35, true).persist();

        em.flush();

        List<TestPerson> results = TestPerson.findWhere( p -> p.age > 25 && p.active);

        assertThat(results)
                .hasSize(2)
                .extracting(p -> p.name)
                .containsExactlyInAnyOrder("John", "Alice");
    }

    /**
     * Helper method to find a method by name and parameter types.
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
