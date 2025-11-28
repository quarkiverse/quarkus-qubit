package io.quarkus.qusaq.deployment.generation.builders;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qusaq.deployment.LambdaExpression;

/**
 * Interface for common expression generation methods shared between builders.
 *
 * <p>This interface defines the contract for methods that both BiEntityExpressionBuilder
 * and GroupExpressionBuilder need from CriteriaExpressionGenerator.
 *
 * <p>Extracted to avoid circular dependencies and enable clean delegation
 * from specialized builders back to the main generator (addresses ARCH-001).
 *
 * @see io.quarkus.qusaq.deployment.generation.CriteriaExpressionGenerator
 */
public interface ExpressionGeneratorHelper {

    /**
     * Generates JPA field access expression.
     *
     * @param method the method creator for bytecode generation
     * @param field the field access expression
     * @param root the root entity handle
     * @return the JPA Path handle
     */
    ResultHandle generateFieldAccess(MethodCreator method, LambdaExpression.FieldAccess field, ResultHandle root);

    /**
     * Generates JPA path expression for relationship navigation.
     *
     * @param method the method creator for bytecode generation
     * @param pathExpr the path expression
     * @param root the root entity handle
     * @return the JPA Path handle
     */
    ResultHandle generatePathExpression(MethodCreator method, LambdaExpression.PathExpression pathExpr, ResultHandle root);

    /**
     * Generates constant value bytecode.
     *
     * @param method the method creator for bytecode generation
     * @param constant the constant expression
     * @return the constant value handle
     */
    ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant);

    /**
     * Wraps value as literal Expression using cb.literal().
     *
     * @param method the method creator for bytecode generation
     * @param cb the CriteriaBuilder handle
     * @param value the value to wrap
     * @return the JPA Expression handle
     */
    ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value);

    /**
     * Combines two predicates with AND or OR.
     *
     * @param method the method creator for bytecode generation
     * @param cb the CriteriaBuilder handle
     * @param left the left predicate
     * @param right the right predicate
     * @param operator the operator (AND or OR)
     * @return the combined Predicate handle
     */
    ResultHandle combinePredicates(MethodCreator method, ResultHandle cb, ResultHandle left, ResultHandle right,
            LambdaExpression.BinaryOp.Operator operator);

    /**
     * Generates comparison operation (EQ, NE, GT, GE, LT, LE).
     *
     * @param method the method creator for bytecode generation
     * @param operator the comparison operator
     * @param cb the CriteriaBuilder handle
     * @param left the left expression
     * @param right the right expression
     * @return the Predicate handle
     */
    ResultHandle generateComparisonOperation(MethodCreator method, LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb, ResultHandle left, ResultHandle right);

    /**
     * Generates arithmetic operation (ADD, SUB, MUL, DIV, MOD).
     *
     * @param method the method creator for bytecode generation
     * @param operator the arithmetic operator
     * @param cb the CriteriaBuilder handle
     * @param left the left expression
     * @param right the right expression
     * @return the Expression handle
     */
    ResultHandle generateArithmeticOperation(MethodCreator method, LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb, ResultHandle left, ResultHandle right);

    /**
     * Checks if binary operation is string concatenation.
     *
     * @param binOp the binary operation
     * @return true if this is a string concatenation
     */
    boolean isStringConcatenation(LambdaExpression.BinaryOp binOp);

    /**
     * Generates string concatenation using cb.concat().
     *
     * @param method the method creator for bytecode generation
     * @param cb the CriteriaBuilder handle
     * @param left the left expression
     * @param right the right expression
     * @return the Expression handle
     */
    ResultHandle generateStringConcatenation(MethodCreator method, ResultHandle cb, ResultHandle left, ResultHandle right);

    /**
     * Generates JPA Expression from lambda expression.
     *
     * @param method the method creator for bytecode generation
     * @param expression the lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Expression handle
     */
    ResultHandle generateExpressionAsJpaExpression(MethodCreator method, LambdaExpression expression,
            ResultHandle cb, ResultHandle root, ResultHandle capturedValues);
}
