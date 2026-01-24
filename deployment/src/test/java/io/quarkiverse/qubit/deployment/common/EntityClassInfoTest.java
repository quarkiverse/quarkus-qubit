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
}
