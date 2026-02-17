package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_ABS;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CEILING;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_EXP;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_FLOOR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_LN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NEG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_POWER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_ROUND;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SIGN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SQRT;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MathFunction;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Generates JPA CriteriaBuilder math function calls from {@link MathFunction} AST nodes.
 *
 * <p>
 * Supported operations:
 * <ul>
 * <li><b>Unary (JPA 2.0):</b> abs, neg, sqrt</li>
 * <li><b>Unary (JPA 3.1):</b> sign, ceiling, floor, exp, ln</li>
 * <li><b>Binary (JPA 3.1):</b> power, round</li>
 * </ul>
 */
public final class MathExpressionBuilder {

    private MathExpressionBuilder() {
    }

    /**
     * Builds the JPA CriteriaBuilder call for a math function.
     *
     * @param bc the block creator for bytecode generation
     * @param cb the CriteriaBuilder expression
     * @param operand the primary operand JPA expression
     * @param secondOperand the second operand (null for unary ops)
     * @param op the math operation
     * @return the resulting JPA expression
     */
    public static Expr build(BlockCreator bc, Expr cb,
            Expr operand, @Nullable Expr secondOperand, MathFunction.MathOp op) {
        return switch (op) {
            case ABS -> bc.invokeInterface(CB_ABS, cb, operand);
            case NEG -> bc.invokeInterface(CB_NEG, cb, operand);
            case SQRT -> bc.invokeInterface(CB_SQRT, cb, operand);
            case SIGN -> bc.invokeInterface(CB_SIGN, cb, operand);
            case CEILING -> bc.invokeInterface(CB_CEILING, cb, operand);
            case FLOOR -> bc.invokeInterface(CB_FLOOR, cb, operand);
            case EXP -> bc.invokeInterface(CB_EXP, cb, operand);
            case LN -> bc.invokeInterface(CB_LN, cb, operand);
            case POWER -> bc.invokeInterface(CB_POWER, cb, operand, secondOperand);
            case ROUND -> bc.invokeInterface(CB_ROUND, cb, operand, secondOperand);
        };
    }

    /** Maps MathOp to its MethodDesc for testing/inspection purposes. */
    public static MethodDesc methodDescFor(MathFunction.MathOp op) {
        return switch (op) {
            case ABS -> CB_ABS;
            case NEG -> CB_NEG;
            case SQRT -> CB_SQRT;
            case SIGN -> CB_SIGN;
            case CEILING -> CB_CEILING;
            case FLOOR -> CB_FLOOR;
            case EXP -> CB_EXP;
            case LN -> CB_LN;
            case POWER -> CB_POWER;
            case ROUND -> CB_ROUND;
        };
    }
}
