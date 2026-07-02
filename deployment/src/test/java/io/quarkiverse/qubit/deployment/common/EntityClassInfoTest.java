package io.quarkiverse.qubit.deployment.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EntityClassInfo}.
 *
 * <p>
 * Tests entity class information record including placeholder detection
 * and effective class name computation.
 */
class EntityClassInfoTest {

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

    @Nested
    class IsPlaceholderTests {

        @Test
        void isPlaceholder_withNullClassName_returnsFalse() {
            EntityClassInfo info = EntityClassInfo.of(String.class);

            EntityClassInfoAssert.assertThat(info)
                    .as("Non-placeholder should return false")
                    .isNotPlaceholder();
        }

        @Test
        void isPlaceholder_withClassName_returnsTrue() {
            EntityClassInfo info = EntityClassInfo.placeholder("com.example.Entity");

            EntityClassInfoAssert.assertThat(info)
                    .as("Placeholder should return true")
                    .isPlaceholder();
        }

        @Test
        void isPlaceholder_withConstructorCreatedPlaceholder_returnsTrue() {
            EntityClassInfo info = new EntityClassInfo(Object.class, "com.test.MyClass");

            EntityClassInfoAssert.assertThat(info)
                    .as("Constructor-created placeholder should return true")
                    .isPlaceholder();
        }

        @Test
        void isPlaceholder_withConstructorCreatedNonPlaceholder_returnsFalse() {
            EntityClassInfo info = new EntityClassInfo(Long.class, null);

            EntityClassInfoAssert.assertThat(info)
                    .as("Constructor-created non-placeholder should return false")
                    .isNotPlaceholder();
        }
    }

    @Nested
    class GetEffectiveClassNameTests {

        @Test
        void getEffectiveClassName_withLoadedClass_returnsClassName() {
            EntityClassInfo info = EntityClassInfo.of(String.class);

            EntityClassInfoAssert.assertThat(info)
                    .as("Should return loaded class name")
                    .hasEffectiveClassName("java.lang.String");
        }

        @Test
        void getEffectiveClassName_withIntegerClass_returnsClassName() {
            EntityClassInfo info = EntityClassInfo.of(Integer.class);

            EntityClassInfoAssert.assertThat(info)
                    .as("Should return Integer class name")
                    .hasEffectiveClassName("java.lang.Integer");
        }

        @Test
        void getEffectiveClassName_withPlaceholder_returnsPlaceholderClassName() {
            EntityClassInfo info = EntityClassInfo.placeholder("com.example.UnloadableEntity");

            EntityClassInfoAssert.assertThat(info)
                    .as("Should return placeholder className")
                    .hasEffectiveClassName("com.example.UnloadableEntity");
        }

        @Test
        void getEffectiveClassName_withPlaceholderOverridesClass_returnsPlaceholderClassName() {
            // Even though Object.class has a name, className takes precedence
            EntityClassInfo info = new EntityClassInfo(Object.class, "com.specific.Entity");

            EntityClassInfoAssert.assertThat(info)
                    .as("Should prefer className over class.getName()")
                    .hasEffectiveClassName("com.specific.Entity");
        }

        @Test
        void getEffectiveClassName_withCustomProjectClass_returnsFullyQualifiedName() {
            EntityClassInfo info = EntityClassInfo.of(EntityClassInfo.class);

            EntityClassInfoAssert.assertThat(info)
                    .as("Should return fully qualified project class name")
                    .hasEffectiveClassName("io.quarkiverse.qubit.deployment.common.EntityClassInfo");
        }
    }
}
