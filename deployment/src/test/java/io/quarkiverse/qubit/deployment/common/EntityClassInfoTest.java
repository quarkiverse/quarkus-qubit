package io.quarkiverse.qubit.deployment.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EntityClassInfo}.
 *
 * <p>Tests entity class information record including placeholder detection
 * and effective class name computation.
 */
class EntityClassInfoTest {

    // ==================== Constructor Tests ====================

    @Nested
    class ConstructorTests {

        @Test
        void constructor_withClassAndNullClassName_createsInfo() {
            EntityClassInfo info = new EntityClassInfo(String.class, null);

            assertThat(info.clazz())
                    .as("Class should be preserved")
                    .isEqualTo(String.class);
            assertThat(info.className())
                    .as("className should be null")
                    .isNull();
        }

        @Test
        void constructor_withObjectClassAndClassName_createsPlaceholder() {
            EntityClassInfo info = new EntityClassInfo(Object.class, "com.example.Entity");

            assertThat(info.clazz())
                    .as("Placeholder class should be Object.class")
                    .isEqualTo(Object.class);
            assertThat(info.className())
                    .as("className should be set")
                    .isEqualTo("com.example.Entity");
        }
    }

    // ==================== Factory Method: of Tests ====================

    @Nested
    class OfFactoryMethodTests {

        @Test
        void of_withClass_createsInfoWithNullClassName() {
            EntityClassInfo info = EntityClassInfo.of(String.class);

            assertThat(info.clazz())
                    .as("Class should be String.class")
                    .isEqualTo(String.class);
            assertThat(info.className())
                    .as("className should be null")
                    .isNull();
        }

        @Test
        void of_withIntegerClass_createsInfo() {
            EntityClassInfo info = EntityClassInfo.of(Integer.class);

            assertThat(info.clazz())
                    .as("Class should be Integer.class")
                    .isEqualTo(Integer.class);
            assertThat(info.className()).isNull();
        }

        @Test
        void of_withCustomClass_createsInfo() {
            EntityClassInfo info = EntityClassInfo.of(EntityClassInfo.class);

            assertThat(info.clazz())
                    .as("Class should be EntityClassInfo.class")
                    .isEqualTo(EntityClassInfo.class);
            assertThat(info.className()).isNull();
        }
    }

    // ==================== Factory Method: placeholder Tests ====================

    @Nested
    class PlaceholderFactoryMethodTests {

        @Test
        void placeholder_withClassName_createsPlaceholderWithObjectClass() {
            EntityClassInfo info = EntityClassInfo.placeholder("com.example.Entity");

            assertThat(info.clazz())
                    .as("Placeholder should use Object.class")
                    .isEqualTo(Object.class);
            assertThat(info.className())
                    .as("className should be set")
                    .isEqualTo("com.example.Entity");
        }

        @Test
        void placeholder_withDifferentClassName_createsPlaceholder() {
            EntityClassInfo info = EntityClassInfo.placeholder("org.domain.MyEntity");

            assertThat(info.clazz()).isEqualTo(Object.class);
            assertThat(info.className()).isEqualTo("org.domain.MyEntity");
        }
    }

    // ==================== isPlaceholder Tests ====================

    @Nested
    class IsPlaceholderTests {

        @Test
        void isPlaceholder_withNullClassName_returnsFalse() {
            EntityClassInfo info = EntityClassInfo.of(String.class);

            assertThat(info.isPlaceholder())
                    .as("Non-placeholder should return false")
                    .isFalse();
        }

        @Test
        void isPlaceholder_withClassName_returnsTrue() {
            EntityClassInfo info = EntityClassInfo.placeholder("com.example.Entity");

            assertThat(info.isPlaceholder())
                    .as("Placeholder should return true")
                    .isTrue();
        }

        @Test
        void isPlaceholder_withConstructorCreatedPlaceholder_returnsTrue() {
            EntityClassInfo info = new EntityClassInfo(Object.class, "com.test.MyClass");

            assertThat(info.isPlaceholder())
                    .as("Constructor-created placeholder should return true")
                    .isTrue();
        }

        @Test
        void isPlaceholder_withConstructorCreatedNonPlaceholder_returnsFalse() {
            EntityClassInfo info = new EntityClassInfo(Long.class, null);

            assertThat(info.isPlaceholder())
                    .as("Constructor-created non-placeholder should return false")
                    .isFalse();
        }
    }

    // ==================== getEffectiveClassName Tests ====================

    @Nested
    class GetEffectiveClassNameTests {

        @Test
        void getEffectiveClassName_withLoadedClass_returnsClassName() {
            EntityClassInfo info = EntityClassInfo.of(String.class);

            assertThat(info.getEffectiveClassName())
                    .as("Should return loaded class name")
                    .isEqualTo("java.lang.String");
        }

        @Test
        void getEffectiveClassName_withIntegerClass_returnsClassName() {
            EntityClassInfo info = EntityClassInfo.of(Integer.class);

            assertThat(info.getEffectiveClassName())
                    .as("Should return Integer class name")
                    .isEqualTo("java.lang.Integer");
        }

        @Test
        void getEffectiveClassName_withPlaceholder_returnsPlaceholderClassName() {
            EntityClassInfo info = EntityClassInfo.placeholder("com.example.UnloadableEntity");

            assertThat(info.getEffectiveClassName())
                    .as("Should return placeholder className")
                    .isEqualTo("com.example.UnloadableEntity");
        }

        @Test
        void getEffectiveClassName_withPlaceholderOverridesClass_returnsPlaceholderClassName() {
            // Even though Object.class has a name, className takes precedence
            EntityClassInfo info = new EntityClassInfo(Object.class, "com.specific.Entity");

            assertThat(info.getEffectiveClassName())
                    .as("Should prefer className over class.getName()")
                    .isEqualTo("com.specific.Entity");
        }

        @Test
        void getEffectiveClassName_withCustomProjectClass_returnsFullyQualifiedName() {
            EntityClassInfo info = EntityClassInfo.of(EntityClassInfo.class);

            assertThat(info.getEffectiveClassName())
                    .as("Should return fully qualified project class name")
                    .isEqualTo("io.quarkiverse.qubit.deployment.common.EntityClassInfo");
        }
    }

    // ==================== Record Equality Tests ====================

    @Nested
    class RecordEqualityTests {

        @Test
        void equals_withSameValues_returnsTrue() {
            EntityClassInfo info1 = EntityClassInfo.of(String.class);
            EntityClassInfo info2 = EntityClassInfo.of(String.class);

            assertThat(info1)
                    .as("Same values should be equal")
                    .isEqualTo(info2);
        }

        @Test
        void equals_withDifferentClass_returnsFalse() {
            EntityClassInfo info1 = EntityClassInfo.of(String.class);
            EntityClassInfo info2 = EntityClassInfo.of(Integer.class);

            assertThat(info1)
                    .as("Different classes should not be equal")
                    .isNotEqualTo(info2);
        }

        @Test
        void equals_withDifferentClassName_returnsFalse() {
            EntityClassInfo info1 = EntityClassInfo.placeholder("com.example.A");
            EntityClassInfo info2 = EntityClassInfo.placeholder("com.example.B");

            assertThat(info1)
                    .as("Different classNames should not be equal")
                    .isNotEqualTo(info2);
        }

        @Test
        void hashCode_withSameValues_returnsSameHash() {
            EntityClassInfo info1 = EntityClassInfo.of(String.class);
            EntityClassInfo info2 = EntityClassInfo.of(String.class);

            assertThat(info1.hashCode())
                    .as("Same values should have same hashCode")
                    .isEqualTo(info2.hashCode());
        }
    }
}
