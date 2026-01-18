package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_NOT_NULL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_NULL;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable;

/**
 * Shared expression generation methods for BiEntityExpressionBuilder and GroupExpressionBuilder.
 * Extracted to avoid circular dependencies with CriteriaExpressionGenerator.
 */
public interface ExpressionGeneratorHelper {

    /** Generates JPA field access expression. */
    ResultHandle generateFieldAccess(MethodCreator method, LambdaExpression.FieldAccess field, ResultHandle root);

    /** Generates JPA path expression for relationship navigation. */
    ResultHandle generatePathExpression(MethodCreator method, LambdaExpression.PathExpression pathExpr, ResultHandle root);

    /** Generates constant value bytecode. */
    ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant);

    /** Wraps value as literal Expression using cb.literal(). */
    ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value);

    /** Combines two predicates with AND or OR. */
    ResultHandle combinePredicates(MethodCreator method, ResultHandle cb, ResultHandle left, ResultHandle right,
            LambdaExpression.BinaryOp.Operator operator);

    /** Generates comparison operation (EQ, NE, GT, GE, LT, LE). */
    ResultHandle generateComparisonOperation(MethodCreator method, LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb, ResultHandle left, ResultHandle right);

    /** Generates arithmetic operation (ADD, SUB, MUL, DIV, MOD). */
    ResultHandle generateArithmeticOperation(MethodCreator method, LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb, ResultHandle left, ResultHandle right);

    /** Checks if binary operation is string concatenation. */
    boolean isStringConcatenation(LambdaExpression.BinaryOp binOp);

    /** Generates string concatenation using cb.concat(). */
    ResultHandle generateStringConcatenation(MethodCreator method, ResultHandle cb, ResultHandle left, ResultHandle right);

    /** Generates JPA Expression from lambda expression. Returns null if unsupported. */
    ResultHandle generateExpressionAsJpaExpression(MethodCreator method, LambdaExpression expression,
            ResultHandle cb, ResultHandle root, ResultHandle capturedValues);

    /**
     * Generates bytecode for lambda expression, returning raw values or JPA expressions.
     * Unlike generateExpressionAsJpaExpression, may return raw values (e.g., LIKE patterns).
     */
    ResultHandle generateExpression(MethodCreator method, LambdaExpression expression,
            ResultHandle cb, ResultHandle root, ResultHandle capturedValues);

    // ========== Captured Variable Utilities ==========

    /** Loads captured variable from array and casts to appropriate type (raw, not JPA literal). */
    ResultHandle loadCapturedValue(MethodCreator method, CapturedVariable capturedVar, ResultHandle capturedValues);

    /** Loads captured variable and wraps as JPA literal expression. */
    ResultHandle loadAndWrapCapturedValue(MethodCreator method, ResultHandle cb,
            CapturedVariable capturedVar, ResultHandle capturedValues);

    // ========== DTO Class Loading Utilities ==========

    /** Loads DTO class by internal name ("com/example/MyDto" → Class.forName). */
    ResultHandle loadDtoClass(MethodCreator method, String internalClassName);

    // ========== Boolean Predicate Wrapping Utilities ==========

    /** Wraps boolean-typed path as predicate using cb.isTrue() if type is boolean. */
    ResultHandle wrapBooleanAsPredicateIfNeeded(MethodCreator method, ResultHandle cb, ResultHandle path, Class<?> type);

    // ========== Correlated Variable Utilities ==========

    /** Generates field expression from CorrelatedVariable (wraps FieldAccess or PathExpression). */
    default ResultHandle generateCorrelatedFieldExpression(
            MethodCreator method,
            LambdaExpression.CorrelatedVariable correlated,
            ResultHandle root) {
        LambdaExpression fieldExpr = correlated.fieldExpression();
        return switch (fieldExpr) {
            case LambdaExpression.FieldAccess field -> generateFieldAccess(method, field, root);
            case LambdaExpression.PathExpression path -> generatePathExpression(method, path, root);
            default -> null;
        };
    }

    // ========== Null Check Predicate Utilities ==========

    /** Extracts non-null expression from null comparison (x == null or null != y). */
    default LambdaExpression extractNonNullExpression(LambdaExpression.BinaryOp binOp) {
        boolean leftIsNull = binOp.left() instanceof LambdaExpression.NullLiteral;
        return leftIsNull ? binOp.right() : binOp.left();
    }

    /** Generates IS NULL (EQ) or IS NOT NULL (NE) predicate. */
    default ResultHandle generateNullCheckPredicate(
            MethodCreator method,
            ResultHandle cb,
            ResultHandle expression,
            LambdaExpression.BinaryOp.Operator operator) {
        if (operator == LambdaExpression.BinaryOp.Operator.EQ) {
            return method.invokeInterfaceMethod(CB_IS_NULL, cb, expression);
        } else {
            return method.invokeInterfaceMethod(CB_IS_NOT_NULL, cb, expression);
        }
    }
}
