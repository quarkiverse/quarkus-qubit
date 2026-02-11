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
        @DisplayName("includes declaring class and method")
        void includesDeclaringClassAndMethod() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "findUsers",
                    "io.quarkiverse.qubit.QuerySpec",
                    2);

            String result = item.toString();

            assertThat(result)
                    .contains("com.example.MyClass")
                    .contains("findUsers");
        }

        @Test
        @DisplayName("includes interface type")
        void includesInterfaceType() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "findUsers",
                    "io.quarkiverse.qubit.QuerySpec",
                    2);

            String result = item.toString();

            assertThat(result).contains("io.quarkiverse.qubit.QuerySpec");
        }

        @Test
        @DisplayName("includes captured variable count")
        void includesCapturedVarCount() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "findUsers",
                    "io.quarkiverse.qubit.QuerySpec",
                    5);

            String result = item.toString();

            assertThat(result).contains("5");
        }

        @Test
        @DisplayName("matches expected format")
        void matchesExpectedFormat() {
            LambdaReflectionBuildItem item = new LambdaReflectionBuildItem(
                    "com.example.MyClass",
                    "findUsers",
                    "io.quarkiverse.qubit.QuerySpec",
                    2);

            String result = item.toString();

            assertThat(result).isEqualTo(
                    "LambdaReflectionBuildItem{com.example.MyClass.findUsers, " +
                            "interface=io.quarkiverse.qubit.QuerySpec, capturedVars=2}");
        }
    }
}
