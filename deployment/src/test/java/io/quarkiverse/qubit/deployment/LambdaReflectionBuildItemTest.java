package io.quarkiverse.qubit.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LambdaReflectionBuildItem}.
 */
@DisplayName("LambdaReflectionBuildItem")
class LambdaReflectionBuildItemTest {

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes declaring class")
        void includesDeclaringClass() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "io.quarkiverse.qubit.QuerySpec");

            String result = item.toString();

            assertThat(result).contains("com.example.MyClass");
        }

        @Test
        @DisplayName("includes interface type")
        void includesInterfaceType() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "io.quarkiverse.qubit.QuerySpec");

            String result = item.toString();

            assertThat(result).contains("io.quarkiverse.qubit.QuerySpec");
        }

        @Test
        @DisplayName("matches expected format")
        void matchesExpectedFormat() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "io.quarkiverse.qubit.QuerySpec");

            String result = item.toString();

            assertThat(result).isEqualTo(
                    "LambdaReflectionBuildItem{com.example.MyClass, " +
                            "interface=io.quarkiverse.qubit.QuerySpec}");
        }
    }
}
