package io.quarkiverse.qubit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Unit tests for {@link FieldNamingStrategy} implementations.
 */
@DisplayName("FieldNamingStrategy")
class FieldNamingStrategyTest {

    // Test class with fields matching javac naming convention
    @SuppressWarnings("unused")
    static class JavacStyleClass {
        private String arg$1;
        private int arg$2;
        private Object arg$3;
    }

    // Test class with fields matching Eclipse naming convention
    @SuppressWarnings("unused")
    static class EclipseStyleClass {
        private String val$1;
        private int val$2;
    }

    // Test class with fields matching GraalVM naming convention
    @SuppressWarnings("unused")
    static class GraalVMStyleClass {
        private String arg0;
        private int arg1;
    }

    // Test class with no captured variable fields
    @SuppressWarnings("unused")
    static class NoFieldsClass {
        private String regularField;
    }

    @Nested
    @DisplayName("JavacStrategy")
    class JavacStrategyTests {

        private final FieldNamingStrategy strategy = new FieldNamingStrategy.JavacStrategy();

        @Test
        @DisplayName("finds field at index 0 (arg$1)")
        void findsFieldAtIndex0() {
            Optional<Field> field = strategy.findCapturedField(JavacStyleClass.class, 0);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("arg$1");
        }

        @Test
        @DisplayName("finds field at index 1 (arg$2)")
        void findsFieldAtIndex1() {
            Optional<Field> field = strategy.findCapturedField(JavacStyleClass.class, 1);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("arg$2");
        }

        @Test
        @DisplayName("finds field at index 2 (arg$3)")
        void findsFieldAtIndex2() {
            Optional<Field> field = strategy.findCapturedField(JavacStyleClass.class, 2);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("arg$3");
        }

        @Test
        @DisplayName("returns empty for non-existent index")
        void returnsEmptyForNonExistentIndex() {
            Optional<Field> field = strategy.findCapturedField(JavacStyleClass.class, 10);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("returns empty for class without javac fields")
        void returnsEmptyForNonJavacClass() {
            Optional<Field> field = strategy.findCapturedField(NoFieldsClass.class, 0);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("strategy name is correct")
        void strategyNameIsCorrect() {
            assertThat(strategy.getStrategyName()).isEqualTo("Javac (arg$N)");
        }

        @Test
        @DisplayName("field is made accessible")
        void fieldIsMadeAccessible() {
            Optional<Field> field = strategy.findCapturedField(JavacStyleClass.class, 0);

            assertThat(field).isPresent();
            assertThat(field.get().canAccess(new JavacStyleClass())).isTrue();
        }
    }

    @Nested
    @DisplayName("EclipseStrategy")
    class EclipseStrategyTests {

        private final FieldNamingStrategy strategy = new FieldNamingStrategy.EclipseStrategy();

        @Test
        @DisplayName("finds field at index 0 (val$1)")
        void findsFieldAtIndex0() {
            Optional<Field> field = strategy.findCapturedField(EclipseStyleClass.class, 0);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("val$1");
        }

        @Test
        @DisplayName("finds field at index 1 (val$2)")
        void findsFieldAtIndex1() {
            Optional<Field> field = strategy.findCapturedField(EclipseStyleClass.class, 1);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("val$2");
        }

        @Test
        @DisplayName("returns empty for non-existent index")
        void returnsEmptyForNonExistentIndex() {
            Optional<Field> field = strategy.findCapturedField(EclipseStyleClass.class, 10);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("strategy name is correct")
        void strategyNameIsCorrect() {
            assertThat(strategy.getStrategyName()).isEqualTo("Eclipse (val$N)");
        }
    }

    @Nested
    @DisplayName("GraalVMStrategy")
    class GraalVMStrategyTests {

        private final FieldNamingStrategy strategy = new FieldNamingStrategy.GraalVMStrategy();

        @Test
        @DisplayName("finds field at index 0 (arg0)")
        void findsFieldAtIndex0() {
            Optional<Field> field = strategy.findCapturedField(GraalVMStyleClass.class, 0);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("arg0");
        }

        @Test
        @DisplayName("finds field at index 1 (arg1)")
        void findsFieldAtIndex1() {
            Optional<Field> field = strategy.findCapturedField(GraalVMStyleClass.class, 1);

            assertThat(field).isPresent();
            assertThat(field.get().getName()).isEqualTo("arg1");
        }

        @Test
        @DisplayName("returns empty for non-existent index")
        void returnsEmptyForNonExistentIndex() {
            Optional<Field> field = strategy.findCapturedField(GraalVMStyleClass.class, 10);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("strategy name is correct")
        void strategyNameIsCorrect() {
            assertThat(strategy.getStrategyName()).isEqualTo("GraalVM (argN)");
        }
    }

    @Nested
    @DisplayName("IndexBasedStrategy")
    class IndexBasedStrategyTests {

        private final FieldNamingStrategy strategy = new FieldNamingStrategy.IndexBasedStrategy();

        @Test
        @DisplayName("always returns empty (disabled for reliability)")
        void alwaysReturnsEmpty() {
            Optional<Field> field = strategy.findCapturedField(JavacStyleClass.class, 0);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("strategy name indicates disabled status")
        void strategyNameIndicatesDisabled() {
            assertThat(strategy.getStrategyName())
                    .contains("DISABLED")
                    .contains("Index-based");
        }
    }

    @Nested
    @DisplayName("Strategy Independence")
    class StrategyIndependenceTests {

        @Test
        @DisplayName("javac strategy does not find eclipse fields")
        void javacDoesNotFindEclipseFields() {
            FieldNamingStrategy javacStrategy = new FieldNamingStrategy.JavacStrategy();
            Optional<Field> field = javacStrategy.findCapturedField(EclipseStyleClass.class, 0);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("eclipse strategy does not find javac fields")
        void eclipseDoesNotFindJavacFields() {
            FieldNamingStrategy eclipseStrategy = new FieldNamingStrategy.EclipseStrategy();
            Optional<Field> field = eclipseStrategy.findCapturedField(JavacStyleClass.class, 0);

            assertThat(field).isEmpty();
        }

        @Test
        @DisplayName("graalvm strategy does not find javac fields")
        void graalvmDoesNotFindJavacFields() {
            FieldNamingStrategy graalvmStrategy = new FieldNamingStrategy.GraalVMStrategy();
            Optional<Field> field = graalvmStrategy.findCapturedField(JavacStyleClass.class, 0);

            assertThat(field).isEmpty();
        }
    }
}
