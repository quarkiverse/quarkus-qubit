package io.quarkiverse.qubit.deployment.common;

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

import java.util.Deque;
import java.util.Iterator;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.util.TypeConverter;

/**
 * Detects bytecode patterns: dcmpl/fcmpl/lcmp, compareTo, arithmetic, arithmetic+constant.
 */
public final class PatternDetector {

    private PatternDetector() {
    }

    /** Bytecode branch patterns, mutually exclusive and checked in priority order. */
    public enum BranchPattern {
        NUMERIC_COMPARISON, // (a - b) == 0 or dcmpl(a, b) != 0
        COMPARE_TO, // a.compareTo(b)
        ARITHMETIC, // (a + b) != 0
        OTHER; // Default (boolean field access, etc.)

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

            // Priority 4: Non-boolean method call (e.g., getSecond(), getMinute())
            // These need comparison with zero, not boolean NOT treatment
            if (isNumericMethodCall(top)) {
                return ARITHMETIC;
            }

            // Default: Other patterns (boolean field access, etc.)
            return OTHER;
        }
    }

    /** Analysis result for branch instruction patterns. */
    public record BranchPatternAnalysis(LambdaExpression top, BranchPattern pattern) {
        public static BranchPatternAnalysis analyze(Deque<LambdaExpression> stack) {
            // ArrayDeque.peek() returns null for empty deques, no need for isEmpty check
            return new BranchPatternAnalysis(
                    stack.peek(),
                    BranchPattern.detect(stack));
        }
    }

    /** Binary operation categories, mutually exclusive and checked in priority order. */
    public enum BinaryOperationCategory {
        STRING_CONCATENATION, // ADD for strings (check before arithmetic)
        ARITHMETIC, // ADD, SUB, MUL, DIV, MOD
        LOGICAL, // AND, OR
        NULL_CHECK, // == null or != null
        BOOLEAN_FIELD_CONSTANT, // bool field vs 0/1
        BOOLEAN_FIELD_CAPTURED_VARIABLE, // bool field vs captured bool
        COMPARE_TO_EQUALITY, // a.compareTo(b) == 0
        COMPARISON; // Default: EQ, NE, LT, LE, GT, GE

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
                isCastOfComparableExpression(expr) ||
                isArithmeticExpression(expr);
    }

    /** Returns true if expression is a Cast wrapping a comparable expression (e.g., checkcast before unboxing). */
    private static boolean isCastOfComparableExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.Cast(var inner, _) && isComparableExpression(inner);
    }

    /** Returns true if expression is entity field access (FieldAccess, PathExpression, BiEntity variants). */
    public static boolean isEntityFieldExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.FieldAccess ||
                expr instanceof LambdaExpression.PathExpression ||
                expr instanceof LambdaExpression.BiEntityFieldAccess ||
                expr instanceof LambdaExpression.BiEntityPathExpression;
    }

    public static boolean isCompareToPattern(LambdaExpression expr) {
        return expr instanceof LambdaExpression.MethodCall(_, var methodName, _, var returnType) &&
                returnType == int.class &&
                METHOD_COMPARE_TO.equals(methodName);
    }

    public static boolean isArithmeticExpression(LambdaExpression expr) {
        return expr instanceof LambdaExpression.BinaryOp(_, var operator, _) &&
                (operator == ADD ||
                        operator == SUB ||
                        operator == MUL ||
                        operator == DIV ||
                        operator == MOD);
    }

    /** Returns true if expression is a method call returning a primitive numeric type. */
    public static boolean isNumericMethodCall(LambdaExpression expr) {
        return expr instanceof LambdaExpression.MethodCall(_, _, _, var returnType) &&
                (returnType == int.class || returnType == long.class ||
                        returnType == double.class || returnType == float.class);
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
            case LambdaExpression.BinaryOp binOp ->
                containsScalarSubquery(binOp.left()) || containsScalarSubquery(binOp.right());
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

    // ─── BETWEEN Pattern Detection ──────────────────────────────────────────

    /**
     * Components of a BETWEEN expression detected from an AND of two comparisons.
     * Both bounds are inclusive (matches SQL BETWEEN semantics).
     */
    public record BetweenComponents(
            LambdaExpression field,
            LambdaExpression lowerBound,
            LambdaExpression upperBound) {
    }

    /**
     * Detects if a BinaryOp(AND) can be optimized to a BETWEEN expression.
     *
     * <p>
     * Detects patterns where two comparisons on the same field form an inclusive range:
     * {@code field >= low && field <= high} (and all operand-order variations).
     *
     * @return BetweenComponents if the pattern matches, null otherwise
     */
    public static @Nullable BetweenComponents detectBetween(LambdaExpression.BinaryOp binOp) {
        if (binOp.operator() != LambdaExpression.BinaryOp.Operator.AND) {
            return null;
        }
        if (!(binOp.left() instanceof LambdaExpression.BinaryOp left) ||
                !(binOp.right() instanceof LambdaExpression.BinaryOp right)) {
            return null;
        }

        // Normalize both comparisons to (field, operator, bound) form
        NormalizedComparison leftNorm = normalizeComparison(left);
        NormalizedComparison rightNorm = normalizeComparison(right);
        if (leftNorm == null || rightNorm == null) {
            return null;
        }

        // Both sides must reference the same field
        if (!leftNorm.field.equals(rightNorm.field)) {
            return null;
        }

        // Need one GE and one LE to form inclusive BETWEEN
        if (leftNorm.isGe() && rightNorm.isLe()) {
            return new BetweenComponents(leftNorm.field, leftNorm.bound, rightNorm.bound);
        }
        if (leftNorm.isLe() && rightNorm.isGe()) {
            return new BetweenComponents(leftNorm.field, rightNorm.bound, leftNorm.bound);
        }

        return null;
    }

    /**
     * A comparison normalized to (field, operator, bound) form.
     * Handles reversed operands: "18 <= field" becomes (field, GE, 18).
     */
    private record NormalizedComparison(
            LambdaExpression field,
            LambdaExpression.BinaryOp.Operator operator,
            LambdaExpression bound) {

        boolean isGe() {
            return operator == LambdaExpression.BinaryOp.Operator.GE;
        }

        boolean isLe() {
            return operator == LambdaExpression.BinaryOp.Operator.LE;
        }
    }

    /**
     * Normalizes a comparison BinaryOp to (field, op, bound) form.
     * Handles reversed operands by flipping the operator.
     * Returns null if not a GE or LE comparison.
     */
    private static @Nullable NormalizedComparison normalizeComparison(LambdaExpression.BinaryOp comp) {
        var op = comp.operator();
        var left = comp.left();
        var right = comp.right();

        // Only GE and LE map to inclusive BETWEEN
        if (op != LambdaExpression.BinaryOp.Operator.GE && op != LambdaExpression.BinaryOp.Operator.LE) {
            return null;
        }

        // Direct form: field >= value or field <= value
        if (isEntityFieldExpression(left)) {
            return new NormalizedComparison(left, op, right);
        }
        // Reversed form: value <= field -> field >= value, value >= field -> field <= value
        if (isEntityFieldExpression(right)) {
            var flippedOp = flipOperator(op);
            return new NormalizedComparison(right, flippedOp, left);
        }

        return null;
    }

    /** Flips comparison operator direction: GE <-> LE. */
    private static LambdaExpression.BinaryOp.Operator flipOperator(LambdaExpression.BinaryOp.Operator op) {
        return switch (op) {
            case GE -> LambdaExpression.BinaryOp.Operator.LE;
            case LE -> LambdaExpression.BinaryOp.Operator.GE;
            default -> throw new IllegalArgumentException("Can only flip GE/LE, got: " + op);
        };
    }

    // ─── NULLIF Pattern Detection ───────────────────────────────────────────

    /** Components of a NULLIF expression: returns null when expression equals sentinel. */
    public record NullifComponents(LambdaExpression expression, LambdaExpression sentinel) {
    }

    /**
     * Detects if a Conditional (ternary) matches NULLIF semantics.
     *
     * <p>
     * Matches patterns at the AST level:
     * <ul>
     * <li>{@code field == sentinel ? null : field} → NULLIF(field, sentinel)</li>
     * <li>{@code field != sentinel ? field : null} → NULLIF(field, sentinel)</li>
     * </ul>
     *
     * <p>
     * Also handles the bytecode-level boolean comparison wrapping produced by
     * {@code handleEqualsMethod()}, where {@code field.equals(sentinel)} becomes
     * {@code BinaryOp(BinaryOp(field, EQ, sentinel), NE, Constant(0))} (truthy check)
     * and {@code !field.equals(sentinel)} becomes
     * {@code BinaryOp(BinaryOp(field, EQ, sentinel), EQ, Constant(0))} (falsy check).
     *
     * @return NullifComponents if pattern matches, null otherwise
     */
    public static @Nullable NullifComponents detectNullif(LambdaExpression.Conditional conditional) {
        LambdaExpression condition = conditional.condition();
        LambdaExpression trueValue = conditional.trueValue();
        LambdaExpression falseValue = conditional.falseValue();

        if (!(condition instanceof LambdaExpression.BinaryOp comp)) {
            return null;
        }

        // Try direct patterns first (for hand-constructed ASTs in tests)
        NullifComponents direct = detectNullifDirect(comp, trueValue, falseValue);
        if (direct != null) {
            return direct;
        }

        // Try unwrapped boolean comparison patterns (from bytecode analysis)
        return detectNullifFromBooleanComparison(comp, trueValue, falseValue);
    }

    /**
     * Detects NULLIF from direct AST patterns (field EQ/NE sentinel in condition).
     */
    private static @Nullable NullifComponents detectNullifDirect(
            LambdaExpression.BinaryOp comp,
            LambdaExpression trueValue,
            LambdaExpression falseValue) {

        // Pattern 1: field == sentinel ? null : field
        if (comp.operator() == LambdaExpression.BinaryOp.Operator.EQ
                && trueValue instanceof LambdaExpression.NullLiteral) {
            return matchNullifField(comp, falseValue);
        }

        // Pattern 2: field != sentinel ? field : null
        if (comp.operator() == LambdaExpression.BinaryOp.Operator.NE
                && falseValue instanceof LambdaExpression.NullLiteral) {
            return matchNullifField(comp, trueValue);
        }

        return null;
    }

    /**
     * Detects NULLIF from bytecode-level boolean comparison wrapping.
     *
     * <p>
     * Bytecode pattern for {@code field.equals(sentinel) ? null : field}:
     *
     * <pre>
     * Conditional(
     *   condition: BinaryOp(BinaryOp(field, EQ, sentinel), NE, Constant(0)),
     *   trueValue: NullLiteral,
     *   falseValue: field
     * )
     * </pre>
     *
     * <p>
     * Bytecode pattern for {@code !field.equals(sentinel) ? field : null}:
     *
     * <pre>
     * Conditional(
     *   condition: BinaryOp(BinaryOp(field, EQ, sentinel), EQ, Constant(0)),
     *   trueValue: field,
     *   falseValue: NullLiteral
     * )
     * </pre>
     */
    private static @Nullable NullifComponents detectNullifFromBooleanComparison(
            LambdaExpression.BinaryOp comp,
            LambdaExpression trueValue,
            LambdaExpression falseValue) {

        // Check for boolean comparison wrapping: inner_comparison NE/EQ Constant(0)
        if (!isEqualityOperation(comp)) {
            return null;
        }

        if (!(comp.left() instanceof LambdaExpression.BinaryOp innerComp)) {
            return null;
        }

        if (!(comp.right() instanceof LambdaExpression.Constant(var value, var type))
                || type != int.class || !Integer.valueOf(0).equals(value)) {
            return null;
        }

        // Unwrap: NE 0 means "is true" (equals matched), EQ 0 means "is false" (not equals)
        // NE 0 + trueValue=null → field.equals(sentinel) ? null : field → NULLIF
        if (comp.operator() == LambdaExpression.BinaryOp.Operator.NE
                && trueValue instanceof LambdaExpression.NullLiteral) {
            return matchNullifField(innerComp, falseValue);
        }

        // EQ 0 + falseValue=null → !field.equals(sentinel) ? field : null → NULLIF
        if (comp.operator() == LambdaExpression.BinaryOp.Operator.EQ
                && falseValue instanceof LambdaExpression.NullLiteral) {
            return matchNullifField(innerComp, trueValue);
        }

        return null;
    }

    /**
     * Matches the field from the comparison against the non-null branch value.
     * Handles both operand orders: field == sentinel and sentinel == field.
     */
    private static @Nullable NullifComponents matchNullifField(
            LambdaExpression.BinaryOp comparison, LambdaExpression nonNullValue) {
        LambdaExpression left = comparison.left();
        LambdaExpression right = comparison.right();

        // field == sentinel ? null : field (field on left of comparison)
        if (left.equals(nonNullValue) && isFieldExpression(left)) {
            return new NullifComponents(left, right);
        }

        // sentinel == field ? null : field (field on right of comparison)
        if (right.equals(nonNullValue) && isFieldExpression(right)) {
            return new NullifComponents(right, left);
        }

        return null;
    }

    /**
     * Returns true if expression is a field-like expression suitable for NULLIF.
     * This includes entity field access and path expressions.
     */
    public static boolean isFieldExpression(LambdaExpression expr) {
        return isEntityFieldExpression(expr);
    }
}
