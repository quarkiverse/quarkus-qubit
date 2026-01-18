package io.quarkiverse.qubit.deployment.devui;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JpqlGenerator}.
 * Verifies JPQL string generation from LambdaExpression AST nodes.
 */
class JpqlGeneratorTest {

    static class Person {
        String name;
        int age;
        boolean active;
    }

    // Use simple class name string for tests (simulates a top-level entity class)
    private static final String PERSON_CLASS = "com.example.Person";

    @Nested
    @DisplayName("Simple queries")
    class SimpleQueries {

        @Test
        @DisplayName("generates SELECT e FROM Entity for null predicate and projection")
        void generateSelectAllQuery() {
            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e");
        }

        @Test
        @DisplayName("generates COUNT query")
        void generateCountQuery() {
            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, null, true);

            assertThat(jpql).isEqualTo("SELECT COUNT(e) FROM Person e");
        }
    }

    @Nested
    @DisplayName("WHERE clause generation")
    class WhereClause {

        @Test
        @DisplayName("generates simple equality predicate")
        void generateEqualityPredicate() {
            // e.name = 'John'
            LambdaExpression predicate = BinaryOp.eq(
                    new FieldAccess("name", String.class),
                    new Constant("John", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE e.name = 'John'");
        }

        @Test
        @DisplayName("generates greater than predicate")
        void generateGreaterThanPredicate() {
            // e.age > 18
            LambdaExpression predicate = BinaryOp.gt(
                    new FieldAccess("age", int.class),
                    new Constant(18, int.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE e.age > 18");
        }

        @Test
        @DisplayName("generates AND predicate")
        void generateAndPredicate() {
            // e.age > 18 AND e.active = TRUE
            LambdaExpression predicate = BinaryOp.and(
                    BinaryOp.gt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                    BinaryOp.eq(new FieldAccess("active", boolean.class), Constant.TRUE));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE (e.age > 18 AND e.active = TRUE)");
        }

        @Test
        @DisplayName("generates OR predicate")
        void generateOrPredicate() {
            // e.age < 18 OR e.age > 65
            LambdaExpression predicate = BinaryOp.or(
                    BinaryOp.lt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                    BinaryOp.gt(new FieldAccess("age", int.class), new Constant(65, int.class)));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE (e.age < 18 OR e.age > 65)");
        }

        @Test
        @DisplayName("generates NOT predicate")
        void generateNotPredicate() {
            // NOT e.active
            LambdaExpression predicate = UnaryOp.not(new FieldAccess("active", boolean.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE NOT e.active");
        }

        @Test
        @DisplayName("generates predicate with captured variable")
        void generatePredicateWithCapturedVariable() {
            // e.age > :param0
            LambdaExpression predicate = BinaryOp.gt(
                    new FieldAccess("age", int.class),
                    new CapturedVariable(0, int.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE e.age > :capturedVar0");
        }
    }

    @Nested
    @DisplayName("Path expressions")
    class PathExpressions {

        @Test
        @DisplayName("generates path expression with multiple segments")
        void generatePathExpression() {
            // e.department.name = 'Engineering'
            PathExpression path = new PathExpression(
                    java.util.List.of(
                            new PathSegment("department", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)),
                    String.class);

            LambdaExpression predicate = BinaryOp.eq(path, new Constant("Engineering", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE e.department.name = 'Engineering'");
        }
    }

    @Nested
    @DisplayName("Method calls")
    class MethodCalls {

        @Test
        @DisplayName("generates LIKE for startsWith")
        void generateStartsWith() {
            // e.name LIKE CONCAT(:param0, '%')
            LambdaExpression predicate = new MethodCall(
                    new FieldAccess("name", String.class),
                    "startsWith",
                    java.util.List.of(new CapturedVariable(0, String.class)),
                    boolean.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e WHERE e.name LIKE CONCAT(:capturedVar0, '%')");
        }

        @Test
        @DisplayName("generates UPPER for toUpperCase")
        void generateToUpperCase() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "toUpperCase",
                    java.util.List.of(),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).isEqualTo("SELECT UPPER(e.name) FROM Person e");
        }
    }

    @Nested
    @DisplayName("predicateToJpql method")
    class PredicateToJpql {

        @Test
        @DisplayName("returns null for null predicate")
        void returnsNullForNullPredicate() {
            String jpql = JpqlGenerator.predicateToJpql(null);

            assertThat(jpql).isNull();
        }

        @Test
        @DisplayName("returns predicate expression without SELECT/FROM")
        void returnsPredicateOnly() {
            LambdaExpression predicate = BinaryOp.eq(
                    new FieldAccess("name", String.class),
                    new Constant("John", String.class));

            String jpql = JpqlGenerator.predicateToJpql(predicate);

            assertThat(jpql).isEqualTo("e.name = 'John'");
        }
    }
}
