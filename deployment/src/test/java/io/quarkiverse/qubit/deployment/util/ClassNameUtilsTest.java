package io.quarkiverse.qubit.deployment.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClassNameUtils}.
 */
@DisplayName("ClassNameUtils")
class ClassNameUtilsTest {

    @Nested
    @DisplayName("extractSimpleName method")
    class ExtractSimpleName {

        @Test
        @DisplayName("extracts simple name from fully qualified class name")
        void extractsSimpleNameFromFqcn() {
            assertThat(ClassNameUtils.extractSimpleName("com.example.model.Product"))
                    .isEqualTo("Product");
        }

        @Test
        @DisplayName("returns Entity for null input")
        void returnsEntityForNull() {
            assertThat(ClassNameUtils.extractSimpleName(null))
                    .isEqualTo("Entity");
        }

        @Test
        @DisplayName("returns Entity for empty input")
        void returnsEntityForEmpty() {
            assertThat(ClassNameUtils.extractSimpleName(""))
                    .isEqualTo("Entity");
        }

        @Test
        @DisplayName("handles simple class name without package")
        void handlesSimpleClassName() {
            assertThat(ClassNameUtils.extractSimpleName("Person"))
                    .isEqualTo("Person");
        }
    }

    @Nested
    @DisplayName("extractSimpleNameFromInternal method")
    class ExtractSimpleNameFromInternal {

        @Test
        @DisplayName("extracts simple name from JVM internal class name")
        void extractsFromInternalName() {
            assertThat(ClassNameUtils.extractSimpleNameFromInternal("com/example/model/Product"))
                    .isEqualTo("Product");
        }

        @Test
        @DisplayName("handles simple internal name without package")
        void handlesSimpleInternalName() {
            assertThat(ClassNameUtils.extractSimpleNameFromInternal("Person"))
                    .isEqualTo("Person");
        }

        @Test
        @DisplayName("returns Entity for null input")
        void returnsEntityForNull() {
            assertThat(ClassNameUtils.extractSimpleNameFromInternal(null))
                    .isEqualTo("Entity");
        }

        @Test
        @DisplayName("returns Entity for empty input")
        void returnsEntityForEmpty() {
            assertThat(ClassNameUtils.extractSimpleNameFromInternal(""))
                    .isEqualTo("Entity");
        }
    }
}
