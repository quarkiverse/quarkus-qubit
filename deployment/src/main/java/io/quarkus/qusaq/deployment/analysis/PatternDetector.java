package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.qusaq.deployment.LambdaExpression;

import java.util.Deque;
import java.util.Iterator;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.*;

/**
 * Detects bytecode patterns: dcmpl/fcmpl/lcmp, compareTo, arithmetic, arithmetic+constant.
 */
public final class PatternDetector {

    private PatternDetector() {
    }

    /**
     * Analysis result for branch instruction patterns.
     */
    public record BranchPatternAnalysis(
        LambdaExpression top,
        boolean isDcmplPattern,
        boolean isCompareToPattern,
        boolean isArithmeticPattern,
        boolean isArithmeticComparisonPattern
    ) {
        /**
         * Analyzes stack for bytecode patterns.
         */
        public static BranchPatternAnalysis analyze(Deque<LambdaExpression> stack) {
            if (stack.isEmpty()) {
                return new BranchPatternAnalysis(null, false, false, false, false);
            }

            LambdaExpression top = stack.peek();
            return new BranchPatternAnalysis(
                top,
                PatternDetector.isDcmplPattern(stack),
                PatternDetector.isCompareToPattern(top),
                PatternDetector.isArithmeticExpression(top),
                PatternDetector.isArithmeticComparisonPattern(stack)
            );
        }
    }

    /**
     * Returns true if stack contains floating-point comparison pattern.
     */
    public static boolean isDcmplPattern(Deque<LambdaExpression> stack) {
        if (stack.size() < 2) {
            return false;
        }
        Iterator<LambdaExpression> iter = stack.iterator();
        LambdaExpression first = iter.next();
        LambdaExpression second = iter.next();
        boolean topIsComparable = (first instanceof LambdaExpression.FieldAccess ||
                                    first instanceof LambdaExpression.Constant ||
                                    first instanceof LambdaExpression.CapturedVariable ||
                                    isArithmeticExpression(first));
        boolean secondIsComparable = (second instanceof LambdaExpression.FieldAccess ||
                                       second instanceof LambdaExpression.Constant ||
                                       second instanceof LambdaExpression.CapturedVariable ||
                                       isArithmeticExpression(second));
        return topIsComparable && secondIsComparable;
    }

    /**
     * Returns true if expression is compareTo method call.
     */
    public static boolean isCompareToPattern(LambdaExpression expr) {
        return expr instanceof LambdaExpression.MethodCall methodCall &&
               methodCall.returnType() == int.class;
    }

    /**
     * Returns true if expression is arithmetic operation.
     */
    public static boolean isArithmeticExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.BinaryOp binOp &&
               (binOp.operator() == ADD ||
                binOp.operator() == SUB ||
                binOp.operator() == MUL ||
                binOp.operator() == DIV ||
                binOp.operator() == MOD);
    }

    /**
     * Returns true if stack contains arithmetic comparison pattern.
     */
    public static boolean isArithmeticComparisonPattern(Deque<LambdaExpression> stack) {
        if (stack.size() < 2) {
            return false;
        }
        Iterator<LambdaExpression> iter = stack.iterator();
        LambdaExpression top = iter.next();
        LambdaExpression second = iter.next();
        return (top instanceof LambdaExpression.Constant) &&
               isArithmeticExpression(second);
    }

    /**
     * Returns true if binary operation is a logical operation (AND or OR).
     */
    public static boolean isLogicalOperation(LambdaExpression.BinaryOp binOp) {
        return binOp.operator() == AND || binOp.operator() == OR;
    }

    /**
     * Returns true if binary operation is an equality operation (EQ or NE).
     */
    public static boolean isEqualityOperation(LambdaExpression.BinaryOp binOp) {
        return binOp.operator() == EQ || binOp.operator() == NE;
    }

    /**
     * Returns true if binary operation is a null check pattern (comparing with null literal).
     */
    public static boolean isNullCheckPattern(LambdaExpression.BinaryOp binOp) {
        if (!isEqualityOperation(binOp)) {
            return false;
        }
        return binOp.left() instanceof LambdaExpression.NullLiteral ||
               binOp.right() instanceof LambdaExpression.NullLiteral;
    }

    /**
     * Returns true if binary operation is comparing a boolean field with constant 0 or 1.
     */
    public static boolean isBooleanFieldConstantComparison(LambdaExpression.BinaryOp binOp) {
        if (!isEqualityOperation(binOp)) {
            return false;
        }

        if (!(binOp.left() instanceof LambdaExpression.FieldAccess fieldAccess)) {
            return false;
        }

        if (!isBooleanType(fieldAccess.fieldType())) {
            return false;
        }

        if (!(binOp.right() instanceof LambdaExpression.Constant constant)) {
            return false;
        }

        return constant.type() == int.class &&
               (constant.value().equals(0) || constant.value().equals(1));
    }

    /**
     * Returns true if binary operation is comparing a boolean field with a boolean captured variable.
     */
    public static boolean isBooleanFieldCapturedVariableComparison(LambdaExpression.BinaryOp binOp) {
        if (!isEqualityOperation(binOp)) {
            return false;
        }

        if (!(binOp.left() instanceof LambdaExpression.FieldAccess fieldAccess)) {
            return false;
        }

        if (!isBooleanType(fieldAccess.fieldType())) {
            return false;
        }

        if (!(binOp.right() instanceof LambdaExpression.CapturedVariable capturedVar)) {
            return false;
        }

        return isBooleanType(capturedVar.type());
    }

    /**
     * Returns true if binary operation is an equality check with a compareTo method call.
     */
    public static boolean isCompareToEqualityPattern(LambdaExpression.BinaryOp binOp) {
        if (binOp.operator() != EQ) {
            return false;
        }

        if (!(binOp.left() instanceof LambdaExpression.MethodCall methodCall)) {
            return false;
        }

        if (!methodCall.methodName().equals("compareTo")) {
            return false;
        }

        return binOp.right() instanceof LambdaExpression.Constant;
    }

    /**
     * Returns true if type is boolean (primitive or wrapper).
     */
    private static boolean isBooleanType(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }
}
