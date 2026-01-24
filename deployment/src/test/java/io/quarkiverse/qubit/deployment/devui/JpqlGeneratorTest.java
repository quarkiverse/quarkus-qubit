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

    @Nested
    @DisplayName("Join queries")
    class JoinQueries {

        @Test
        @DisplayName("generates inner join query")
        void generateInnerJoin() {
            FieldAccess relationship = new FieldAccess("phones", java.util.List.class);

            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, relationship, null, null, false, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e JOIN e.phones j");
        }

        @Test
        @DisplayName("generates left join query")
        void generateLeftJoin() {
            FieldAccess relationship = new FieldAccess("phones", java.util.List.class);

            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, relationship, null, null, true, false);

            assertThat(jpql).isEqualTo("SELECT e FROM Person e LEFT JOIN e.phones j");
        }

        @Test
        @DisplayName("generates join query with count")
        void generateJoinCount() {
            FieldAccess relationship = new FieldAccess("phones", java.util.List.class);

            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, relationship, null, null, false, true);

            assertThat(jpql).isEqualTo("SELECT COUNT(e) FROM Person e JOIN e.phones j");
        }

        @Test
        @DisplayName("generates join query with predicate")
        void generateJoinWithPredicate() {
            FieldAccess relationship = new FieldAccess("phones", java.util.List.class);
            LambdaExpression predicate = BinaryOp.eq(
                    BiEntityFieldAccess.fromSecond("type", String.class),
                    new Constant("mobile", String.class));

            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, relationship, predicate, null, false, false);

            assertThat(jpql).contains("WHERE j.type = 'mobile'");
        }

        @Test
        @DisplayName("generates join query with projection")
        void generateJoinWithProjection() {
            FieldAccess relationship = new FieldAccess("phones", java.util.List.class);
            FieldAccess projection = new FieldAccess("name", String.class);

            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, relationship, null, projection, false, false);

            assertThat(jpql).startsWith("SELECT e.name FROM Person e JOIN");
        }

        @Test
        @DisplayName("generates join with path expression relationship")
        void generateJoinWithPathRelationship() {
            PathExpression path = new PathExpression(
                    java.util.List.of(
                            new PathSegment("department", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("employees", java.util.List.class, RelationType.ONE_TO_MANY)),
                    java.util.List.class);

            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, path, null, null, false, false);

            assertThat(jpql).contains("JOIN e.department.employees j");
        }

        @Test
        @DisplayName("generates join with null relationship")
        void generateJoinWithNullRelationship() {
            String jpql = JpqlGenerator.generateJoinJpql(
                    PERSON_CLASS, null, null, null, false, false);

            assertThat(jpql).contains("JOIN e.? j");
        }
    }

    @Nested
    @DisplayName("String method calls")
    class StringMethodCalls {

        @Test
        @DisplayName("generates LIKE for endsWith")
        void generateEndsWith() {
            LambdaExpression predicate = new MethodCall(
                    new FieldAccess("name", String.class),
                    "endsWith",
                    java.util.List.of(new Constant("son", String.class)),
                    boolean.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e.name LIKE CONCAT('%', 'son')");
        }

        @Test
        @DisplayName("generates LIKE for contains")
        void generateContains() {
            LambdaExpression predicate = new MethodCall(
                    new FieldAccess("name", String.class),
                    "contains",
                    java.util.List.of(new Constant("oh", String.class)),
                    boolean.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e.name LIKE CONCAT('%', 'oh', '%')");
        }

        @Test
        @DisplayName("generates case-insensitive equals")
        void generateEqualsIgnoreCase() {
            LambdaExpression predicate = new MethodCall(
                    new FieldAccess("name", String.class),
                    "equalsIgnoreCase",
                    java.util.List.of(new Constant("john", String.class)),
                    boolean.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("UPPER(e.name) = UPPER('john')");
        }

        @Test
        @DisplayName("generates isEmpty check")
        void generateIsEmpty() {
            LambdaExpression predicate = new MethodCall(
                    new FieldAccess("name", String.class),
                    "isEmpty",
                    java.util.List.of(),
                    boolean.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("LENGTH(e.name) = 0");
        }

        @Test
        @DisplayName("generates isBlank check")
        void generateIsBlank() {
            LambdaExpression predicate = new MethodCall(
                    new FieldAccess("name", String.class),
                    "isBlank",
                    java.util.List.of(),
                    boolean.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("TRIM(e.name) = ''");
        }

        @Test
        @DisplayName("generates LOWER for toLowerCase")
        void generateToLowerCase() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "toLowerCase",
                    java.util.List.of(),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).isEqualTo("SELECT LOWER(e.name) FROM Person e");
        }

        @Test
        @DisplayName("generates TRIM")
        void generateTrim() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "trim",
                    java.util.List.of(),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).isEqualTo("SELECT TRIM(e.name) FROM Person e");
        }

        @Test
        @DisplayName("generates LENGTH")
        void generateLength() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "length",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).isEqualTo("SELECT LENGTH(e.name) FROM Person e");
        }

        @Test
        @DisplayName("generates SUBSTRING with two args")
        void generateSubstringTwoArgs() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "substring",
                    java.util.List.of(new Constant(0, int.class), new Constant(5, int.class)),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("SUBSTRING(e.name, 0, 5)");
        }

        @Test
        @DisplayName("generates SUBSTRING with one arg")
        void generateSubstringOneArg() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "substring",
                    java.util.List.of(new Constant(2, int.class)),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("SUBSTRING(e.name, 2)");
        }

        @Test
        @DisplayName("generates SUBSTRING with no args")
        void generateSubstringNoArgs() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("name", String.class),
                    "substring",
                    java.util.List.of(),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("SUBSTRING(e.name)");
        }

        @Test
        @DisplayName("generates CONCAT")
        void generateConcat() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("firstName", String.class),
                    "concat",
                    java.util.List.of(new FieldAccess("lastName", String.class)),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("CONCAT(e.firstName, e.lastName)");
        }
    }

    @Nested
    @DisplayName("Math method calls")
    class MathMethodCalls {

        @Test
        @DisplayName("generates ABS")
        void generateAbs() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("balance", double.class),
                    "abs",
                    java.util.List.of(),
                    double.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("ABS(e.balance)");
        }

        @Test
        @DisplayName("generates SQRT")
        void generateSqrt() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("value", double.class),
                    "sqrt",
                    java.util.List.of(),
                    double.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("SQRT(e.value)");
        }

        @Test
        @DisplayName("generates MOD")
        void generateMod() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("number", int.class),
                    "mod",
                    java.util.List.of(new Constant(2, int.class)),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("MOD(e.number, 2)");
        }
    }

    @Nested
    @DisplayName("Date/Time method calls")
    class DateTimeMethodCalls {

        @Test
        @DisplayName("generates YEAR")
        void generateYear() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("birthDate", java.time.LocalDate.class),
                    "getYear",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("YEAR(e.birthDate)");
        }

        @Test
        @DisplayName("generates MONTH")
        void generateMonth() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("birthDate", java.time.LocalDate.class),
                    "getMonthValue",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("MONTH(e.birthDate)");
        }

        @Test
        @DisplayName("generates DAY")
        void generateDay() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("birthDate", java.time.LocalDate.class),
                    "getDayOfMonth",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("DAY(e.birthDate)");
        }

        @Test
        @DisplayName("generates HOUR")
        void generateHour() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("createdAt", java.time.LocalDateTime.class),
                    "getHour",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("HOUR(e.createdAt)");
        }

        @Test
        @DisplayName("generates MINUTE")
        void generateMinute() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("createdAt", java.time.LocalDateTime.class),
                    "getMinute",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("MINUTE(e.createdAt)");
        }

        @Test
        @DisplayName("generates SECOND")
        void generateSecond() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("createdAt", java.time.LocalDateTime.class),
                    "getSecond",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("SECOND(e.createdAt)");
        }
    }

    @Nested
    @DisplayName("Collection operations")
    class CollectionOperations {

        @Test
        @DisplayName("generates SIZE")
        void generateSize() {
            LambdaExpression projection = new MethodCall(
                    new FieldAccess("phones", java.util.List.class),
                    "size",
                    java.util.List.of(),
                    int.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("SIZE(e.phones)");
        }

        @Test
        @DisplayName("generates IN expression")
        void generateInExpression() {
            LambdaExpression predicate = InExpression.in(
                    new FieldAccess("city", String.class),
                    new CapturedVariable(0, java.util.Collection.class, "cities"));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e.city IN :cities");
        }

        @Test
        @DisplayName("generates NOT IN expression")
        void generateNotInExpression() {
            LambdaExpression predicate = InExpression.notIn(
                    new FieldAccess("city", String.class),
                    new CapturedVariable(0, java.util.Collection.class, "cities"));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e.city NOT IN :cities");
        }

        @Test
        @DisplayName("generates MEMBER OF expression")
        void generateMemberOfExpression() {
            LambdaExpression predicate = MemberOfExpression.memberOf(
                    new Constant("admin", String.class),
                    new FieldAccess("roles", java.util.Set.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("'admin' MEMBER OF e.roles");
        }

        @Test
        @DisplayName("generates NOT MEMBER OF expression")
        void generateNotMemberOfExpression() {
            LambdaExpression predicate = MemberOfExpression.notMemberOf(
                    new Constant("admin", String.class),
                    new FieldAccess("roles", java.util.Set.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("'admin' NOT MEMBER OF e.roles");
        }
    }

    @Nested
    @DisplayName("Type operations")
    class TypeOperations {

        @Test
        @DisplayName("generates TYPE for instanceof")
        void generateInstanceOf() {
            LambdaExpression predicate = new InstanceOf(
                    new Parameter("entity", Object.class, 0),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("TYPE(e) = String");
        }

        @Test
        @DisplayName("cast expression returns inner expression")
        void castExpressionReturnsInner() {
            LambdaExpression projection = new Cast(
                    new FieldAccess("value", Object.class),
                    String.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("e.value");
        }
    }

    @Nested
    @DisplayName("Constructor and array")
    class ConstructorAndArray {

        @Test
        @DisplayName("generates constructor call")
        void generateConstructorCall() {
            LambdaExpression projection = new ConstructorCall(
                    "com/example/PersonDTO",
                    java.util.List.of(
                            new FieldAccess("firstName", String.class),
                            new FieldAccess("lastName", String.class)),
                    Object.class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("NEW PersonDTO(e.firstName, e.lastName)");
        }

        @Test
        @DisplayName("generates array creation as list")
        void generateArrayCreation() {
            LambdaExpression projection = new ArrayCreation(
                    "java/lang/Object",
                    java.util.List.of(
                            new FieldAccess("firstName", String.class),
                            new FieldAccess("age", int.class)),
                    Object[].class);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("[e.firstName, e.age]");
        }
    }

    @Nested
    @DisplayName("Conditional expression")
    class ConditionalExpression {

        @Test
        @DisplayName("generates CASE WHEN expression")
        void generateCaseWhen() {
            LambdaExpression projection = new Conditional(
                    new FieldAccess("active", boolean.class),
                    new Constant("Yes", String.class),
                    new Constant("No", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, null, projection, false);

            assertThat(jpql).contains("CASE WHEN e.active THEN 'Yes' ELSE 'No' END");
        }
    }

    @Nested
    @DisplayName("Constant formatting")
    class ConstantFormatting {

        @Test
        @DisplayName("formats NULL constant")
        void formatNullConstant() {
            LambdaExpression predicate = BinaryOp.eq(
                    new FieldAccess("name", String.class),
                    new Constant(null, String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("= NULL");
        }

        @Test
        @DisplayName("formats boolean constant as uppercase")
        void formatBooleanConstant() {
            LambdaExpression predicate = BinaryOp.eq(
                    new FieldAccess("active", boolean.class),
                    new Constant(false, boolean.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("= FALSE");
        }

        @Test
        @DisplayName("formats enum constant with single quotes")
        void formatEnumConstant() {
            LambdaExpression predicate = BinaryOp.eq(
                    new FieldAccess("state", Thread.State.class),
                    new Constant(Thread.State.RUNNABLE, Thread.State.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("= 'RUNNABLE'");
        }

        @Test
        @DisplayName("escapes single quotes in string")
        void escapesSingleQuotes() {
            LambdaExpression predicate = BinaryOp.eq(
                    new FieldAccess("name", String.class),
                    new Constant("O'Brien", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("'O''Brien'");
        }
    }

    @Nested
    @DisplayName("Bi-entity expressions")
    class BiEntityExpressions {

        @Test
        @DisplayName("generates bi-entity field from first")
        void generateBiEntityFieldFirst() {
            LambdaExpression predicate = BinaryOp.eq(
                    BiEntityFieldAccess.fromFirst("name", String.class),
                    new Constant("John", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e1.name = 'John'");
        }

        @Test
        @DisplayName("generates bi-entity field from second")
        void generateBiEntityFieldSecond() {
            LambdaExpression predicate = BinaryOp.eq(
                    BiEntityFieldAccess.fromSecond("type", String.class),
                    new Constant("mobile", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e2.type = 'mobile'");
        }

        @Test
        @DisplayName("generates bi-entity path")
        void generateBiEntityPath() {
            BiEntityPathExpression path = BiEntityPathExpression.fromFirst(
                    java.util.List.of(
                            new PathSegment("department", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)),
                    String.class);
            LambdaExpression predicate = BinaryOp.eq(path, new Constant("Engineering", String.class));

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, predicate, null, false);

            assertThat(jpql).contains("e1.department.name = 'Engineering'");
        }

        @Test
        @DisplayName("generates bi-entity parameter")
        void generateBiEntityParameter() {
            LambdaExpression expr = new BiEntityParameter("entity", Object.class, 0, EntityPosition.SECOND);

            String jpql = JpqlGenerator.generateJpql(PERSON_CLASS, expr, null, false);

            assertThat(jpql).contains("e2");
        }
    }

    @Nested
    @DisplayName("Group expressions")
    class GroupExpressions {

        @Test
        @DisplayName("generates group key reference")
        void generateGroupKeyReference() {
            LambdaExpression expr = new GroupKeyReference(null, String.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("KEY");
        }

        @Test
        @DisplayName("generates group parameter")
        void generateGroupParameter() {
            LambdaExpression expr = new GroupParameter("g", Object.class, 0, Object.class, String.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("GROUP");
        }

        @Test
        @DisplayName("generates COUNT aggregation")
        void generateCountAggregation() {
            LambdaExpression expr = GroupAggregation.count();

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("COUNT(e)");
        }

        @Test
        @DisplayName("generates COUNT DISTINCT aggregation")
        void generateCountDistinctAggregation() {
            LambdaExpression expr = GroupAggregation.countDistinct(new FieldAccess("department", String.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("COUNT(DISTINCT e.department)");
        }

        @Test
        @DisplayName("generates AVG aggregation")
        void generateAvgAggregation() {
            LambdaExpression expr = GroupAggregation.avg(new FieldAccess("salary", double.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("AVG(e.salary)");
        }

        @Test
        @DisplayName("generates SUM aggregation")
        void generateSumAggregation() {
            LambdaExpression expr = GroupAggregation.sumLong(new FieldAccess("total", long.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("SUM(e.total)");
        }

        @Test
        @DisplayName("generates MIN aggregation")
        void generateMinAggregation() {
            LambdaExpression expr = GroupAggregation.min(new FieldAccess("price", Double.class), Double.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("MIN(e.price)");
        }

        @Test
        @DisplayName("generates MAX aggregation")
        void generateMaxAggregation() {
            LambdaExpression expr = GroupAggregation.max(new FieldAccess("price", Double.class), Double.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("MAX(e.price)");
        }
    }

    @Nested
    @DisplayName("Subquery expressions")
    class SubqueryExpressions {

        @Test
        @DisplayName("generates scalar subquery AVG")
        void generateScalarSubqueryAvg() {
            LambdaExpression expr = ScalarSubquery.avg(
                    Object.class,
                    new FieldAccess("salary", double.class),
                    null);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("SELECT AVG(e.salary)");
            assertThat(jpql).contains("FROM Object s");
        }

        @Test
        @DisplayName("generates scalar subquery with predicate")
        void generateScalarSubqueryWithPredicate() {
            LambdaExpression expr = ScalarSubquery.avg(
                    Object.class,
                    new FieldAccess("salary", double.class),
                    new FieldAccess("active", boolean.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("WHERE e.active");
        }

        @Test
        @DisplayName("generates scalar subquery COUNT")
        void generateScalarSubqueryCount() {
            LambdaExpression expr = ScalarSubquery.count(Object.class, null);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("SELECT COUNT(s)");
        }

        @Test
        @DisplayName("generates scalar subquery SUM")
        void generateScalarSubquerySum() {
            LambdaExpression expr = ScalarSubquery.sum(Object.class,
                    new FieldAccess("amount", double.class), null, Double.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("SELECT SUM(e.amount)");
        }

        @Test
        @DisplayName("generates scalar subquery MIN")
        void generateScalarSubqueryMin() {
            LambdaExpression expr = ScalarSubquery.min(Object.class,
                    new FieldAccess("price", double.class), null, Double.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("SELECT MIN(e.price)");
        }

        @Test
        @DisplayName("generates scalar subquery MAX")
        void generateScalarSubqueryMax() {
            LambdaExpression expr = ScalarSubquery.max(Object.class,
                    new FieldAccess("price", double.class), null, Double.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("SELECT MAX(e.price)");
        }

        @Test
        @DisplayName("generates EXISTS subquery")
        void generateExistsSubquery() {
            LambdaExpression expr = ExistsSubquery.exists(
                    Object.class,
                    new FieldAccess("active", boolean.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("EXISTS (SELECT s FROM Object s WHERE e.active)");
        }

        @Test
        @DisplayName("generates NOT EXISTS subquery")
        void generateNotExistsSubquery() {
            LambdaExpression expr = ExistsSubquery.notExists(
                    Object.class,
                    new FieldAccess("active", boolean.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("NOT EXISTS (SELECT s FROM Object s");
        }

        @Test
        @DisplayName("generates IN subquery")
        void generateInSubquery() {
            LambdaExpression expr = InSubquery.in(
                    new FieldAccess("departmentId", Long.class),
                    Object.class,
                    new FieldAccess("id", Long.class),
                    null);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("e.departmentId IN (SELECT e.id FROM Object s)");
        }

        @Test
        @DisplayName("generates NOT IN subquery")
        void generateNotInSubquery() {
            LambdaExpression expr = InSubquery.notIn(
                    new FieldAccess("departmentId", Long.class),
                    Object.class,
                    new FieldAccess("id", Long.class),
                    null);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("NOT IN");
        }

        @Test
        @DisplayName("generates IN subquery with predicate")
        void generateInSubqueryWithPredicate() {
            LambdaExpression expr = InSubquery.in(
                    new FieldAccess("departmentId", Long.class),
                    Object.class,
                    new FieldAccess("id", Long.class),
                    new FieldAccess("active", boolean.class));

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).contains("WHERE e.active");
        }

        @Test
        @DisplayName("generates correlated variable")
        void generateCorrelatedVariable() {
            LambdaExpression expr = new CorrelatedVariable(
                    new FieldAccess("id", Long.class),
                    0,
                    Object.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("OUTER.e.id");
        }

        @Test
        @DisplayName("generates subquery builder reference placeholder")
        void generateSubqueryBuilderReference() {
            LambdaExpression expr = new SubqueryBuilderReference(Object.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("(SUBQUERY)");
        }
    }

    @Nested
    @DisplayName("Null literal")
    class NullLiteralTests {

        @Test
        @DisplayName("generates NULL literal")
        void generateNullLiteral() {
            LambdaExpression expr = new NullLiteral(String.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("NULL");
        }
    }

    @Nested
    @DisplayName("Parameter reference")
    class ParameterReferenceTests {

        @Test
        @DisplayName("generates entity alias for parameter")
        void generateParameterAlias() {
            LambdaExpression expr = new Parameter("entity", Object.class, 0);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("e");
        }
    }

    @Nested
    @DisplayName("All operators")
    class AllOperators {

        @Test
        @DisplayName("generates all comparison operators")
        void generateAllComparisonOperators() {
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.lt(
                    new FieldAccess("age", int.class), new Constant(18, int.class)))).contains("<");
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.le(
                    new FieldAccess("age", int.class), new Constant(18, int.class)))).contains("<=");
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.ge(
                    new FieldAccess("age", int.class), new Constant(18, int.class)))).contains(">=");
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.ne(
                    new FieldAccess("age", int.class), new Constant(18, int.class)))).contains("<>");
        }

        @Test
        @DisplayName("generates arithmetic operators")
        void generateArithmeticOperators() {
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.sub(
                    new FieldAccess("price", double.class), new Constant(10.0, double.class)))).contains("-");
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.mul(
                    new FieldAccess("price", double.class), new Constant(2.0, double.class)))).contains("*");
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.div(
                    new FieldAccess("price", double.class), new Constant(2.0, double.class)))).contains("/");
            assertThat(JpqlGenerator.predicateToJpql(BinaryOp.mod(
                    new FieldAccess("count", int.class), new Constant(2, int.class)))).contains("MOD");
        }
    }

    @Nested
    @DisplayName("Default method handling")
    class DefaultMethodHandling {

        @Test
        @DisplayName("generates unknown method as uppercase function")
        void generateUnknownMethod() {
            LambdaExpression expr = new MethodCall(
                    new FieldAccess("value", Object.class),
                    "customMethod",
                    java.util.List.of(),
                    Object.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("CUSTOMMETHOD(e.value)");
        }

        @Test
        @DisplayName("generates equals method")
        void generateEqualsMethod() {
            LambdaExpression expr = new MethodCall(
                    new FieldAccess("name", String.class),
                    "equals",
                    java.util.List.of(new Constant("John", String.class)),
                    boolean.class);

            String jpql = JpqlGenerator.predicateToJpql(expr);

            assertThat(jpql).isEqualTo("e.name = 'John'");
        }
    }
}
