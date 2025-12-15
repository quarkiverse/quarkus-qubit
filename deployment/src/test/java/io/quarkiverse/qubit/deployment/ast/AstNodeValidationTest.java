package io.quarkiverse.qubit.deployment.ast;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.constant;
import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge case tests for AST node validation.
 */
class AstNodeValidationTest {

    // ==================== FieldAccess Validation ====================

    @Nested
    class FieldAccessValidationTests {

        @Test
        void constructor_withNullFieldName_throwsNullPointerException() {
            assertThatThrownBy(() -> new FieldAccess(null, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field name cannot be null");
        }

        @Test
        void constructor_withNullFieldType_throwsNullPointerException() {
            assertThatThrownBy(() -> new FieldAccess("name", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field type cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            FieldAccess access = new FieldAccess("name", String.class);
            assertThat(access.fieldName()).isEqualTo("name");
            assertThat(access.fieldType()).isEqualTo(String.class);
        }

        @Test
        void getFieldName_returnsFieldName() {
            FieldAccess access = new FieldAccess("firstName", String.class);
            assertThat(access.getFieldName()).isPresent().contains("firstName");
        }
    }

    // ==================== MethodCall Validation ====================

    @Nested
    class MethodCallValidationTests {

        @Test
        void constructor_withNullMethodName_throwsNullPointerException() {
            assertThatThrownBy(() -> new MethodCall(field("target", Object.class), null, List.of(), String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Method name cannot be null");
        }

        @Test
        void constructor_withNullReturnType_throwsNullPointerException() {
            assertThatThrownBy(() -> new MethodCall(field("target", Object.class), "methodName", List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Return type cannot be null");
        }

        @Test
        void constructor_withNullArguments_throwsNullPointerException() {
            assertThatThrownBy(() -> new MethodCall(null, "methodName", null, String.class))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_createsDefensiveCopyOfArguments() {
            List<LambdaExpression> mutableArgs = new ArrayList<>();
            mutableArgs.add(constant(1));

            MethodCall call = new MethodCall(null, "test", mutableArgs, String.class);

            // Modifying original list should not affect the MethodCall
            mutableArgs.add(constant(2));
            assertThat(call.arguments()).hasSize(1);
        }

        @Test
        void arguments_returnsImmutableList() {
            MethodCall call = new MethodCall(null, "test", List.of(constant(1)), String.class);

            assertThatThrownBy(() -> call.arguments().add(constant(2)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== Constant Validation ====================

    @Nested
    class ConstantValidationTests {

        @Test
        void constructor_withNullType_throwsNullPointerException() {
            assertThatThrownBy(() -> new Constant(42, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Type cannot be null");
        }

        @Test
        void constructor_withNullValue_succeeds() {
            // Null value is allowed (for null constants)
            Constant constant = new Constant(null, String.class);
            assertThat(constant.value()).isNull();
            assertThat(constant.type()).isEqualTo(String.class);
        }

        @Test
        void predefinedConstants_haveCorrectValues() {
            assertThat(Constant.TRUE.value()).isEqualTo(true);
            assertThat(Constant.TRUE.type()).isEqualTo(boolean.class);

            assertThat(Constant.FALSE.value()).isEqualTo(false);
            assertThat(Constant.FALSE.type()).isEqualTo(boolean.class);

            assertThat(Constant.ZERO_INT.value()).isEqualTo(0);
            assertThat(Constant.ZERO_INT.type()).isEqualTo(int.class);
        }
    }

    // ==================== Parameter Validation ====================

    @Nested
    class ParameterValidationTests {

        @Test
        void constructor_withNullName_throwsNullPointerException() {
            assertThatThrownBy(() -> new Parameter(null, String.class, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Parameter name cannot be null");
        }

        @Test
        void constructor_withNullType_throwsNullPointerException() {
            assertThatThrownBy(() -> new Parameter("param", null, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Parameter type cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            Parameter param = new Parameter("entity", Object.class, 0);
            assertThat(param.name()).isEqualTo("entity");
            assertThat(param.type()).isEqualTo(Object.class);
            assertThat(param.index()).isZero();
        }
    }

    // ==================== CapturedVariable Validation ====================

    @Nested
    class CapturedVariableValidationTests {

        @Test
        void constructor_withNegativeIndex_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new CapturedVariable(-1, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CapturedVariable index must be non-negative")
                    .hasMessageContaining("-1");
        }

        @Test
        void constructor_withNullType_throwsNullPointerException() {
            assertThatThrownBy(() -> new CapturedVariable(0, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Type cannot be null");
        }

        @Test
        void constructor_withZeroIndex_succeeds() {
            CapturedVariable var = new CapturedVariable(0, String.class);
            assertThat(var.index()).isZero();
            assertThat(var.type()).isEqualTo(String.class);
        }

        @Test
        void constructor_withPositiveIndex_succeeds() {
            CapturedVariable var = new CapturedVariable(5, Integer.class);
            assertThat(var.index()).isEqualTo(5);
        }
    }

    // ==================== NullLiteral Validation ====================

    @Nested
    class NullLiteralValidationTests {

        @Test
        void constructor_withNullExpectedType_succeeds() {
            // NullLiteral allows null expectedType
            NullLiteral literal = new NullLiteral(null);
            assertThat(literal.expectedType()).isNull();
        }

        @Test
        void constructor_withExpectedType_succeeds() {
            NullLiteral literal = new NullLiteral(String.class);
            assertThat(literal.expectedType()).isEqualTo(String.class);
        }
    }

    // ==================== Cast Validation ====================

    @Nested
    class CastValidationTests {

        @Test
        void constructor_withNullExpression_throwsNullPointerException() {
            assertThatThrownBy(() -> new Cast(null, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Expression cannot be null");
        }

        @Test
        void constructor_withNullTargetType_throwsNullPointerException() {
            assertThatThrownBy(() -> new Cast(constant(42), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Target type cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            Cast cast = new Cast(constant(42), Long.class);
            assertThat(cast.targetType()).isEqualTo(Long.class);
        }
    }

    // ==================== InstanceOf Validation ====================

    @Nested
    class InstanceOfValidationTests {

        @Test
        void constructor_withNullExpression_throwsNullPointerException() {
            assertThatThrownBy(() -> new InstanceOf(null, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Expression cannot be null");
        }

        @Test
        void constructor_withNullTargetType_throwsNullPointerException() {
            assertThatThrownBy(() -> new InstanceOf(field("value", Object.class), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Target type cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            InstanceOf check = new InstanceOf(field("value", Object.class), Number.class);
            assertThat(check.targetType()).isEqualTo(Number.class);
        }
    }

    // ==================== Conditional Validation ====================

    @Nested
    class ConditionalValidationTests {

        @Test
        void constructor_withNullCondition_throwsNullPointerException() {
            assertThatThrownBy(() -> new Conditional(null, constant(1), constant(2)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Condition cannot be null");
        }

        @Test
        void constructor_withNullTrueValue_throwsNullPointerException() {
            assertThatThrownBy(() -> new Conditional(Constant.TRUE, null, constant(2)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("True value cannot be null");
        }

        @Test
        void constructor_withNullFalseValue_throwsNullPointerException() {
            assertThatThrownBy(() -> new Conditional(Constant.TRUE, constant(1), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("False value cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            Conditional cond = new Conditional(Constant.TRUE, constant(1), constant(2));
            assertThat(cond.condition()).isEqualTo(Constant.TRUE);
        }
    }

    // ==================== ConstructorCall Validation ====================

    @Nested
    class ConstructorCallValidationTests {

        @Test
        void constructor_withNullClassName_throwsNullPointerException() {
            assertThatThrownBy(() -> new ConstructorCall(null, List.of(), Object.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Class name cannot be null");
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            assertThatThrownBy(() -> new ConstructorCall("com/example/DTO", List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void constructor_createsDefensiveCopyOfArguments() {
            List<LambdaExpression> mutableArgs = new ArrayList<>();
            mutableArgs.add(constant("value"));

            ConstructorCall call = new ConstructorCall("DTO", mutableArgs, Object.class);

            mutableArgs.add(constant(42));
            assertThat(call.arguments()).hasSize(1);
        }

        @Test
        void arguments_returnsImmutableList() {
            ConstructorCall call = new ConstructorCall("DTO", List.of(constant(1)), Object.class);

            assertThatThrownBy(() -> call.arguments().add(constant(2)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== ArrayCreation Validation ====================

    @Nested
    class ArrayCreationValidationTests {

        @Test
        void constructor_withNullElementType_throwsNullPointerException() {
            assertThatThrownBy(() -> new ArrayCreation(null, List.of(), Object[].class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Element type cannot be null");
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            assertThatThrownBy(() -> new ArrayCreation("java/lang/Object", List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void constructor_createsDefensiveCopyOfElements() {
            List<LambdaExpression> mutableElements = new ArrayList<>();
            mutableElements.add(constant(1));

            ArrayCreation array = new ArrayCreation("java/lang/Object", mutableElements, Object[].class);

            mutableElements.add(constant(2));
            assertThat(array.elements()).hasSize(1);
        }

        @Test
        void isObjectArray_withObjectElementType_returnsTrue() {
            ArrayCreation array = new ArrayCreation("java/lang/Object", List.of(), Object[].class);
            assertThat(array.isObjectArray()).isTrue();
        }

        @Test
        void isObjectArray_withOtherElementType_returnsFalse() {
            ArrayCreation array = new ArrayCreation("java/lang/String", List.of(), String[].class);
            assertThat(array.isObjectArray()).isFalse();
        }
    }

    // ==================== PathSegment Validation ====================

    @Nested
    class PathSegmentValidationTests {

        @Test
        void constructor_withNullFieldName_throwsNullPointerException() {
            assertThatThrownBy(() -> new PathSegment(null, String.class, RelationType.FIELD))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field name cannot be null");
        }

        @Test
        void constructor_withBlankFieldName_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PathSegment("", String.class, RelationType.FIELD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Field name cannot be empty or blank");
        }

        @Test
        void constructor_withWhitespaceFieldName_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PathSegment("   ", String.class, RelationType.FIELD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Field name cannot be empty or blank");
        }

        @Test
        void constructor_withNullFieldType_throwsNullPointerException() {
            assertThatThrownBy(() -> new PathSegment("field", null, RelationType.FIELD))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field type cannot be null");
        }

        @Test
        void constructor_withNullRelationType_throwsNullPointerException() {
            assertThatThrownBy(() -> new PathSegment("field", String.class, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Relation type cannot be null");
        }

        @Test
        void requiresJoin_withFieldType_returnsFalse() {
            PathSegment segment = new PathSegment("name", String.class, RelationType.FIELD);
            assertThat(segment.requiresJoin()).isFalse();
        }

        @Test
        void requiresJoin_withManyToOne_returnsTrue() {
            PathSegment segment = new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE);
            assertThat(segment.requiresJoin()).isTrue();
        }

        @Test
        void requiresJoin_withOneToMany_returnsTrue() {
            PathSegment segment = new PathSegment("phones", List.class, RelationType.ONE_TO_MANY);
            assertThat(segment.requiresJoin()).isTrue();
        }
    }

    // ==================== SegmentBasedPath Validation ====================

    @Nested
    class SegmentBasedPathValidationTests {

        @Test
        void validateSegments_withNullSegments_throwsNullPointerException() {
            assertThatThrownBy(() -> LambdaExpression.SegmentBasedPath.validateSegments(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Segments cannot be null");
        }

        @Test
        void validateSegments_withEmptySegments_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> LambdaExpression.SegmentBasedPath.validateSegments(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Path expression must have at least one segment");
        }

        @Test
        void validateSegments_withValidSegments_returnsImmutableCopy() {
            List<PathSegment> mutableSegments = new ArrayList<>();
            mutableSegments.add(new PathSegment("field", String.class, RelationType.FIELD));

            List<PathSegment> result = LambdaExpression.SegmentBasedPath.validateSegments(mutableSegments);

            // Verify defensive copy
            mutableSegments.add(new PathSegment("other", String.class, RelationType.FIELD));
            assertThat(result)
                    .as("validateSegments should return an immutable copy")
                    .hasSize(1);

            // Verify immutability
            assertThatThrownBy(() -> result.add(new PathSegment("x", String.class, RelationType.FIELD)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== PathExpression Validation ====================

    @Nested
    class PathExpressionValidationTests {

        @Test
        void constructor_withNullSegments_throwsNullPointerException() {
            assertThatThrownBy(() -> new PathExpression(null, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Segments cannot be null");
        }

        @Test
        void constructor_withEmptySegments_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PathExpression(List.of(), String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Path expression must have at least one segment");
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            PathSegment segment = new PathSegment("field", String.class, RelationType.FIELD);
            assertThatThrownBy(() -> new PathExpression(List.of(segment), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void constructor_createsDefensiveCopy() {
            List<PathSegment> mutableSegments = new ArrayList<>();
            mutableSegments.add(new PathSegment("field", String.class, RelationType.FIELD));

            PathExpression path = new PathExpression(mutableSegments, String.class);

            mutableSegments.add(new PathSegment("other", String.class, RelationType.FIELD));
            assertThat(path.segments()).hasSize(1);
        }

        @Test
        void depth_returnsSegmentCount() {
            PathExpression single = PathExpression.field("name", String.class);
            assertThat(single.depth()).isEqualTo(1);

            PathExpression multi = new PathExpression(
                    List.of(
                            new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)
                    ),
                    String.class
            );
            assertThat(multi.depth()).isEqualTo(2);
        }

        @Test
        void finalSegment_returnsLastSegment() {
            PathExpression path = new PathExpression(
                    List.of(
                            new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("firstName", String.class, RelationType.FIELD)
                    ),
                    String.class
            );

            assertThat(path.finalSegment().fieldName()).isEqualTo("firstName");
        }

        @Test
        void requiresJoins_withFieldOnlyPath_returnsFalse() {
            PathExpression path = PathExpression.field("name", String.class);
            assertThat(path.requiresJoins()).isFalse();
        }

        @Test
        void requiresJoins_withRelationshipPath_returnsTrue() {
            PathExpression path = PathExpression.single("owner", Object.class, RelationType.MANY_TO_ONE);
            assertThat(path.requiresJoins()).isTrue();
        }

        @Test
        void getFieldName_returnsFirstSegmentFieldName() {
            PathExpression path = new PathExpression(
                    List.of(
                            new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE),
                            new PathSegment("name", String.class, RelationType.FIELD)
                    ),
                    String.class
            );

            assertThat(path.getFieldName()).isPresent().contains("owner");
        }

        // Note: getFieldName_withEmptySegments test is NOT possible because the constructor
        // validates that segments cannot be empty. The segments.isEmpty() check in getFieldName()
        // is dead code - an EQUIVALENT MUTATION that cannot be killed.
    }

    // ==================== InExpression Validation ====================

    @Nested
    class InExpressionValidationTests {

        @Test
        void constructor_withNullField_throwsNullPointerException() {
            assertThatThrownBy(() -> new InExpression(null, constant(List.of()), false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field cannot be null");
        }

        @Test
        void constructor_withNullCollection_throwsNullPointerException() {
            assertThatThrownBy(() -> new InExpression(field("city", String.class), null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Collection cannot be null");
        }

        @Test
        void in_createsNonNegatedExpression() {
            InExpression expr = InExpression.in(field("city", String.class), constant(List.of()));
            assertThat(expr.negated()).isFalse();
        }

        @Test
        void notIn_createsNegatedExpression() {
            InExpression expr = InExpression.notIn(field("city", String.class), constant(List.of()));
            assertThat(expr.negated()).isTrue();
        }
    }

    // ==================== MemberOfExpression Validation ====================

    @Nested
    class MemberOfExpressionValidationTests {

        @Test
        void constructor_withNullValue_throwsNullPointerException() {
            assertThatThrownBy(() -> new MemberOfExpression(null, field("roles", List.class), false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Value cannot be null");
        }

        @Test
        void constructor_withNullCollectionField_throwsNullPointerException() {
            assertThatThrownBy(() -> new MemberOfExpression(constant("admin"), null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Collection field cannot be null");
        }

        @Test
        void memberOf_createsNonNegatedExpression() {
            MemberOfExpression expr = MemberOfExpression.memberOf(constant("admin"), field("roles", List.class));
            assertThat(expr.negated()).isFalse();
        }

        @Test
        void notMemberOf_createsNegatedExpression() {
            MemberOfExpression expr = MemberOfExpression.notMemberOf(constant("admin"), field("roles", List.class));
            assertThat(expr.negated()).isTrue();
        }
    }

    // ==================== BiEntityParameter Validation ====================

    @Nested
    class BiEntityParameterValidationTests {

        @Test
        void constructor_withNullName_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityParameter(null, Object.class, 0, EntityPosition.FIRST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Parameter name cannot be null");
        }

        @Test
        void constructor_withNullType_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityParameter("entity", null, 0, EntityPosition.FIRST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Parameter type cannot be null");
        }

        @Test
        void constructor_withNullPosition_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityParameter("entity", Object.class, 0, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity position cannot be null");
        }

        @Test
        void isFirstEntity_withFirstPosition_returnsTrue() {
            BiEntityParameter param = new BiEntityParameter("p", Object.class, 0, EntityPosition.FIRST);
            assertThat(param.isFirstEntity()).isTrue();
            assertThat(param.isSecondEntity()).isFalse();
        }

        @Test
        void isSecondEntity_withSecondPosition_returnsTrue() {
            BiEntityParameter param = new BiEntityParameter("ph", Object.class, 1, EntityPosition.SECOND);
            assertThat(param.isSecondEntity()).isTrue();
            assertThat(param.isFirstEntity()).isFalse();
        }
    }

    // ==================== BiEntityFieldAccess Validation ====================

    @Nested
    class BiEntityFieldAccessValidationTests {

        @Test
        void constructor_withNullFieldName_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityFieldAccess(null, String.class, EntityPosition.FIRST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field name cannot be null");
        }

        @Test
        void constructor_withNullFieldType_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityFieldAccess("name", null, EntityPosition.FIRST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field type cannot be null");
        }

        @Test
        void constructor_withNullEntityPosition_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityFieldAccess("name", String.class, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity position cannot be null");
        }

        @Test
        void fromFirst_createsFieldWithFirstPosition() {
            BiEntityFieldAccess access = BiEntityFieldAccess.fromFirst("name", String.class);
            assertThat(access.isFromFirstEntity())
                    .as("FIRST position should return true for isFromFirstEntity()")
                    .isTrue();
            assertThat(access.isFromSecondEntity())
                    .as("FIRST position should return false for isFromSecondEntity()")
                    .isFalse();
            assertThat(access.entityPosition()).isEqualTo(EntityPosition.FIRST);
        }

        @Test
        void fromSecond_createsFieldWithSecondPosition() {
            BiEntityFieldAccess access = BiEntityFieldAccess.fromSecond("type", String.class);
            assertThat(access.isFromSecondEntity())
                    .as("SECOND position should return true for isFromSecondEntity()")
                    .isTrue();
            assertThat(access.isFromFirstEntity())
                    .as("SECOND position should return false for isFromFirstEntity()")
                    .isFalse();
            assertThat(access.entityPosition()).isEqualTo(EntityPosition.SECOND);
        }

        @Test
        void getFieldName_returnsFieldName() {
            BiEntityFieldAccess access = new BiEntityFieldAccess("phone", Object.class, EntityPosition.SECOND);
            assertThat(access.getFieldName()).isPresent().contains("phone");
        }
    }

    // ==================== BiEntityPathExpression Validation ====================

    @Nested
    class BiEntityPathExpressionValidationTests {

        @Test
        void constructor_withNullSegments_throwsNullPointerException() {
            assertThatThrownBy(() -> new BiEntityPathExpression(null, String.class, EntityPosition.FIRST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Segments cannot be null");
        }

        @Test
        void constructor_withEmptySegments_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new BiEntityPathExpression(List.of(), String.class, EntityPosition.FIRST))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Path expression must have at least one segment");
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            PathSegment segment = new PathSegment("field", String.class, RelationType.FIELD);
            assertThatThrownBy(() -> new BiEntityPathExpression(List.of(segment), null, EntityPosition.FIRST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void constructor_withNullEntityPosition_throwsNullPointerException() {
            PathSegment segment = new PathSegment("field", String.class, RelationType.FIELD);
            assertThatThrownBy(() -> new BiEntityPathExpression(List.of(segment), String.class, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity position cannot be null");
        }

        @Test
        void fromFirst_createsPathWithFirstPosition() {
            List<PathSegment> segments = List.of(
                    new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE)
            );
            BiEntityPathExpression path = BiEntityPathExpression.fromFirst(segments, Object.class);
            assertThat(path.isFromFirstEntity())
                    .as("FIRST position should return true for isFromFirstEntity()")
                    .isTrue();
            assertThat(path.isFromSecondEntity())
                    .as("FIRST position should return false for isFromSecondEntity()")
                    .isFalse();
        }

        @Test
        void fromSecond_createsPathWithSecondPosition() {
            List<PathSegment> segments = List.of(
                    new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE)
            );
            BiEntityPathExpression path = BiEntityPathExpression.fromSecond(segments, Object.class);
            assertThat(path.isFromSecondEntity())
                    .as("SECOND position should return true for isFromSecondEntity()")
                    .isTrue();
            assertThat(path.isFromFirstEntity())
                    .as("SECOND position should return false for isFromFirstEntity()")
                    .isFalse();
        }

        @Test
        void getFieldName_returnsFirstSegmentFieldName() {
            List<PathSegment> segments = List.of(
                    new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE),
                    new PathSegment("name", String.class, RelationType.FIELD)
            );
            BiEntityPathExpression path = BiEntityPathExpression.fromFirst(segments, String.class);

            assertThat(path.getFieldName())
                    .as("getFieldName should return the first segment's field name")
                    .isPresent()
                    .contains("owner");
        }

        @Test
        void getFieldName_withSingleSegment_returnsFieldName() {
            List<PathSegment> segments = List.of(
                    new PathSegment("department", Object.class, RelationType.MANY_TO_ONE)
            );
            BiEntityPathExpression path = BiEntityPathExpression.fromSecond(segments, Object.class);

            assertThat(path.getFieldName())
                    .as("getFieldName with single segment should return that segment's field name")
                    .isPresent()
                    .contains("department");
        }

        // Note: Cannot test getFieldName with empty segments because the constructor
        // validates that segments cannot be empty. The dead code (isEmpty() check) was
        // removed from getFieldName() - it now directly accesses segments.getFirst().
    }

    // ==================== GroupKeyReference Validation ====================

    @Nested
    class GroupKeyReferenceValidationTests {

        @Test
        void constructor_withNullKeyExpression_succeeds() {
            // keyExpression can be null (resolved at code generation time)
            GroupKeyReference ref = new GroupKeyReference(null, String.class);
            assertThat(ref.keyExpression()).isNull();
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupKeyReference(field("dept", String.class), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            GroupKeyReference ref = new GroupKeyReference(field("department", String.class), String.class);
            assertThat(ref.resultType()).isEqualTo(String.class);
        }
    }

    // ==================== GroupAggregation Validation ====================

    @Nested
    class GroupAggregationValidationTests {

        @Test
        void constructor_withNullAggregationType_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupAggregation(null, null, Long.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Aggregation type cannot be null");
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupAggregation(GroupAggregationType.COUNT, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void count_createsCountAggregation() {
            GroupAggregation agg = GroupAggregation.count();
            assertThat(agg.aggregationType()).isEqualTo(GroupAggregationType.COUNT);
            assertThat(agg.fieldExpression()).isNull();
            assertThat(agg.resultType()).isEqualTo(long.class);
        }

        @Test
        void avg_createsAvgAggregation() {
            GroupAggregation agg = GroupAggregation.avg(field("salary", Double.class));
            assertThat(agg.aggregationType()).isEqualTo(GroupAggregationType.AVG);
            assertThat(agg.resultType()).isEqualTo(Double.class);
        }

        @Test
        void requiresField_forCount_returnsFalse() {
            assertThat(GroupAggregation.count().requiresField()).isFalse();
        }

        @Test
        void requiresField_forAvg_returnsTrue() {
            assertThat(GroupAggregation.avg(field("salary", Double.class)).requiresField()).isTrue();
        }
    }

    // ==================== GroupParameter Validation ====================

    @Nested
    class GroupParameterValidationTests {

        @Test
        void constructor_withNullName_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupParameter(null, Object.class, 0, Object.class, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Parameter name cannot be null");
        }

        @Test
        void constructor_withNullType_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupParameter("g", null, 0, Object.class, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Parameter type cannot be null");
        }

        @Test
        void constructor_withNullEntityType_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupParameter("g", Object.class, 0, null, String.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity type cannot be null");
        }

        @Test
        void constructor_withNullKeyType_throwsNullPointerException() {
            assertThatThrownBy(() -> new GroupParameter("g", Object.class, 0, Object.class, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Key type cannot be null");
        }
    }

    // ==================== SubqueryBuilderReference Validation ====================

    @Nested
    class SubqueryBuilderReferenceValidationTests {

        @Test
        void constructor_withNullEntityClass_throwsNullPointerException() {
            assertThatThrownBy(() -> new SubqueryBuilderReference(null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity class cannot be null");
        }

        @Test
        void singleArgConstructor_succeeds() {
            SubqueryBuilderReference ref = new SubqueryBuilderReference(Object.class);
            assertThat(ref.entityClass()).isEqualTo(Object.class);
            assertThat(ref.entityClassName()).isNull();
            assertThat(ref.predicate()).isNull();
        }

        @Test
        void hasPredicate_withNoPredicate_returnsFalse() {
            SubqueryBuilderReference ref = new SubqueryBuilderReference(Object.class);
            assertThat(ref.hasPredicate())
                    .as("No predicate should return false for hasPredicate()")
                    .isFalse();
        }

        @Test
        void withPredicate_addsPredicate() {
            SubqueryBuilderReference ref = new SubqueryBuilderReference(Object.class);
            SubqueryBuilderReference withPred = ref.withPredicate(Constant.TRUE);

            assertThat(withPred.hasPredicate())
                    .as("After adding predicate, hasPredicate() should be true")
                    .isTrue();
            assertThat(withPred.predicate()).isEqualTo(Constant.TRUE);
        }

        @Test
        void withPredicate_combinesWithExistingPredicate() {
            SubqueryBuilderReference ref = new SubqueryBuilderReference(Object.class, Constant.TRUE);
            SubqueryBuilderReference combined = ref.withPredicate(Constant.FALSE);

            assertThat(combined.predicate()).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) combined.predicate();
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.AND);
        }

        @Test
        void withPredicate_withNullPredicate_throwsNullPointerException() {
            SubqueryBuilderReference ref = new SubqueryBuilderReference(Object.class);
            assertThatThrownBy(() -> ref.withPredicate(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("New predicate cannot be null");
        }
    }

    // ==================== ScalarSubquery Validation ====================

    @Nested
    class ScalarSubqueryValidationTests {

        @Test
        void constructor_withNullAggregationType_throwsNullPointerException() {
            assertThatThrownBy(() -> new ScalarSubquery(null, Object.class, null, null, null, Double.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Aggregation type cannot be null");
        }

        @Test
        void constructor_withNullEntityClass_throwsNullPointerException() {
            assertThatThrownBy(() -> new ScalarSubquery(SubqueryAggregationType.AVG, null, null, null, null, Double.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity class cannot be null");
        }

        @Test
        void constructor_withNullResultType_throwsNullPointerException() {
            assertThatThrownBy(() -> new ScalarSubquery(SubqueryAggregationType.AVG, Object.class, null, null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result type cannot be null");
        }

        @Test
        void avg_createsAvgSubquery() {
            ScalarSubquery sub = ScalarSubquery.avg(Object.class, field("salary", Double.class), null);
            assertThat(sub.aggregationType()).isEqualTo(SubqueryAggregationType.AVG);
            assertThat(sub.resultType()).isEqualTo(Double.class);
            assertThat(sub.isCount())
                    .as("AVG is not COUNT, isCount() should be false")
                    .isFalse();
        }

        @Test
        void count_createsCountSubquery() {
            ScalarSubquery sub = ScalarSubquery.count(Object.class, null);
            assertThat(sub.aggregationType()).isEqualTo(SubqueryAggregationType.COUNT);
            assertThat(sub.isCount())
                    .as("COUNT should return true for isCount()")
                    .isTrue();
            assertThat(sub.fieldExpression()).isNull();
        }

        @Test
        void hasPredicate_withNoPredicate_returnsFalse() {
            ScalarSubquery sub = ScalarSubquery.count(Object.class, null);
            assertThat(sub.hasPredicate()).isFalse();
        }

        @Test
        void hasPredicate_withPredicate_returnsTrue() {
            ScalarSubquery sub = ScalarSubquery.count(Object.class, Constant.TRUE);
            assertThat(sub.hasPredicate()).isTrue();
        }

        @Test
        void sum_createsSumSubquery() {
            ScalarSubquery sub = ScalarSubquery.sum(Object.class, field("salary", Double.class), null, Double.class);
            assertThat(sub)
                    .as("sum() should create a non-null ScalarSubquery")
                    .isNotNull();
            assertThat(sub.aggregationType())
                    .as("sum() should create SUM aggregation type")
                    .isEqualTo(SubqueryAggregationType.SUM);
            assertThat(sub.resultType())
                    .as("sum() should have correct result type")
                    .isEqualTo(Double.class);
            assertThat(sub.fieldExpression())
                    .as("sum() should have field expression")
                    .isNotNull();
        }

        @Test
        void min_createsMinSubquery() {
            ScalarSubquery sub = ScalarSubquery.min(Object.class, field("age", Integer.class), null, Integer.class);
            assertThat(sub)
                    .as("min() should create a non-null ScalarSubquery")
                    .isNotNull();
            assertThat(sub.aggregationType())
                    .as("min() should create MIN aggregation type")
                    .isEqualTo(SubqueryAggregationType.MIN);
            assertThat(sub.resultType())
                    .as("min() should have correct result type")
                    .isEqualTo(Integer.class);
        }

        @Test
        void max_createsMaxSubquery() {
            ScalarSubquery sub = ScalarSubquery.max(Object.class, field("age", Integer.class), null, Integer.class);
            assertThat(sub)
                    .as("max() should create a non-null ScalarSubquery")
                    .isNotNull();
            assertThat(sub.aggregationType())
                    .as("max() should create MAX aggregation type")
                    .isEqualTo(SubqueryAggregationType.MAX);
            assertThat(sub.resultType())
                    .as("max() should have correct result type")
                    .isEqualTo(Integer.class);
        }
    }

    // ==================== ExistsSubquery Validation ====================

    @Nested
    class ExistsSubqueryValidationTests {

        @Test
        void constructor_withNullEntityClass_throwsNullPointerException() {
            assertThatThrownBy(() -> new ExistsSubquery(null, null, Constant.TRUE, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity class cannot be null");
        }

        @Test
        void constructor_withNullPredicate_throwsNullPointerException() {
            assertThatThrownBy(() -> new ExistsSubquery(Object.class, null, null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Predicate cannot be null");
        }

        @Test
        void exists_createsNonNegatedSubquery() {
            ExistsSubquery sub = ExistsSubquery.exists(Object.class, Constant.TRUE);
            assertThat(sub.negated()).isFalse();
        }

        @Test
        void notExists_createsNegatedSubquery() {
            ExistsSubquery sub = ExistsSubquery.notExists(Object.class, Constant.TRUE);
            assertThat(sub.negated()).isTrue();
        }
    }

    // ==================== InSubquery Validation ====================

    @Nested
    class InSubqueryValidationTests {

        @Test
        void constructor_withNullField_throwsNullPointerException() {
            assertThatThrownBy(() -> new InSubquery(null, Object.class, null, field("id", Long.class), null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field cannot be null");
        }

        @Test
        void constructor_withNullEntityClass_throwsNullPointerException() {
            assertThatThrownBy(() -> new InSubquery(field("id", Long.class), null, null, field("id", Long.class), null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Entity class cannot be null");
        }

        @Test
        void constructor_withNullSelectExpression_throwsNullPointerException() {
            assertThatThrownBy(() -> new InSubquery(field("id", Long.class), Object.class, null, null, null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Select expression cannot be null");
        }

        @Test
        void in_createsNonNegatedSubquery() {
            InSubquery sub = InSubquery.in(field("id", Long.class), Object.class, field("id", Long.class), null);
            assertThat(sub.negated()).isFalse();
        }

        @Test
        void notIn_createsNegatedSubquery() {
            InSubquery sub = InSubquery.notIn(field("id", Long.class), Object.class, field("id", Long.class), null);
            assertThat(sub.negated()).isTrue();
        }

        @Test
        void hasPredicate_withNoPredicate_returnsFalse() {
            InSubquery sub = InSubquery.in(field("id", Long.class), Object.class, field("id", Long.class), null);
            assertThat(sub.hasPredicate())
                    .as("No predicate should return false for hasPredicate()")
                    .isFalse();
        }

        @Test
        void hasPredicate_withPredicate_returnsTrue() {
            InSubquery sub = InSubquery.in(field("id", Long.class), Object.class, field("id", Long.class), Constant.TRUE);
            assertThat(sub.hasPredicate())
                    .as("With predicate should return true for hasPredicate()")
                    .isTrue();
        }
    }

    // ==================== CorrelatedVariable Validation ====================

    @Nested
    class CorrelatedVariableValidationTests {

        @Test
        void constructor_withNullFieldExpression_throwsNullPointerException() {
            assertThatThrownBy(() -> new CorrelatedVariable(null, 0, Object.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Field expression cannot be null");
        }

        @Test
        void constructor_withNullOuterEntityType_throwsNullPointerException() {
            assertThatThrownBy(() -> new CorrelatedVariable(field("id", Long.class), 0, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Outer entity type cannot be null");
        }

        @Test
        void constructor_withValidArgs_succeeds() {
            CorrelatedVariable var = new CorrelatedVariable(field("id", Long.class), 0, Object.class);
            assertThat(var.outerParameterIndex()).isZero();
            assertThat(var.outerEntityType()).isEqualTo(Object.class);
        }
    }

    // ==================== BinaryOp Factory Methods ====================

    @Nested
    class BinaryOpFactoryMethodTests {

        private final LambdaExpression left = constant(5);
        private final LambdaExpression right = constant(10);

        @Test
        void and_createsAndOperator() {
            BinaryOp op = BinaryOp.and(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.AND);
        }

        @Test
        void or_createsOrOperator() {
            BinaryOp op = BinaryOp.or(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.OR);
        }

        @Test
        void eq_createsEqOperator() {
            BinaryOp op = BinaryOp.eq(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.EQ);
        }

        @Test
        void ne_createsNeOperator() {
            BinaryOp op = BinaryOp.ne(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.NE);
        }

        @Test
        void lt_createsLtOperator() {
            BinaryOp op = BinaryOp.lt(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.LT);
        }

        @Test
        void le_createsLeOperator() {
            BinaryOp op = BinaryOp.le(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.LE);
        }

        @Test
        void gt_createsGtOperator() {
            BinaryOp op = BinaryOp.gt(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.GT);
        }

        @Test
        void ge_createsGeOperator() {
            BinaryOp op = BinaryOp.ge(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.GE);
        }

        @Test
        void add_createsAddOperator() {
            BinaryOp op = BinaryOp.add(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.ADD);
        }

        @Test
        void sub_createsSubOperator() {
            BinaryOp op = BinaryOp.sub(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.SUB);
        }

        @Test
        void mul_createsMulOperator() {
            BinaryOp op = BinaryOp.mul(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.MUL);
        }

        @Test
        void div_createsDivOperator() {
            BinaryOp op = BinaryOp.div(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.DIV);
        }

        @Test
        void mod_createsModOperator() {
            BinaryOp op = BinaryOp.mod(left, right);
            assertThat(op.operator()).isEqualTo(BinaryOp.Operator.MOD);
        }

        @Test
        void operatorSymbol_returnsCorrectSymbols() {
            assertThat(BinaryOp.Operator.EQ.symbol()).isEqualTo("==");
            assertThat(BinaryOp.Operator.NE.symbol()).isEqualTo("!=");
            assertThat(BinaryOp.Operator.AND.symbol()).isEqualTo("&&");
            assertThat(BinaryOp.Operator.OR.symbol()).isEqualTo("||");
            assertThat(BinaryOp.Operator.ADD.symbol()).isEqualTo("+");
        }
    }

    // ==================== UnaryOp Factory Methods ====================

    @Nested
    class UnaryOpFactoryMethodTests {

        @Test
        void not_createsNotOperator() {
            UnaryOp op = UnaryOp.not(Constant.TRUE);
            assertThat(op.operator()).isEqualTo(UnaryOp.Operator.NOT);
        }

        @Test
        void operatorSymbol_returnsCorrectSymbol() {
            assertThat(UnaryOp.Operator.NOT.symbol()).isEqualTo("!");
        }
    }

    // ==================== getFieldName Default Behavior ====================

    @Nested
    class GetFieldNameDefaultBehaviorTests {

        @Test
        void constant_returnsEmpty() {
            assertThat(constant(42).getFieldName()).isEmpty();
        }

        @Test
        void binaryOp_returnsEmpty() {
            BinaryOp op = BinaryOp.add(constant(1), constant(2));
            assertThat(op.getFieldName()).isEmpty();
        }

        @Test
        void getFieldNameOrThrow_onConstant_throwsException() {
            assertThatThrownBy(() -> constant(42).getFieldNameOrThrow())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot extract field name from expression");
        }

        @Test
        void getFieldNameOrThrow_onFieldAccess_returnsFieldName() {
            String result = field("name", String.class).getFieldNameOrThrow();
            assertThat(result).isEqualTo("name");
        }
    }
}
