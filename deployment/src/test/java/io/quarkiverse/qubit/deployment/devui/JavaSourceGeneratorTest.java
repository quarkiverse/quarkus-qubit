package io.quarkiverse.qubit.deployment.devui;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JavaSourceGenerator}.
 * Verifies Java source code generation from LambdaExpression AST nodes.
 */
class JavaSourceGeneratorTest {

    @Nested
    @DisplayName("Simple expressions")
    class SimpleExpressions {

        @Test
        @DisplayName("generates null for null input")
        void returnsNullForNull() {
            assertThat(JavaSourceGenerator.generateJavaSource(null)).isNull();
        }

        @Test
        @DisplayName("generates field access")
        void generateFieldAccess() {
            LambdaExpression expr = new FieldAccess("name", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.name");
        }

        @Test
        @DisplayName("generates constant string")
        void generateConstantString() {
            LambdaExpression expr = new Constant("hello", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> \"hello\"");
        }

        @Test
        @DisplayName("generates constant number")
        void generateConstantNumber() {
            LambdaExpression expr = new Constant(42, int.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> 42");
        }

        @Test
        @DisplayName("generates boolean constant")
        void generateBooleanConstant() {
            LambdaExpression expr = Constant.TRUE;

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> true");
        }
    }

    @Nested
    @DisplayName("Binary operations")
    class BinaryOperations {

        @Test
        @DisplayName("generates equality comparison with .equals() for strings")
        void generateEquality() {
            LambdaExpression expr = BinaryOp.eq(
                    new FieldAccess("name", String.class),
                    new Constant("John", String.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            // String comparisons should use .equals() instead of ==
            assertThat(java).isEqualTo("p -> p.name.equals(\"John\")");
        }

        @Test
        @DisplayName("generates greater-than comparison")
        void generateGreaterThan() {
            LambdaExpression expr = BinaryOp.gt(
                    new FieldAccess("age", int.class),
                    new Constant(18, int.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.age > 18");
        }

        @Test
        @DisplayName("generates AND with parentheses")
        void generateAnd() {
            LambdaExpression expr = BinaryOp.and(
                    BinaryOp.gt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                    new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> (p.age > 18 && p.active)");
        }

        @Test
        @DisplayName("generates OR with parentheses")
        void generateOr() {
            LambdaExpression expr = BinaryOp.or(
                    BinaryOp.lt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                    BinaryOp.gt(new FieldAccess("age", int.class), new Constant(65, int.class)));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> (p.age < 18 || p.age > 65)");
        }

        @Test
        @DisplayName("generates arithmetic operations")
        void generateArithmetic() {
            LambdaExpression expr = BinaryOp.add(
                    new FieldAccess("price", double.class),
                    new FieldAccess("tax", double.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.price + p.tax");
        }
    }

    @Nested
    @DisplayName("Unary operations")
    class UnaryOperations {

        @Test
        @DisplayName("generates NOT operation")
        void generateNot() {
            LambdaExpression expr = UnaryOp.not(new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !p.active");
        }
    }

    @Nested
    @DisplayName("Path expressions")
    class PathExpressions {

        @Test
        @DisplayName("generates multi-segment path")
        void generatePath() {
            PathExpression path = new PathExpression(
                    List.of(
                            new PathSegment("department", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)),
                    String.class);

            String java = JavaSourceGenerator.generateJavaSource(path);

            assertThat(java).isEqualTo("p -> p.department.name");
        }
    }

    @Nested
    @DisplayName("Method calls")
    class MethodCalls {

        @Test
        @DisplayName("generates method with no arguments")
        void generateMethodNoArgs() {
            LambdaExpression expr = new MethodCall(
                    new FieldAccess("name", String.class),
                    "toLowerCase",
                    List.of(),
                    String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.name.toLowerCase()");
        }

        @Test
        @DisplayName("generates method with arguments")
        void generateMethodWithArgs() {
            LambdaExpression expr = new MethodCall(
                    new FieldAccess("name", String.class),
                    "startsWith",
                    List.of(new Constant("A", String.class)),
                    boolean.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.name.startsWith(\"A\")");
        }

        @Test
        @DisplayName("generates equals method")
        void generateEquals() {
            LambdaExpression expr = new MethodCall(
                    new FieldAccess("name", String.class),
                    "equals",
                    List.of(new Constant("John", String.class)),
                    boolean.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.name.equals(\"John\")");
        }
    }

    @Nested
    @DisplayName("Captured variables")
    class CapturedVariables {

        @Test
        @DisplayName("generates captured variable reference")
        void generateCapturedVar() {
            LambdaExpression expr = BinaryOp.eq(
                    new FieldAccess("age", int.class),
                    new CapturedVariable(0, int.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.age == capturedVar0");
        }
    }

    @Nested
    @DisplayName("Constructor calls")
    class ConstructorCalls {

        @Test
        @DisplayName("generates DTO constructor")
        void generateConstructor() {
            LambdaExpression expr = new ConstructorCall(
                    "com/example/PersonDTO",
                    List.of(
                            new FieldAccess("firstName", String.class),
                            new FieldAccess("age", int.class)),
                    Object.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> new PersonDTO(p.firstName, p.age)");
        }
    }

    @Nested
    @DisplayName("Conditional expressions")
    class ConditionalExpressions {

        @Test
        @DisplayName("generates ternary operator")
        void generateTernary() {
            LambdaExpression expr = new Conditional(
                    new FieldAccess("active", boolean.class),
                    new Constant("Yes", String.class),
                    new Constant("No", String.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> (p.active ? \"Yes\" : \"No\")");
        }
    }

    @Nested
    @DisplayName("Expression body only")
    class ExpressionBodyOnly {

        @Test
        @DisplayName("generates expression without lambda arrow")
        void generateBodyOnly() {
            LambdaExpression expr = BinaryOp.gt(
                    new FieldAccess("age", int.class),
                    new Constant(18, int.class));

            String java = JavaSourceGenerator.expressionBodyToJava(expr);

            assertThat(java).isEqualTo("p.age > 18");
        }
    }

    @Nested
    @DisplayName("Bi-entity expressions")
    class BiEntityExpressions {

        @Test
        @DisplayName("generates bi-entity lambda")
        void generateBiEntityLambda() {
            LambdaExpression expr = new MethodCall(
                    BiEntityFieldAccess.fromSecond("type", String.class),
                    "equals",
                    List.of(new Constant("mobile", String.class)),
                    boolean.class);

            String java = JavaSourceGenerator.generateBiEntityJavaSource(expr, "p", "ph");

            assertThat(java).isEqualTo("(p, ph) -> ph.type.equals(\"mobile\")");
        }
    }

    @Nested
    @DisplayName("Group expressions")
    class GroupExpressions {

        @Test
        @DisplayName("generates g.key()")
        void generateGroupKey() {
            LambdaExpression expr = new GroupKeyReference(null, String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.key()");
        }

        @Test
        @DisplayName("generates g.count()")
        void generateGroupCount() {
            LambdaExpression expr = GroupAggregation.count();

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.count()");
        }

        @Test
        @DisplayName("generates g.avg(field)")
        void generateGroupAvg() {
            LambdaExpression expr = GroupAggregation.avg(
                    new FieldAccess("salary", double.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.avg(e.salary)");
        }
    }
}
