package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.and;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CANNOT_COMBINE_EMPTY_PREDICATE_LIST;

/** Captured variable operations: counting, collecting indices, renumbering, validation. */
public final class CapturedVariableHelper {

    private CapturedVariableHelper() {
    }

    /** Counts distinct captured variables in lambda expression. */
    public static int countCapturedVariables(LambdaExpression expression) {
        Set<Integer> capturedIndices = new HashSet<>();
        collectCapturedVariableIndices(expression, capturedIndices);
        return capturedIndices.size();
    }

    /** Counts total captured variables across all sort expressions. */
    public static int countCapturedVariablesInSortExpressions(List<LambdaAnalysisResult.SortExpression> sortExpressions) {
        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (LambdaAnalysisResult.SortExpression sortExpr : sortExpressions) {
            count += countCapturedVariables(sortExpr.keyExtractor());
        }
        return count;
    }

    /** Recursively collects captured variable indices. */
    public static void collectCapturedVariableIndices(LambdaExpression expression, Set<Integer> capturedIndices) {
        if (expression == null) {
            return;
        }

        switch (expression) {
            case LambdaExpression.CapturedVariable capturedVar ->
                capturedIndices.add(capturedVar.index());

            case LambdaExpression.BinaryOp binOp -> {
                collectCapturedVariableIndices(binOp.left(), capturedIndices);
                collectCapturedVariableIndices(binOp.right(), capturedIndices);
            }

            case LambdaExpression.UnaryOp unaryOp ->
                collectCapturedVariableIndices(unaryOp.operand(), capturedIndices);

            case LambdaExpression.MethodCall methodCall -> {
                collectCapturedVariableIndices(methodCall.target(), capturedIndices);
                for (LambdaExpression arg : methodCall.arguments()) {
                    collectCapturedVariableIndices(arg, capturedIndices);
                }
            }

            case LambdaExpression.ConstructorCall constructorCall -> {
                for (LambdaExpression arg : constructorCall.arguments()) {
                    collectCapturedVariableIndices(arg, capturedIndices);
                }
            }

            case LambdaExpression.InExpression inExpr -> {
                collectCapturedVariableIndices(inExpr.field(), capturedIndices);
                collectCapturedVariableIndices(inExpr.collection(), capturedIndices);
            }

            case LambdaExpression.MemberOfExpression memberOfExpr -> {
                collectCapturedVariableIndices(memberOfExpr.value(), capturedIndices);
                collectCapturedVariableIndices(memberOfExpr.collectionField(), capturedIndices);
            }

            // No captured variables (separate cases: Java 21 `_` requires preview)
            case LambdaExpression.PathExpression ignored1 -> { /* no-op */ }
            case LambdaExpression.BiEntityFieldAccess ignored2 -> { /* no-op */ }
            case LambdaExpression.BiEntityPathExpression ignored3 -> { /* no-op */ }
            case LambdaExpression.BiEntityParameter ignored4 -> { /* no-op */ }
            default -> { /* no-op */ }
        }
    }

    /** Renumbers captured variable indices by adding offset. */
    public static LambdaExpression renumberCapturedVariables(LambdaExpression expression, int offset) {
        if (expression == null || offset == 0) {
            return expression;
        }

        return switch (expression) {
            case LambdaExpression.CapturedVariable capturedVar ->
                new LambdaExpression.CapturedVariable(capturedVar.index() + offset, capturedVar.type());

            case LambdaExpression.BinaryOp binOp ->
                new LambdaExpression.BinaryOp(
                        renumberCapturedVariables(binOp.left(), offset),
                        binOp.operator(),
                        renumberCapturedVariables(binOp.right(), offset));

            case LambdaExpression.UnaryOp unaryOp ->
                new LambdaExpression.UnaryOp(
                        unaryOp.operator(),
                        renumberCapturedVariables(unaryOp.operand(), offset));

            case LambdaExpression.MethodCall methodCall -> {
                LambdaExpression newTarget = renumberCapturedVariables(methodCall.target(), offset);
                List<LambdaExpression> newArgs = new ArrayList<>();
                for (LambdaExpression arg : methodCall.arguments()) {
                    newArgs.add(renumberCapturedVariables(arg, offset));
                }
                yield new LambdaExpression.MethodCall(newTarget, methodCall.methodName(), newArgs, methodCall.returnType());
            }

            case LambdaExpression.ConstructorCall constructorCall -> {
                List<LambdaExpression> newArgs = new ArrayList<>();
                for (LambdaExpression arg : constructorCall.arguments()) {
                    newArgs.add(renumberCapturedVariables(arg, offset));
                }
                yield new LambdaExpression.ConstructorCall(constructorCall.className(), newArgs, constructorCall.resultType());
            }

            case LambdaExpression.Cast cast ->
                new LambdaExpression.Cast(renumberCapturedVariables(cast.expression(), offset), cast.targetType());

            case LambdaExpression.InstanceOf instanceOf ->
                new LambdaExpression.InstanceOf(renumberCapturedVariables(instanceOf.expression(), offset), instanceOf.targetType());

            case LambdaExpression.Conditional conditional ->
                new LambdaExpression.Conditional(
                        renumberCapturedVariables(conditional.condition(), offset),
                        renumberCapturedVariables(conditional.trueValue(), offset),
                        renumberCapturedVariables(conditional.falseValue(), offset));

            case LambdaExpression.InExpression inExpr ->
                new LambdaExpression.InExpression(
                        renumberCapturedVariables(inExpr.field(), offset),
                        renumberCapturedVariables(inExpr.collection(), offset),
                        inExpr.negated());

            case LambdaExpression.MemberOfExpression memberOfExpr ->
                new LambdaExpression.MemberOfExpression(
                        renumberCapturedVariables(memberOfExpr.value(), offset),
                        renumberCapturedVariables(memberOfExpr.collectionField(), offset),
                        memberOfExpr.negated());

            // No captured variables - return as-is (multi-pattern `_` requires Java 21 preview)
            case LambdaExpression.PathExpression ignored1 -> expression;
            case LambdaExpression.BiEntityFieldAccess ignored2 -> expression;
            case LambdaExpression.BiEntityPathExpression ignored3 -> expression;
            case LambdaExpression.BiEntityParameter ignored4 -> expression;
            default -> expression;
        };
    }

    /** Combines multiple predicates with AND. Throws if predicates is empty. */
    public static LambdaExpression combinePredicatesWithAnd(List<LambdaExpression> predicates) {
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException(CANNOT_COMBINE_EMPTY_PREDICATE_LIST);
        }

        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        // Chain predicates with AND: (p1 AND p2 AND p3 AND ...)
        LambdaExpression combined = predicates.get(0);
        for (int i = 1; i < predicates.size(); i++) {
            combined = and(combined, predicates.get(i));
        }

        return combined;
    }

    /** Combines two predicates with AND if both non-null. */
    public static LambdaExpression combinePredicatesWithAnd(LambdaExpression predicate1, LambdaExpression predicate2) {
        if (predicate1 != null && predicate2 != null) {
            return and(predicate1, predicate2);
        } else if (predicate1 != null) {
            return predicate1;
        } else {
            return predicate2;
        }
    }

    /** Validates CapturedVariable indices are within bounds (build-time safety check). */
    public static void validateCapturedVariableIndices(LambdaExpression expression, int expectedCount) {
        if (expression == null) {
            return;
        }

        Set<Integer> capturedIndices = new HashSet<>();
        collectCapturedVariableIndices(expression, capturedIndices);

        // If no captured variables in expression, nothing to validate
        if (capturedIndices.isEmpty()) {
            return;
        }

        // If we have captured variables but expectedCount is 0, that's an error
        if (expectedCount == 0) {
            throw new IllegalStateException(String.format(
                    "Expression contains %d captured variable(s) but expectedCount=0. " +
                    "This indicates a mismatch between bytecode analysis and captured variable counting.",
                    capturedIndices.size()));
        }

        for (int index : capturedIndices) {
            // Note: CapturedVariable constructor already validates index >= 0,
            // so we only need to check the upper bound here
            if (index >= expectedCount) {
                throw new IllegalStateException(String.format(
                        "CapturedVariable index %d is out of bounds (expectedCount=%d). " +
                        "This indicates a mismatch between bytecode analysis and captured variable counting. " +
                        "The generated code would fail with ArrayIndexOutOfBoundsException at runtime.",
                        index, expectedCount));
            }
        }
    }

    /** Validates captured variable indices for multiple expressions. */
    public static void validateCapturedVariableIndices(int expectedCount, LambdaExpression... expressions) {
        for (LambdaExpression expression : expressions) {
            if (expression != null) {
                validateCapturedVariableIndices(expression, expectedCount);
            }
        }
    }
}
