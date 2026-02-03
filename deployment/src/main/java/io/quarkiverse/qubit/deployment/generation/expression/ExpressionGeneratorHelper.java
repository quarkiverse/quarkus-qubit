package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_NOT_NULL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_NULL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT;

import org.jspecify.annotations.Nullable;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable;

/**
 * Shared expression generation methods for BiEntityExpressionBuilder and GroupExpressionBuilder.
 * Extracted to avoid circular dependencies with CriteriaExpressionGenerator.
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public interface ExpressionGeneratorHelper {

    /** Generates JPA field access expression. */
    Expr generateFieldAccess(BlockCreator bc, LambdaExpression.FieldAccess field, Expr root);

    /** Generates JPA path expression for relationship navigation. */
    Expr generatePathExpression(BlockCreator bc, LambdaExpression.PathExpression pathExpr, Expr root);

    /** Generates constant value bytecode. */
    Expr generateConstant(BlockCreator bc, LambdaExpression.Constant constant);

    /** Wraps value as literal Expression using cb.literal(). */
    Expr wrapAsLiteral(BlockCreator bc, Expr cb, Expr value);

    /** Combines two predicates with AND or OR. */
    Expr combinePredicates(BlockCreator bc, Expr cb, Expr left, Expr right,
            LambdaExpression.BinaryOp.Operator operator);

    /** Generates comparison operation (EQ, NE, GT, GE, LT, LE). */
    Expr generateComparisonOperation(BlockCreator bc, LambdaExpression.BinaryOp.Operator operator,
            Expr cb, Expr left, Expr right);

    /** Generates arithmetic operation (ADD, SUB, MUL, DIV, MOD). */
    Expr generateArithmeticOperation(BlockCreator bc, LambdaExpression.BinaryOp.Operator operator,
            Expr cb, Expr left, Expr right);

    /** Checks if binary operation is string concatenation. */
    boolean isStringConcatenation(LambdaExpression.BinaryOp binOp);

    /** Generates string concatenation using cb.concat(). */
    Expr generateStringConcatenation(BlockCreator bc, Expr cb, Expr left, Expr right);

    /** Generates JPA Expression from lambda expression. Returns null if expression is null. */
    @Nullable Expr generateExpressionAsJpaExpression(BlockCreator bc, @Nullable LambdaExpression expression,
            Expr cb, Expr root, Expr capturedValues);

    /**
     * Generates bytecode for lambda expression, returning raw values or JPA expressions.
     * Returns null if expression is null.
     */
    @Nullable Expr generateExpression(BlockCreator bc, @Nullable LambdaExpression expression,
            Expr cb, Expr root, Expr capturedValues);

    // ========== Captured Variable Utilities ==========

    /** Loads captured variable from array and casts to appropriate type (raw, not JPA literal). */
    Expr loadCapturedValue(BlockCreator bc, CapturedVariable capturedVar, Expr capturedValues);

    /** Loads captured variable and wraps as JPA literal expression. */
    Expr loadAndWrapCapturedValue(BlockCreator bc, Expr cb,
            CapturedVariable capturedVar, Expr capturedValues);

    // ========== DTO Class Loading Utilities ==========

    /** Loads DTO class by internal name ("com/example/MyDto" → Class.forName). */
    Expr loadDtoClass(BlockCreator bc, String internalClassName);

    // ========== Boolean Predicate Wrapping Utilities ==========

    /** Wraps boolean-typed path as predicate using cb.isTrue() if type is boolean. */
    Expr wrapBooleanAsPredicateIfNeeded(BlockCreator bc, Expr cb, Expr path, Class<?> type);

    // ========== Correlated Variable Utilities ==========

    /** Generates field expression from CorrelatedVariable. Returns null if unsupported expression type. */
    default @Nullable Expr generateCorrelatedFieldExpression(
            BlockCreator bc,
            LambdaExpression.CorrelatedVariable correlated,
            Expr root) {
        LambdaExpression fieldExpr = correlated.fieldExpression();
        return switch (fieldExpr) {
            case LambdaExpression.FieldAccess field -> generateFieldAccess(bc, field, root);
            case LambdaExpression.PathExpression path -> generatePathExpression(bc, path, root);
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
    default Expr generateNullCheckPredicate(
            BlockCreator bc,
            Expr cb,
            Expr expression,
            LambdaExpression.BinaryOp.Operator operator) {
        if (operator == LambdaExpression.BinaryOp.Operator.EQ) {
            return bc.invokeInterface(CB_IS_NULL, cb, expression);
        } else {
            return bc.invokeInterface(CB_IS_NOT_NULL, cb, expression);
        }
    }

    // ========== Unary Operation Utilities ==========

    /** Applies unary operator to operand. Currently only NOT is supported. */
    default Expr applyUnaryOperator(
            BlockCreator bc,
            Expr cb,
            Expr operand,
            LambdaExpression.UnaryOp.Operator operator) {
        return switch (operator) {
            case NOT -> bc.invokeInterface(CB_NOT, cb, operand);
        };
    }
}
