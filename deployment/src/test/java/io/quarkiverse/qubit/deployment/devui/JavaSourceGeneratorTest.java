package io.quarkiverse.qubit.deployment.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;

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

        @Test
        @DisplayName("generates g.countDistinct(field)")
        void generateGroupCountDistinct() {
            LambdaExpression expr = GroupAggregation.countDistinct(
                    new FieldAccess("department", String.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.countDistinct(e.department)");
        }

        @Test
        @DisplayName("generates g.sumInteger(field)")
        void generateGroupSumInteger() {
            LambdaExpression expr = GroupAggregation.sumInteger(
                    new FieldAccess("count", int.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.sumInteger(e.count)");
        }

        @Test
        @DisplayName("generates g.sumLong(field)")
        void generateGroupSumLong() {
            LambdaExpression expr = GroupAggregation.sumLong(
                    new FieldAccess("total", long.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.sumLong(e.total)");
        }

        @Test
        @DisplayName("generates g.sumDouble(field)")
        void generateGroupSumDouble() {
            LambdaExpression expr = GroupAggregation.sumDouble(
                    new FieldAccess("amount", double.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.sumDouble(e.amount)");
        }

        @Test
        @DisplayName("generates g.min(field)")
        void generateGroupMin() {
            LambdaExpression expr = GroupAggregation.min(
                    new FieldAccess("price", Double.class), Double.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.min(e.price)");
        }

        @Test
        @DisplayName("generates g.max(field)")
        void generateGroupMax() {
            LambdaExpression expr = GroupAggregation.max(
                    new FieldAccess("price", Double.class), Double.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g.max(e.price)");
        }

        @Test
        @DisplayName("generates group parameter")
        void generateGroupParameter() {
            LambdaExpression expr = new GroupParameter("g", Object.class, 0, Object.class, String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> g");
        }
    }

    @Nested
    @DisplayName("Constant formatting")
    class ConstantFormatting {

        @Test
        @DisplayName("generates long constant with L suffix")
        void generateLongConstant() {
            LambdaExpression expr = new Constant(100L, long.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> 100L");
        }

        @Test
        @DisplayName("generates float constant with f suffix")
        void generateFloatConstant() {
            LambdaExpression expr = new Constant(3.14f, float.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> 3.14f");
        }

        @Test
        @DisplayName("generates double constant with d suffix")
        void generateDoubleConstant() {
            LambdaExpression expr = new Constant(2.718, double.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> 2.718d");
        }

        @Test
        @DisplayName("generates character constant with single quotes")
        void generateCharConstant() {
            LambdaExpression expr = new Constant('A', char.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> 'A'");
        }

        @Test
        @DisplayName("generates null constant")
        void generateNullConstant() {
            LambdaExpression expr = new Constant(null, String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> null");
        }

        @Test
        @DisplayName("generates enum constant with class name")
        void generateEnumConstant() {
            LambdaExpression expr = new Constant(Thread.State.RUNNABLE, Thread.State.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> State.RUNNABLE");
        }

        @Test
        @DisplayName("escapes string with newline")
        void escapeNewline() {
            LambdaExpression expr = new Constant("hello\nworld", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> \"hello\\nworld\"");
        }

        @Test
        @DisplayName("escapes string with tab")
        void escapeTab() {
            LambdaExpression expr = new Constant("hello\tworld", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> \"hello\\tworld\"");
        }

        @Test
        @DisplayName("escapes string with quotes")
        void escapeQuotes() {
            LambdaExpression expr = new Constant("say \"hello\"", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> \"say \\\"hello\\\"\"");
        }

        @Test
        @DisplayName("escapes string with backslash")
        void escapeBackslash() {
            LambdaExpression expr = new Constant("path\\file", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> \"path\\\\file\"");
        }

        @Test
        @DisplayName("escapes string with carriage return")
        void escapeCarriageReturn() {
            LambdaExpression expr = new Constant("line1\rline2", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> \"line1\\rline2\"");
        }
    }

    @Nested
    @DisplayName("Boolean simplification")
    class BooleanSimplification {

        @Test
        @DisplayName("simplifies field == true to field")
        void simplifyEqTrue() {
            LambdaExpression expr = BinaryOp.eq(
                    new FieldAccess("active", boolean.class),
                    Constant.TRUE);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.active");
        }

        @Test
        @DisplayName("simplifies field == false to !field")
        void simplifyEqFalse() {
            LambdaExpression expr = BinaryOp.eq(
                    new FieldAccess("active", boolean.class),
                    Constant.FALSE);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !p.active");
        }

        @Test
        @DisplayName("simplifies field != true to !field")
        void simplifyNeTrue() {
            LambdaExpression expr = BinaryOp.ne(
                    new FieldAccess("active", boolean.class),
                    Constant.TRUE);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !p.active");
        }

        @Test
        @DisplayName("simplifies field != false to field")
        void simplifyNeFalse() {
            LambdaExpression expr = BinaryOp.ne(
                    new FieldAccess("active", boolean.class),
                    Constant.FALSE);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.active");
        }

        @Test
        @DisplayName("simplifies true == field")
        void simplifyTrueEqField() {
            LambdaExpression expr = BinaryOp.eq(
                    Constant.TRUE,
                    new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.active");
        }

        @Test
        @DisplayName("simplifies Boolean path == true")
        void simplifyPathEqTrue() {
            PathExpression path = new PathExpression(
                    List.of(new PathSegment("enabled", Boolean.class, RelationType.FIELD)),
                    Boolean.class);
            LambdaExpression expr = BinaryOp.eq(path, Constant.TRUE);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.enabled");
        }

        @Test
        @DisplayName("simplifies with Boolean wrapper constant")
        void simplifyWithBooleanWrapper() {
            LambdaExpression expr = BinaryOp.eq(
                    new FieldAccess("active", boolean.class),
                    new Constant(Boolean.TRUE, Boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.active");
        }
    }

    @Nested
    @DisplayName("String equality formatting")
    class StringEqualityFormatting {

        @Test
        @DisplayName("formats string NE as !equals")
        void formatStringNotEquals() {
            LambdaExpression expr = BinaryOp.ne(
                    new FieldAccess("status", String.class),
                    new Constant("deleted", String.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !p.status.equals(\"deleted\")");
        }

        @Test
        @DisplayName("formats constant == field as field.equals")
        void formatConstantEqField() {
            LambdaExpression expr = BinaryOp.eq(
                    new Constant("active", String.class),
                    new FieldAccess("status", String.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.status.equals(\"active\")");
        }

        @Test
        @DisplayName("formats constant != field as !field.equals")
        void formatConstantNeField() {
            LambdaExpression expr = BinaryOp.ne(
                    new Constant("deleted", String.class),
                    new FieldAccess("status", String.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !p.status.equals(\"deleted\")");
        }
    }

    @Nested
    @DisplayName("Collection expressions")
    class CollectionExpressions {

        @Test
        @DisplayName("generates IN expression as contains")
        void generateInExpression() {
            LambdaExpression expr = InExpression.in(
                    new FieldAccess("city", String.class),
                    new CapturedVariable(0, java.util.Collection.class, "cities"));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> cities.contains(p.city)");
        }

        @Test
        @DisplayName("generates NOT IN expression with negation")
        void generateNotInExpression() {
            LambdaExpression expr = InExpression.notIn(
                    new FieldAccess("city", String.class),
                    new CapturedVariable(0, java.util.Collection.class, "cities"));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !cities.contains(p.city)");
        }

        @Test
        @DisplayName("generates MEMBER OF expression as contains")
        void generateMemberOfExpression() {
            LambdaExpression expr = MemberOfExpression.memberOf(
                    new Constant("admin", String.class),
                    new FieldAccess("roles", java.util.Set.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.roles.contains(\"admin\")");
        }

        @Test
        @DisplayName("generates NOT MEMBER OF expression with negation")
        void generateNotMemberOfExpression() {
            LambdaExpression expr = MemberOfExpression.notMemberOf(
                    new Constant("admin", String.class),
                    new FieldAccess("roles", java.util.Set.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> !p.roles.contains(\"admin\")");
        }
    }

    @Nested
    @DisplayName("Type operations")
    class TypeOperations {

        @Test
        @DisplayName("generates cast expression")
        void generateCast() {
            LambdaExpression expr = new Cast(
                    new FieldAccess("value", Object.class),
                    String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> ((String) p.value)");
        }

        @Test
        @DisplayName("generates instanceof expression")
        void generateInstanceOf() {
            LambdaExpression expr = new InstanceOf(
                    new FieldAccess("entity", Object.class),
                    String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.entity instanceof String");
        }
    }

    @Nested
    @DisplayName("Array creation")
    class ArrayCreationTests {

        @Test
        @DisplayName("generates array creation")
        void generateArrayCreation() {
            LambdaExpression expr = new ArrayCreation(
                    "java/lang/Object",
                    List.of(
                            new FieldAccess("firstName", String.class),
                            new FieldAccess("age", int.class)),
                    Object[].class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> new Object[]{p.firstName, p.age}");
        }
    }

    @Nested
    @DisplayName("Null literal")
    class NullLiteralTests {

        @Test
        @DisplayName("generates null literal")
        void generateNullLiteral() {
            LambdaExpression expr = new NullLiteral(String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> null");
        }
    }

    @Nested
    @DisplayName("Parameter reference")
    class ParameterReference {

        @Test
        @DisplayName("generates parameter reference")
        void generateParameter() {
            LambdaExpression expr = new Parameter("entity", Object.class, 0);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p");
        }
    }

    @Nested
    @DisplayName("Subquery expressions")
    class SubqueryExpressions {

        @Test
        @DisplayName("generates scalar subquery with avg")
        void generateScalarSubqueryAvg() {
            LambdaExpression expr = ScalarSubquery.avg(
                    Object.class,
                    new FieldAccess("salary", double.class),
                    null);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains("subquery(Object.class)");
            assertThat(java).contains(".avg(s -> s.salary)");
        }

        @Test
        @DisplayName("generates scalar subquery with predicate")
        void generateScalarSubqueryWithPredicate() {
            LambdaExpression expr = ScalarSubquery.avg(
                    Object.class,
                    new FieldAccess("salary", double.class),
                    new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".where(s -> s.active)");
        }

        @Test
        @DisplayName("generates scalar subquery count")
        void generateScalarSubqueryCount() {
            LambdaExpression expr = ScalarSubquery.count(Object.class, null);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".count()");
        }

        @Test
        @DisplayName("generates scalar subquery sum")
        void generateScalarSubquerySum() {
            LambdaExpression expr = ScalarSubquery.sum(
                    Object.class,
                    new FieldAccess("amount", double.class),
                    null,
                    Double.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".sum(s -> s.amount)");
        }

        @Test
        @DisplayName("generates scalar subquery min")
        void generateScalarSubqueryMin() {
            LambdaExpression expr = ScalarSubquery.min(
                    Object.class,
                    new FieldAccess("price", double.class),
                    null,
                    Double.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".min(s -> s.price)");
        }

        @Test
        @DisplayName("generates scalar subquery max")
        void generateScalarSubqueryMax() {
            LambdaExpression expr = ScalarSubquery.max(
                    Object.class,
                    new FieldAccess("price", double.class),
                    null,
                    Double.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".max(s -> s.price)");
        }

        @Test
        @DisplayName("generates exists subquery")
        void generateExistsSubquery() {
            LambdaExpression expr = ExistsSubquery.exists(
                    Object.class,
                    new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains("subquery(Object.class)");
            assertThat(java).contains(".exists(s -> s.active)");
        }

        @Test
        @DisplayName("generates not exists subquery")
        void generateNotExistsSubquery() {
            LambdaExpression expr = ExistsSubquery.notExists(
                    Object.class,
                    new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".notExists(s -> s.active)");
        }

        @Test
        @DisplayName("generates in subquery")
        void generateInSubquery() {
            LambdaExpression expr = InSubquery.in(
                    new FieldAccess("departmentId", Long.class),
                    Object.class,
                    new FieldAccess("id", Long.class),
                    null);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".in(p.departmentId, s -> s.id)");
        }

        @Test
        @DisplayName("generates not in subquery")
        void generateNotInSubquery() {
            LambdaExpression expr = InSubquery.notIn(
                    new FieldAccess("departmentId", Long.class),
                    Object.class,
                    new FieldAccess("id", Long.class),
                    null);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(".notIn(p.departmentId, s -> s.id)");
        }

        @Test
        @DisplayName("generates in subquery with predicate")
        void generateInSubqueryWithPredicate() {
            LambdaExpression expr = InSubquery.in(
                    new FieldAccess("departmentId", Long.class),
                    Object.class,
                    new FieldAccess("id", Long.class),
                    new FieldAccess("active", boolean.class));

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).contains(", s -> s.active)");
        }

        @Test
        @DisplayName("generates correlated variable")
        void generateCorrelatedVariable() {
            LambdaExpression expr = new CorrelatedVariable(
                    new FieldAccess("id", Long.class),
                    0,
                    Object.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.p.id");
        }

        @Test
        @DisplayName("generates subquery builder reference placeholder")
        void generateSubqueryBuilderReference() {
            LambdaExpression expr = new SubqueryBuilderReference(Object.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> subquery(...)");
        }
    }

    @Nested
    @DisplayName("Bi-entity path expressions")
    class BiEntityPathExpressions {

        @Test
        @DisplayName("generates bi-entity path from first entity")
        void generateBiEntityPathFirst() {
            BiEntityPathExpression path = BiEntityPathExpression.fromFirst(
                    List.of(new PathSegment("department", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)),
                    String.class);

            String java = JavaSourceGenerator.generateBiEntityJavaSource(path, "p", "ph");

            assertThat(java).isEqualTo("(p, ph) -> p.department.name");
        }

        @Test
        @DisplayName("generates bi-entity path from second entity")
        void generateBiEntityPathSecond() {
            BiEntityPathExpression path = BiEntityPathExpression.fromSecond(
                    List.of(new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)),
                    String.class);

            String java = JavaSourceGenerator.generateBiEntityJavaSource(path, "p", "ph");

            assertThat(java).isEqualTo("(p, ph) -> ph.owner.name");
        }

        @Test
        @DisplayName("generates bi-entity parameter from first")
        void generateBiEntityParamFirst() {
            BiEntityParameter param = new BiEntityParameter("entity", Object.class, 0, EntityPosition.FIRST);

            String java = JavaSourceGenerator.generateBiEntityJavaSource(param, "person", "phone");

            assertThat(java).isEqualTo("(person, phone) -> person");
        }

        @Test
        @DisplayName("generates bi-entity parameter from second")
        void generateBiEntityParamSecond() {
            BiEntityParameter param = new BiEntityParameter("entity", Object.class, 1, EntityPosition.SECOND);

            String java = JavaSourceGenerator.generateBiEntityJavaSource(param, "person", "phone");

            assertThat(java).isEqualTo("(person, phone) -> phone");
        }

        @Test
        @DisplayName("generates bi-entity binary op")
        void generateBiEntityBinaryOp() {
            LambdaExpression expr = BinaryOp.and(
                    BiEntityFieldAccess.fromFirst("active", boolean.class),
                    BiEntityFieldAccess.fromSecond("verified", boolean.class));

            String java = JavaSourceGenerator.generateBiEntityJavaSource(expr, "p", "ph");

            assertThat(java).contains("(p.active && ph.verified)");
        }

        @Test
        @DisplayName("generates bi-entity unary op")
        void generateBiEntityUnaryOp() {
            LambdaExpression expr = UnaryOp.not(BiEntityFieldAccess.fromFirst("deleted", boolean.class));

            String java = JavaSourceGenerator.generateBiEntityJavaSource(expr, "p", "ph");

            assertThat(java).isEqualTo("(p, ph) -> !p.deleted");
        }

        @Test
        @DisplayName("bi-entity null returns null")
        void biEntityNullReturnsNull() {
            assertThat(JavaSourceGenerator.generateBiEntityJavaSource(null, "p", "ph")).isNull();
        }
    }

    @Nested
    @DisplayName("Group java source generation")
    class GroupJavaSourceGeneration {

        @Test
        @DisplayName("generateGroupJavaSource null returns null")
        void groupSourceNullReturnsNull() {
            assertThat(JavaSourceGenerator.generateGroupJavaSource(null)).isNull();
        }

        @Test
        @DisplayName("generateGroupJavaSource formats with g prefix")
        void groupSourceUsesGPrefix() {
            LambdaExpression expr = new FieldAccess("name", String.class);

            String java = JavaSourceGenerator.generateGroupJavaSource(expr);

            assertThat(java).isEqualTo("g -> g.name");
        }
    }

    @Nested
    @DisplayName("Expression body only")
    class ExpressionBodyOnlyExtended {

        @Test
        @DisplayName("expressionBodyToJava null returns null")
        void bodyNullReturnsNull() {
            assertThat(JavaSourceGenerator.expressionBodyToJava(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Bi-entity expressions in single context")
    class BiEntityInSingleContext {

        @Test
        @DisplayName("bi-entity field in single context uses default aliases")
        void biEntityFieldInSingleContext() {
            LambdaExpression expr = BiEntityFieldAccess.fromSecond("name", String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> e2.name");
        }

        @Test
        @DisplayName("bi-entity path in single context uses default aliases")
        void biEntityPathInSingleContext() {
            BiEntityPathExpression path = BiEntityPathExpression.fromFirst(
                    List.of(new PathSegment("department", Object.class, RelationType.MANY_TO_ONE)),
                    Object.class);

            String java = JavaSourceGenerator.generateJavaSource(path);

            assertThat(java).isEqualTo("p -> e1.department");
        }

        @Test
        @DisplayName("bi-entity param in single context uses default aliases")
        void biEntityParamInSingleContext() {
            BiEntityParameter param = new BiEntityParameter("e", Object.class, 0, EntityPosition.FIRST);

            String java = JavaSourceGenerator.generateJavaSource(param);

            assertThat(java).isEqualTo("p -> e1");
        }
    }

    @Nested
    @DisplayName("Method calls with multiple arguments")
    class MethodCallMultipleArgs {

        @Test
        @DisplayName("generates method call with multiple arguments")
        void generateMethodMultipleArgs() {
            LambdaExpression expr = new MethodCall(
                    new FieldAccess("text", String.class),
                    "substring",
                    List.of(new Constant(0, int.class), new Constant(5, int.class)),
                    String.class);

            String java = JavaSourceGenerator.generateJavaSource(expr);

            assertThat(java).isEqualTo("p -> p.text.substring(0, 5)");
        }
    }
}
