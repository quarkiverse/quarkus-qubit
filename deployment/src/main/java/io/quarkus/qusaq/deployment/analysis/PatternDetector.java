package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.qusaq.deployment.LambdaExpression;

import java.util.Deque;
import java.util.Iterator;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.ADD;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.DIV;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.MOD;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.MUL;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.NE;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.OR;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.SUB;

/**
 * Detects bytecode patterns: dcmpl/fcmpl/lcmp, compareTo, arithmetic, arithmetic+constant.
 */
public final class PatternDetector {

    private PatternDetector() {
    }

    /**
     * Enumeration of bytecode branch patterns detected during lambda analysis.
     * Patterns are mutually exclusive and checked in priority order during detection.
     */
    public enum BranchPattern {
        /**
         * Numeric comparison pattern: arithmetic comparison (ISUB/LSUB) or floating-point comparison (DCMPL/DCMPG).
         * Examples: {@code (a - b) == 0}, {@code dcmpl(a, b) != 0}
         */
        NUMERIC_COMPARISON,

        /**
         * CompareTo method call pattern.
         * Example: {@code a.compareTo(b)}
         */
        COMPARE_TO,

        /**
         * Arithmetic expression pattern (ADD/SUB/MUL/DIV/MOD).
         * Example: {@code (a + b) != 0}
         */
        ARITHMETIC,

        /**
         * Other patterns (boolean field access, etc.).
         * This is the default when no other pattern matches.
         */
        OTHER;

        /**
         * Detects branch pattern with priority ordering.
         * Patterns are checked from highest to lowest priority.
         *
         * @param stack expression stack to analyze
         * @return detected pattern (never null)
         */
        public static BranchPattern detect(Deque<LambdaExpression> stack) {
            if (stack.isEmpty()) {
                return OTHER;
            }

            LambdaExpression top = stack.peek();

            // Priority 1: Numeric comparison (covers both arithmetic and DCMPL)
            if (PatternDetector.isArithmeticComparisonPattern(stack) ||
                PatternDetector.isDcmplPattern(stack)) {
                return NUMERIC_COMPARISON;
            }

            // Priority 2: CompareTo method call
            if (PatternDetector.isCompareToPattern(top)) {
                return COMPARE_TO;
            }

            // Priority 3: Arithmetic expression
            if (PatternDetector.isArithmeticExpression(top)) {
                return ARITHMETIC;
            }

            // Default: Other patterns
            return OTHER;
        }
    }

    /**
     * Analysis result for branch instruction patterns.
     * Immutable record containing the top stack expression and detected pattern type.
     */
    public record BranchPatternAnalysis(
        LambdaExpression top,
        BranchPattern pattern
    ) {
        /**
         * Analyzes stack for bytecode patterns.
         *
         * @param stack expression stack to analyze
         * @return analysis result with top expression and pattern type
         */
        public static BranchPatternAnalysis analyze(Deque<LambdaExpression> stack) {
            return new BranchPatternAnalysis(
                stack.isEmpty() ? null : stack.peek(),
                BranchPattern.detect(stack)
            );
        }
    }

    /**
     * Returns true if stack contains floating-point/long comparison pattern.
     * <p>
     * Iteration 7: Extended to support GroupAggregation and GroupKeyReference
     * for GROUP BY HAVING clause comparisons like {@code g.count() > 1}.
     */
    public static boolean isDcmplPattern(Deque<LambdaExpression> stack) {
        if (stack.size() < 2) {
            return false;
        }
        Iterator<LambdaExpression> iter = stack.iterator();
        LambdaExpression first = iter.next();
        LambdaExpression second = iter.next();
        boolean topIsComparable = isComparableExpression(first);
        boolean secondIsComparable = isComparableExpression(second);
        return topIsComparable && secondIsComparable;
    }

    /**
     * Returns true if expression is a comparable value (can be used in LCMP, DCMPL, etc.).
     * <p>
     * Iteration 7: Added GroupAggregation and GroupKeyReference support.
     * Iteration 8: Added ScalarSubquery support for subquery comparisons.
     */
    private static boolean isComparableExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.FieldAccess ||
               expr instanceof LambdaExpression.Constant ||
               expr instanceof LambdaExpression.CapturedVariable ||
               expr instanceof LambdaExpression.GroupAggregation ||
               expr instanceof LambdaExpression.GroupKeyReference ||
               expr instanceof LambdaExpression.ScalarSubquery ||
               isArithmeticExpression(expr);
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
