package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;

/**
 * Tests for {@link ExpressionTypeInferrer}.
 *
 * <p>
 * Tests type inference utilities for lambda expressions.
 */
class ExpressionTypeInferrerTest {

    // ==================== inferFieldType (single arg) Tests ====================

    @Nested
    class InferFieldTypeSingleArgTests {

        @Test
        void inferFieldType_withNull_returnsObjectClass() {
            Class<?> result = ExpressionTypeInferrer.inferFieldType(null);

            assertThat(result)
                    .as("Null expression should return Object.class")
                    .isEqualTo(Object.class);
        }

        @Test
        void inferFieldType_withFieldAccess_returnsFieldType() {
            LambdaExpression expr = field("name", String.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("FieldAccess should return its fieldType")
                    .isEqualTo(String.class);
        }

        @Test
        void inferFieldType_withFieldAccessIntegerType_returnsIntegerType() {
            LambdaExpression expr = field("age", Integer.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("FieldAccess with Integer type")
                    .isEqualTo(Integer.class);
        }

        @Test
        void inferFieldType_withPathExpression_returnsResultType() {
            PathExpression expr = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD),
                            new PathSegment("address", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("city", String.class, RelationType.FIELD)),
                    String.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("PathExpression should return its resultType")
                    .isEqualTo(String.class);
        }

        @Test
        void inferFieldType_withPathExpressionLongType_returnsLongType() {
            PathExpression expr = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD),
                            new PathSegment("id", Long.class, RelationType.FIELD)),
                    Long.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("PathExpression with Long resultType")
                    .isEqualTo(Long.class);
        }

        @Test
        void inferFieldType_withConstant_returnsObjectClass() {
            Constant expr = new Constant("test", String.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("Constant should return Object.class (default case)")
                    .isEqualTo(Object.class);
        }

        @Test
        void inferFieldType_withCapturedVariable_returnsObjectClass() {
            LambdaExpression expr = captured(0, String.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("CapturedVariable should return Object.class (default case)")
                    .isEqualTo(Object.class);
        }

        @Test
        void inferFieldType_withBinaryOp_returnsObjectClass() {
            BinaryOp expr = BinaryOp.eq(field("name", String.class), constant("test"));

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("BinaryOp should return Object.class (default case)")
                    .isEqualTo(Object.class);
        }

        @Test
        void inferFieldType_withMethodCall_returnsObjectClass() {
            MethodCall expr = new MethodCall(
                    field("name", String.class), "toUpperCase", List.of(), String.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("MethodCall should return Object.class (default case)")
                    .isEqualTo(Object.class);
        }

        @Test
        void inferFieldType_withParameter_returnsObjectClass() {
            Parameter expr = new Parameter("e", Object.class, 0);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr);

            assertThat(result)
                    .as("Parameter should return Object.class (default case)")
                    .isEqualTo(Object.class);
        }
    }

    // ==================== inferFieldType (with default) Tests ====================

    @Nested
    class InferFieldTypeWithDefaultTests {

        @Test
        void inferFieldType_withNullAndDefault_returnsDefault() {
            Class<?> result = ExpressionTypeInferrer.inferFieldType(null, Long.class);

            assertThat(result)
                    .as("Null expression should return defaultType")
                    .isEqualTo(Long.class);
        }

        @Test
        void inferFieldType_withNullAndStringDefault_returnsStringDefault() {
            Class<?> result = ExpressionTypeInferrer.inferFieldType(null, String.class);

            assertThat(result)
                    .as("Null expression should return String defaultType")
                    .isEqualTo(String.class);
        }

        @Test
        void inferFieldType_withFieldAccessAndDefault_returnsFieldType() {
            LambdaExpression expr = field("active", Boolean.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr, Object.class);

            assertThat(result)
                    .as("FieldAccess should return fieldType, not default")
                    .isEqualTo(Boolean.class);
        }

        @Test
        void inferFieldType_withPathExpressionAndDefault_returnsResultType() {
            PathExpression expr = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD),
                            new PathSegment("count", Integer.class, RelationType.FIELD)),
                    Integer.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr, Long.class);

            assertThat(result)
                    .as("PathExpression should return resultType, not default")
                    .isEqualTo(Integer.class);
        }

        @Test
        void inferFieldType_withConstantAndDefault_returnsDefault() {
            Constant expr = new Constant(42, Integer.class);

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr, Double.class);

            assertThat(result)
                    .as("Constant should return defaultType")
                    .isEqualTo(Double.class);
        }

        @Test
        void inferFieldType_withUnaryOpAndDefault_returnsDefault() {
            UnaryOp expr = UnaryOp.not(field("active", Boolean.class));

            Class<?> result = ExpressionTypeInferrer.inferFieldType(expr, Boolean.class);

            assertThat(result)
                    .as("UnaryOp should return defaultType")
                    .isEqualTo(Boolean.class);
        }
    }

    // ==================== isNumericType Tests ====================

    @Nested
    class IsNumericTypeTests {

        @Test
        void isNumericType_withIntegerField_returnsTrue() {
            LambdaExpression expr = field("count", Integer.class);

            assertThat(ExpressionTypeInferrer.isNumericType(expr))
                    .as("Integer field should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericType_withLongField_returnsTrue() {
            LambdaExpression expr = field("id", Long.class);

            assertThat(ExpressionTypeInferrer.isNumericType(expr))
                    .as("Long field should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericType_withDoubleField_returnsTrue() {
            LambdaExpression expr = field("salary", Double.class);

            assertThat(ExpressionTypeInferrer.isNumericType(expr))
                    .as("Double field should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericType_withStringField_returnsFalse() {
            LambdaExpression expr = field("name", String.class);

            assertThat(ExpressionTypeInferrer.isNumericType(expr))
                    .as("String field should not be numeric")
                    .isFalse();
        }

        @Test
        void isNumericType_withNullExpression_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericType(null))
                    .as("Null expression should not be numeric (Object.class)")
                    .isFalse();
        }

        @Test
        void isNumericType_withBigDecimalPathExpression_returnsTrue() {
            PathExpression expr = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD),
                            new PathSegment("amount", BigDecimal.class, RelationType.FIELD)),
                    BigDecimal.class);

            assertThat(ExpressionTypeInferrer.isNumericType(expr))
                    .as("BigDecimal path should be numeric")
                    .isTrue();
        }
    }

    // ==================== isNumericClass Tests ====================

    @Nested
    class IsNumericClassTests {

        @Test
        void isNumericClass_withIntPrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(int.class))
                    .as("int primitive should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withIntegerWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Integer.class))
                    .as("Integer wrapper should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withLongPrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(long.class))
                    .as("long primitive should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withLongWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Long.class))
                    .as("Long wrapper should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withDoublePrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(double.class))
                    .as("double primitive should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withDoubleWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Double.class))
                    .as("Double wrapper should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withFloatPrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(float.class))
                    .as("float primitive should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withFloatWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Float.class))
                    .as("Float wrapper should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withShortPrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(short.class))
                    .as("short primitive should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withShortWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Short.class))
                    .as("Short wrapper should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withBytePrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(byte.class))
                    .as("byte primitive should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withByteWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Byte.class))
                    .as("Byte wrapper should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withBigDecimal_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(BigDecimal.class))
                    .as("BigDecimal should be numeric (extends Number)")
                    .isTrue();
        }

        @Test
        void isNumericClass_withBigInteger_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(BigInteger.class))
                    .as("BigInteger should be numeric (extends Number)")
                    .isTrue();
        }

        @Test
        void isNumericClass_withNumberClass_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Number.class))
                    .as("Number class itself should be numeric")
                    .isTrue();
        }

        @Test
        void isNumericClass_withString_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericClass(String.class))
                    .as("String should not be numeric")
                    .isFalse();
        }

        @Test
        void isNumericClass_withBoolean_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Boolean.class))
                    .as("Boolean should not be numeric")
                    .isFalse();
        }

        @Test
        void isNumericClass_withBooleanPrimitive_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericClass(boolean.class))
                    .as("boolean primitive should not be numeric")
                    .isFalse();
        }

        @Test
        void isNumericClass_withObject_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericClass(Object.class))
                    .as("Object should not be numeric")
                    .isFalse();
        }

        @Test
        void isNumericClass_withLocalDate_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericClass(LocalDate.class))
                    .as("LocalDate should not be numeric")
                    .isFalse();
        }

        @Test
        void isNumericClass_withCharPrimitive_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isNumericClass(char.class))
                    .as("char primitive should not be numeric")
                    .isFalse();
        }
    }

    // ==================== isComparableType Tests ====================

    @Nested
    class IsComparableTypeTests {

        @Test
        void isComparableType_withStringField_returnsTrue() {
            LambdaExpression expr = field("name", String.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("String field should be comparable")
                    .isTrue();
        }

        @Test
        void isComparableType_withIntegerField_returnsTrue() {
            LambdaExpression expr = field("count", Integer.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("Integer field should be comparable")
                    .isTrue();
        }

        @Test
        void isComparableType_withLocalDatePath_returnsTrue() {
            PathExpression expr = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD),
                            new PathSegment("createdAt", LocalDate.class, RelationType.FIELD)),
                    LocalDate.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("LocalDate path should be comparable")
                    .isTrue();
        }

        @Test
        void isComparableType_withNullExpression_returnsFalse() {
            // null -> Object.class, which is not Comparable and not primitive
            assertThat(ExpressionTypeInferrer.isComparableType(null))
                    .as("Null expression (Object.class) should not be comparable")
                    .isFalse();
        }

        @Test
        void isComparableType_withPrimitiveIntField_returnsTrue() {
            LambdaExpression expr = field("count", int.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("int primitive should be comparable")
                    .isTrue();
        }

        @Test
        void isComparableType_withPrimitiveLongField_returnsTrue() {
            LambdaExpression expr = field("id", long.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("long primitive should be comparable")
                    .isTrue();
        }

        @Test
        void isComparableType_withPrimitiveDoubleField_returnsTrue() {
            LambdaExpression expr = field("amount", double.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("double primitive should be comparable")
                    .isTrue();
        }

        @Test
        void isComparableType_withPrimitiveBooleanField_returnsTrue() {
            LambdaExpression expr = field("active", boolean.class);

            assertThat(ExpressionTypeInferrer.isComparableType(expr))
                    .as("boolean primitive should be comparable")
                    .isTrue();
        }
    }

    // ==================== isBooleanType Tests ====================

    @Nested
    class IsBooleanTypeTests {

        @Test
        void isBooleanType_withBooleanPrimitive_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isBooleanType(boolean.class))
                    .as("boolean primitive should be boolean type")
                    .isTrue();
        }

        @Test
        void isBooleanType_withBooleanWrapper_returnsTrue() {
            assertThat(ExpressionTypeInferrer.isBooleanType(Boolean.class))
                    .as("Boolean wrapper should be boolean type")
                    .isTrue();
        }

        @Test
        void isBooleanType_withString_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isBooleanType(String.class))
                    .as("String should not be boolean type")
                    .isFalse();
        }

        @Test
        void isBooleanType_withInteger_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isBooleanType(Integer.class))
                    .as("Integer should not be boolean type")
                    .isFalse();
        }

        @Test
        void isBooleanType_withIntPrimitive_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isBooleanType(int.class))
                    .as("int primitive should not be boolean type")
                    .isFalse();
        }

        @Test
        void isBooleanType_withObject_returnsFalse() {
            assertThat(ExpressionTypeInferrer.isBooleanType(Object.class))
                    .as("Object should not be boolean type")
                    .isFalse();
        }
    }

    // ==================== extractFieldName Tests ====================

    @Nested
    class ExtractFieldNameTests {

        @Test
        void extractFieldName_withNull_returnsNull() {
            assertThat(ExpressionTypeInferrer.extractFieldName(null))
                    .as("Null method name should return null")
                    .isNull();
        }

        @Test
        void extractFieldName_withGetterPrefix_extractsFieldName() {
            assertThat(ExpressionTypeInferrer.extractFieldName("getName"))
                    .as("getName should extract 'name'")
                    .isEqualTo("name");
        }

        @Test
        void extractFieldName_withGetterPrefixUpperCase_lowercasesFirstChar() {
            assertThat(ExpressionTypeInferrer.extractFieldName("getAge"))
                    .as("getAge should extract 'age'")
                    .isEqualTo("age");
        }

        @Test
        void extractFieldName_withGetterMultiWordField_preservesCase() {
            assertThat(ExpressionTypeInferrer.extractFieldName("getFirstName"))
                    .as("getFirstName should extract 'firstName'")
                    .isEqualTo("firstName");
        }

        @Test
        void extractFieldName_withIsPrefix_extractsFieldName() {
            assertThat(ExpressionTypeInferrer.extractFieldName("isActive"))
                    .as("isActive should extract 'active'")
                    .isEqualTo("active");
        }

        @Test
        void extractFieldName_withIsPrefixMultiWord_preservesCase() {
            assertThat(ExpressionTypeInferrer.extractFieldName("isEmailVerified"))
                    .as("isEmailVerified should extract 'emailVerified'")
                    .isEqualTo("emailVerified");
        }

        @Test
        void extractFieldName_withNoPrefix_returnsAsIs() {
            assertThat(ExpressionTypeInferrer.extractFieldName("name"))
                    .as("name without prefix should return as-is")
                    .isEqualTo("name");
        }

        @Test
        void extractFieldName_withSetPrefix_returnsAsIs() {
            assertThat(ExpressionTypeInferrer.extractFieldName("setName"))
                    .as("setName should return as-is (not a getter)")
                    .isEqualTo("setName");
        }

        @Test
        void extractFieldName_withGetOnly_returnsAsIs() {
            assertThat(ExpressionTypeInferrer.extractFieldName("get"))
                    .as("'get' alone should return as-is")
                    .isEqualTo("get");
        }

        @Test
        void extractFieldName_withIsOnly_returnsAsIs() {
            assertThat(ExpressionTypeInferrer.extractFieldName("is"))
                    .as("'is' alone should return as-is")
                    .isEqualTo("is");
        }

        @Test
        void extractFieldName_withEmptyString_returnsEmpty() {
            assertThat(ExpressionTypeInferrer.extractFieldName(""))
                    .as("Empty string should return empty")
                    .isEmpty();
        }

        @Test
        void extractFieldName_withSingleUpperCaseAfterGet_lowercases() {
            assertThat(ExpressionTypeInferrer.extractFieldName("getX"))
                    .as("getX should extract 'x'")
                    .isEqualTo("x");
        }

        @Test
        void extractFieldName_withSingleUpperCaseAfterIs_lowercases() {
            assertThat(ExpressionTypeInferrer.extractFieldName("isA"))
                    .as("isA should extract 'a'")
                    .isEqualTo("a");
        }

        @Test
        void extractFieldName_withGetPrefixCaseSensitive_handlesCorrectly() {
            // "getname" does not match "get" + uppercase pattern
            assertThat(ExpressionTypeInferrer.extractFieldName("getname"))
                    .as("getname (lowercase after get) should extract 'name'")
                    .isEqualTo("name");
        }

        @ParameterizedTest
        @ValueSource(strings = { "getId", "getCount", "getTotal", "getValue" })
        void extractFieldName_withVariousGetters_extractsCorrectly(String methodName) {
            String expectedField = Character.toLowerCase(methodName.charAt(3)) +
                    methodName.substring(4);

            assertThat(ExpressionTypeInferrer.extractFieldName(methodName))
                    .as("Getter %s should extract correct field", methodName)
                    .isEqualTo(expectedField);
        }

        @ParameterizedTest
        @ValueSource(strings = { "isEnabled", "isValid", "isOpen", "isClosed" })
        void extractFieldName_withVariousIsPrefixes_extractsCorrectly(String methodName) {
            String expectedField = Character.toLowerCase(methodName.charAt(2)) +
                    methodName.substring(3);

            assertThat(ExpressionTypeInferrer.extractFieldName(methodName))
                    .as("Is-getter %s should extract correct field", methodName)
                    .isEqualTo(expectedField);
        }
    }
}
