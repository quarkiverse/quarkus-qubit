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
            case LambdaExpression.CapturedVariable(var index, var type, var name) ->
                new LambdaExpression.CapturedVariable(index + offset, type, name);

            case LambdaExpression.BinaryOp(var left, var operator, var right) ->
                new LambdaExpression.BinaryOp(
                        renumberCapturedVariables(left, offset),
                        operator,
                        renumberCapturedVariables(right, offset));

            case LambdaExpression.UnaryOp(var operator, var operand) ->
                new LambdaExpression.UnaryOp(
                        operator,
                        renumberCapturedVariables(operand, offset));

            case LambdaExpression.MethodCall(var target, var methodName, var arguments, var returnType) -> {
                LambdaExpression newTarget = renumberCapturedVariables(target, offset);
                List<LambdaExpression> newArgs = new ArrayList<>();
                for (LambdaExpression arg : arguments) {
                    newArgs.add(renumberCapturedVariables(arg, offset));
                }
                yield new LambdaExpression.MethodCall(newTarget, methodName, newArgs, returnType);
            }

            case LambdaExpression.ConstructorCall(var className, var arguments, var resultType) -> {
                List<LambdaExpression> newArgs = new ArrayList<>();
                for (LambdaExpression arg : arguments) {
                    newArgs.add(renumberCapturedVariables(arg, offset));
                }
                yield new LambdaExpression.ConstructorCall(className, newArgs, resultType);
            }

            case LambdaExpression.Cast(var innerExpr, var targetType) ->
                new LambdaExpression.Cast(renumberCapturedVariables(innerExpr, offset), targetType);

            case LambdaExpression.InstanceOf(var innerExpr, var targetType) ->
                new LambdaExpression.InstanceOf(renumberCapturedVariables(innerExpr, offset), targetType);

            case LambdaExpression.Conditional(var condition, var trueValue, var falseValue) ->
                new LambdaExpression.Conditional(
                        renumberCapturedVariables(condition, offset),
                        renumberCapturedVariables(trueValue, offset),
                        renumberCapturedVariables(falseValue, offset));

            case LambdaExpression.InExpression(var field, var collection, var negated) ->
                new LambdaExpression.InExpression(
                        renumberCapturedVariables(field, offset),
                        renumberCapturedVariables(collection, offset),
                        negated);

            case LambdaExpression.MemberOfExpression(var value, var collectionField, var negated) ->
                new LambdaExpression.MemberOfExpression(
                        renumberCapturedVariables(value, offset),
                        renumberCapturedVariables(collectionField, offset),
                        negated);

            // No captured variables - return as-is
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
