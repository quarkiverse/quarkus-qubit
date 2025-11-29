package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.qusaq.deployment.LambdaExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.and;

/**
 * Utility class for captured variable operations in lambda expressions.
 * <p>
 * Extracted from CallSiteProcessor (ARCH-001) to improve maintainability and reusability.
 * <p>
 * Provides methods for:
 * <ul>
 *   <li>Counting captured variables in expressions</li>
 *   <li>Collecting captured variable indices</li>
 *   <li>Renumbering captured variable indices for predicate combination</li>
 * </ul>
 *
 * @see CallSiteProcessor
 */
public final class CapturedVariableHelper {

    private CapturedVariableHelper() {
        // Utility class
    }

    /**
     * Counts distinct captured variables in lambda expression.
     *
     * @param expression the lambda expression to analyze
     * @return the number of distinct captured variables
     */
    public static int countCapturedVariables(LambdaExpression expression) {
        Set<Integer> capturedIndices = new HashSet<>();
        collectCapturedVariableIndices(expression, capturedIndices);
        return capturedIndices.size();
    }

    /**
     * Counts total captured variables across all sort expressions.
     *
     * @param sortExpressions the list of sort expressions
     * @return 0 if sortExpressions is null or empty, otherwise the total count
     */
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

    /**
     * Recursively collects captured variable indices.
     * <p>
     * Uses Java 21 pattern matching switch for cleaner type dispatch.
     *
     * @param expression the lambda expression to analyze
     * @param capturedIndices the set to populate with indices
     */
    public static void collectCapturedVariableIndices(LambdaExpression expression, Set<Integer> capturedIndices) {
        if (expression == null) {
            return;
        }

        // Java 21 pattern matching switch for type dispatch
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
                // Phase 2.4: Handle DTO constructor calls
                for (LambdaExpression arg : constructorCall.arguments()) {
                    collectCapturedVariableIndices(arg, capturedIndices);
                }
            }

            case LambdaExpression.InExpression inExpr -> {
                // Iteration 5: Handle IN clause expressions
                collectCapturedVariableIndices(inExpr.field(), capturedIndices);
                collectCapturedVariableIndices(inExpr.collection(), capturedIndices);
            }

            case LambdaExpression.MemberOfExpression memberOfExpr -> {
                // Iteration 5: Handle MEMBER OF expressions
                collectCapturedVariableIndices(memberOfExpr.value(), capturedIndices);
                collectCapturedVariableIndices(memberOfExpr.collectionField(), capturedIndices);
            }

            // These expression types don't contain captured variables - use separate cases
            // because multi-pattern cases with unnamed `_` require preview features in Java 21
            case LambdaExpression.PathExpression ignored1 -> { /* no captured variables */ }
            case LambdaExpression.BiEntityFieldAccess ignored2 -> { /* no captured variables */ }
            case LambdaExpression.BiEntityPathExpression ignored3 -> { /* no captured variables */ }
            case LambdaExpression.BiEntityParameter ignored4 -> { /* no captured variables */ }

            // Other expression types: FieldAccess, Constant, Parameter, NullLiteral, etc.
            default -> { /* no captured variables */ }
        }
    }

    /**
     * Renumbers captured variable indices in lambda expression tree by adding offset.
     * This ensures sequential indices when combining multiple lambdas.
     * <p>
     * Example: For second predicate with offset=1:
     * <ul>
     *   <li>CapturedVariable(0) becomes CapturedVariable(1)</li>
     *   <li>CapturedVariable(1) becomes CapturedVariable(2)</li>
     * </ul>
     * <p>
     * Recursively walks the entire AST tree to renumber all CapturedVariable nodes.
     * <p>
     * Uses Java 21 pattern matching switch expression for cleaner type dispatch.
     *
     * @param expression the lambda expression AST
     * @param offset the offset to add to each captured variable index
     * @return new expression tree with renumbered indices
     */
    public static LambdaExpression renumberCapturedVariables(LambdaExpression expression, int offset) {
        if (expression == null || offset == 0) {
            return expression;
        }

        // Java 21 pattern matching switch expression for type dispatch
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
                // Iteration 5: Handle IN clause expressions
                new LambdaExpression.InExpression(
                        renumberCapturedVariables(inExpr.field(), offset),
                        renumberCapturedVariables(inExpr.collection(), offset),
                        inExpr.negated());

            case LambdaExpression.MemberOfExpression memberOfExpr ->
                // Iteration 5: Handle MEMBER OF expressions
                new LambdaExpression.MemberOfExpression(
                        renumberCapturedVariables(memberOfExpr.value(), offset),
                        renumberCapturedVariables(memberOfExpr.collectionField(), offset),
                        memberOfExpr.negated());

            // These expression types don't contain captured variables, return as-is
            // Using separate cases because multi-pattern with `_` requires Java 21 preview
            case LambdaExpression.PathExpression ignored1 -> expression;
            case LambdaExpression.BiEntityFieldAccess ignored2 -> expression;
            case LambdaExpression.BiEntityPathExpression ignored3 -> expression;
            case LambdaExpression.BiEntityParameter ignored4 -> expression;

            // Other expression types: FieldAccess, Constant, Parameter, NullLiteral, etc.
            default -> expression;
        };
    }

    /**
     * Combines multiple predicates with AND operation.
     * Phase 2.5: Supports multiple where() chaining.
     *
     * @param predicates the list of predicate expressions to combine
     * @return a single combined expression with AND operations
     * @throws IllegalArgumentException if predicates is empty
     */
    public static LambdaExpression combinePredicatesWithAnd(List<LambdaExpression> predicates) {
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("Cannot combine empty predicate list");
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
}
