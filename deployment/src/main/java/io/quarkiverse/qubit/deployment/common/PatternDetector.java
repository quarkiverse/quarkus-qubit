package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.util.TypeConverter;

import java.util.Deque;
import java.util.Iterator;
import java.util.function.Predicate;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.ADD;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.DIV;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.MOD;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.MUL;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.NE;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.OR;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.SUB;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_COMPARE_TO;

/**
 * Detects bytecode patterns: dcmpl/fcmpl/lcmp, compareTo, arithmetic, arithmetic+constant.
 */
public final class PatternDetector {

    private PatternDetector() {
    }

    /** Bytecode branch patterns, mutually exclusive and checked in priority order. */
    public enum BranchPattern {
        NUMERIC_COMPARISON, // (a - b) == 0 or dcmpl(a, b) != 0
        COMPARE_TO,         // a.compareTo(b)
        ARITHMETIC,         // (a + b) != 0
        OTHER;              // Default (boolean field access, etc.)

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

    /** Analysis result for branch instruction patterns. */
    public record BranchPatternAnalysis(LambdaExpression top, BranchPattern pattern) {
        public static BranchPatternAnalysis analyze(Deque<LambdaExpression> stack) {
            // ArrayDeque.peek() returns null for empty deques, no need for isEmpty check
            return new BranchPatternAnalysis(
                stack.peek(),
                BranchPattern.detect(stack)
            );
        }
    }

    // ========== Binary Operation Category Detection ==========

    /** Binary operation categories, mutually exclusive and checked in priority order. */
    public enum BinaryOperationCategory {
        STRING_CONCATENATION,           // ADD for strings (check before arithmetic)
        ARITHMETIC,                     // ADD, SUB, MUL, DIV, MOD
        LOGICAL,                        // AND, OR
        NULL_CHECK,                     // == null or != null
        BOOLEAN_FIELD_CONSTANT,         // bool field vs 0/1
        BOOLEAN_FIELD_CAPTURED_VARIABLE,// bool field vs captured bool
        COMPARE_TO_EQUALITY,            // a.compareTo(b) == 0
        COMPARISON;                     // Default: EQ, NE, LT, LE, GT, GE

        public static BinaryOperationCategory categorize(
                LambdaExpression.BinaryOp binOp,
                Predicate<LambdaExpression.BinaryOp> isStringConcatenation) {

            // Priority 1: String concatenation (must check before arithmetic, both use ADD)
            if (isStringConcatenation.test(binOp)) {
                return STRING_CONCATENATION;
            }

            // Priority 2: Arithmetic operations
            if (PatternDetector.isArithmeticExpression(binOp)) {
                return ARITHMETIC;
            }

            // Priority 3: Logical operations (AND, OR)
            if (isLogicalOperation(binOp)) {
                return LOGICAL;
            }

            // Priority 4: Null check pattern
            if (isNullCheckPattern(binOp)) {
                return NULL_CHECK;
            }

            // Priority 5: Boolean field compared to constant 0/1
            if (isBooleanFieldConstantComparison(binOp)) {
                return BOOLEAN_FIELD_CONSTANT;
            }

            // Priority 6: Boolean field compared to captured boolean variable
            if (isBooleanFieldCapturedVariableComparison(binOp)) {
                return BOOLEAN_FIELD_CAPTURED_VARIABLE;
            }

            // Priority 7: CompareTo equality pattern
            if (isCompareToEqualityPattern(binOp)) {
                return COMPARE_TO_EQUALITY;
            }

            // Default: Standard comparison operation
            return COMPARISON;
        }
    }

    /** Returns true if stack contains floating-point/long comparison pattern (includes GROUP BY HAVING). */
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

    private static boolean isComparableExpression(LambdaExpression expr) {
        return isEntityFieldExpression(expr) ||
               expr instanceof LambdaExpression.Constant ||
               expr instanceof LambdaExpression.CapturedVariable ||
               expr instanceof LambdaExpression.GroupAggregation ||
               expr instanceof LambdaExpression.GroupKeyReference ||
               expr instanceof LambdaExpression.ScalarSubquery ||
               isArithmeticExpression(expr);
    }

    /** Returns true if expression is entity field access (FieldAccess, PathExpression, BiEntity variants). */
    public static boolean isEntityFieldExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.FieldAccess ||
               expr instanceof LambdaExpression.PathExpression ||
               expr instanceof LambdaExpression.BiEntityFieldAccess ||
               expr instanceof LambdaExpression.BiEntityPathExpression;
    }

    public static boolean isCompareToPattern(LambdaExpression expr) {
        return expr instanceof LambdaExpression.MethodCall(_, _, _, var returnType) &&
               returnType == int.class;
    }

    public static boolean isArithmeticExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.BinaryOp(_, var operator, _) &&
               (operator == ADD ||
                operator == SUB ||
                operator == MUL ||
                operator == DIV ||
                operator == MOD);
    }

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

    public static boolean isLogicalOperation(LambdaExpression.BinaryOp binOp) {
        return binOp.operator() == AND || binOp.operator() == OR;
    }

    public static boolean isEqualityOperation(LambdaExpression.BinaryOp binOp) {
        return binOp.operator() == EQ || binOp.operator() == NE;
    }

    public static boolean isNullCheckPattern(LambdaExpression.BinaryOp binOp) {
        if (!isEqualityOperation(binOp)) {
            return false;
        }
        return binOp.left() instanceof LambdaExpression.NullLiteral ||
               binOp.right() instanceof LambdaExpression.NullLiteral;
    }

    public static boolean isBooleanFieldConstantComparison(LambdaExpression.BinaryOp binOp) {
        if (!isEqualityOperation(binOp)) {
            return false;
        }

        if (!(binOp.left() instanceof LambdaExpression.FieldAccess(_, var fieldType))) {
            return false;
        }

        if (!TypeConverter.isBooleanType(fieldType)) {
            return false;
        }

        if (!(binOp.right() instanceof LambdaExpression.Constant(var value, var type))) {
            return false;
        }

        return type == int.class &&
               (value.equals(0) || value.equals(1));
    }

    public static boolean isBooleanFieldCapturedVariableComparison(LambdaExpression.BinaryOp binOp) {
        if (!isEqualityOperation(binOp)) {
            return false;
        }

        if (!(binOp.left() instanceof LambdaExpression.FieldAccess(_, var fieldType))) {
            return false;
        }

        if (!TypeConverter.isBooleanType(fieldType)) {
            return false;
        }

        if (!(binOp.right() instanceof LambdaExpression.CapturedVariable(_, var capturedType, _))) {
            return false;
        }

        return TypeConverter.isBooleanType(capturedType);
    }

    public static boolean isCompareToEqualityPattern(LambdaExpression.BinaryOp binOp) {
        if (binOp.operator() != EQ) {
            return false;
        }

        if (!(binOp.left() instanceof LambdaExpression.MethodCall(_, var methodName, _, _))) {
            return false;
        }

        if (!methodName.equals(METHOD_COMPARE_TO)) {
            return false;
        }

        return binOp.right() instanceof LambdaExpression.Constant;
    }

    // ========== Subquery Pattern Detection==========

    /** Checks if expression contains any subquery (scalar, exists, or in). */
    public static boolean containsSubquery(LambdaExpression expr) {
        return switch (expr) {
            case LambdaExpression.ScalarSubquery _ -> true;
            case LambdaExpression.ExistsSubquery _ -> true;
            case LambdaExpression.InSubquery _ -> true;
            case LambdaExpression.BinaryOp binOp -> containsSubquery(binOp.left()) || containsSubquery(binOp.right());
            case LambdaExpression.UnaryOp unOp -> containsSubquery(unOp.operand());
            case null, default -> false;
        };
    }

    /** Checks if expression contains a scalar subquery (usable in comparisons, unlike EXISTS/IN predicates). */
    public static boolean containsScalarSubquery(LambdaExpression expr) {
        return switch (expr) {
            case LambdaExpression.ScalarSubquery _ -> true;
            case LambdaExpression.ExistsSubquery _ -> false;
            case LambdaExpression.InSubquery _ -> false;
            case LambdaExpression.BinaryOp binOp -> containsScalarSubquery(binOp.left()) || containsScalarSubquery(binOp.right());
            case LambdaExpression.UnaryOp unOp -> containsScalarSubquery(unOp.operand());
            case null, default -> false;
        };
    }

    /** Checks if comparing subquery to boolean constant (due to bytecode short-circuit patterns). */
    public static boolean isSubqueryBooleanComparison(LambdaExpression.BinaryOp binOp) {
        if (binOp.operator() != LambdaExpression.BinaryOp.Operator.EQ &&
            binOp.operator() != LambdaExpression.BinaryOp.Operator.NE) {
            return false;
        }

        boolean leftIsSubquery = containsSubquery(binOp.left());
        boolean rightIsSubquery = containsSubquery(binOp.right());

        return (leftIsSubquery && isBooleanConstant(binOp.right())) ||
               (rightIsSubquery && isBooleanConstant(binOp.left()));
    }

    /** Checks if expression is a boolean constant (true/false or 0/1). */
    public static boolean isBooleanConstant(LambdaExpression expr) {
        if (!(expr instanceof LambdaExpression.Constant(var value, _))) {
            return false;
        }
        return value instanceof Boolean ||
               (value instanceof Integer intValue && (intValue == 0 || intValue == 1));
    }

    /** Returns true if comparison should negate subquery (e.g., subquery == false). */
    public static boolean isNegatedSubqueryComparison(
            LambdaExpression.BinaryOp.Operator operator,
            LambdaExpression constantExpr) {
        if (!(constantExpr instanceof LambdaExpression.Constant(var value, _))) {
            return false;
        }

        if (operator == LambdaExpression.BinaryOp.Operator.EQ) {
            return Boolean.FALSE.equals(value) || Integer.valueOf(0).equals(value);
        }
        if (operator == LambdaExpression.BinaryOp.Operator.NE) {
            return Boolean.TRUE.equals(value) || Integer.valueOf(1).equals(value);
        }
        return false;
    }
}
