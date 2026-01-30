package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsScalarSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNegatedSubqueryComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNullCheckPattern;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isSubqueryBooleanComparison;
import static io.quarkiverse.qubit.deployment.generation.GizmoHelper.buildConstructorExpression;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.generation.UnsupportedExpressionException;
import io.quarkiverse.qubit.deployment.generation.methodcall.BiEntityMethodCallContext;
import io.quarkiverse.qubit.deployment.generation.methodcall.GenerationResult;
import io.quarkiverse.qubit.deployment.generation.methodcall.MethodCallHandlerChain;
import io.quarkus.gizmo.ResultHandle;

/** Builds JPA Criteria expressions for bi-entity (join) queries. */
public enum BiEntityExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates JPA Predicate from bi-entity lambda AST. */
    public @Nullable ResultHandle generateBiEntityPredicate(
            BiEntityContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(ctx, binOp);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperation(ctx, unOp);

            case BiEntityFieldAccess biField -> {
                ResultHandle path = generateBiEntityFieldPath(ctx, biField);
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.method(), ctx.cb(), path, biField.fieldType());
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle path = generateBiEntityPathPath(ctx, biPath);
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.method(), ctx.cb(), path, biPath.resultType());
            }

            case LambdaExpression.FieldAccess field -> {
                // Single-entity field in bi-entity context (from root)
                ResultHandle path = ctx.helper().generateFieldAccess(ctx.method(), field, ctx.root());
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.method(), ctx.cb(), path, field.fieldType());
            }

            case PathExpression pathExpr -> {
                // Single-entity path in bi-entity context (from root)
                ResultHandle path = ctx.helper().generatePathExpression(ctx.method(), pathExpr, ctx.root());
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.method(), ctx.cb(), path, pathExpr.resultType());
            }

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(ctx, methodCall);

            default -> null;
        };
    }

    /** Generates JPA Predicate from bi-entity lambda AST with subquery support. */
    public @Nullable ResultHandle generateBiEntityPredicateWithSubqueries(
            BiEntitySubqueryContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ExistsSubquery existsSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildExistsSubquery(
                        ctx.method(), existsSubquery, ctx.cb(), ctx.query(), ctx.root(), ctx.capturedValues());

            case InSubquery inSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildInSubquery(
                        ctx.method(), inSubquery, ctx.cb(), ctx.query(), ctx.root(), ctx.capturedValues());

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperationWithSubqueries(ctx, binOp);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperationWithSubqueries(ctx, unOp);

            default -> generateBiEntityPredicate(ctx, expression);
        };
    }

    /** Generates JPA Expression from bi-entity lambda AST with subquery support. */
    public @Nullable ResultHandle generateBiEntityExpressionWithSubqueries(
            BiEntitySubqueryContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildScalarSubquery(
                        ctx.method(), scalarSubquery, ctx.cb(), ctx.query(), ctx.root(), ctx.capturedValues());

            default -> generateBiEntityExpressionAsJpaExpression(ctx, expression);
        };
    }

    /** Generates JPA Expression from bi-entity lambda AST. */
    public @Nullable ResultHandle generateBiEntityExpressionAsJpaExpression(
            BiEntityContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case BiEntityFieldAccess biField ->
                generateBiEntityFieldPath(ctx, biField);

            case BiEntityPathExpression biPath ->
                generateBiEntityPathPath(ctx, biPath);

            case LambdaExpression.FieldAccess field ->
                // Single-entity field defaults to root
                ctx.helper().generateFieldAccess(ctx.method(), field, ctx.root());

            case PathExpression pathExpr ->
                // Single-entity path defaults to root
                ctx.helper().generatePathExpression(ctx.method(), pathExpr, ctx.root());

            case LambdaExpression.Constant constant -> {
                ResultHandle constantValue = ctx.helper().generateConstant(ctx.method(), constant);
                yield ctx.helper().wrapAsLiteral(ctx.method(), ctx.cb(), constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                ctx.helper().loadAndWrapCapturedValue(ctx.method(), ctx.cb(), capturedVar, ctx.capturedValues());

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(ctx, methodCall);

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(ctx, binOp);

            case LambdaExpression.CorrelatedVariable correlated ->
                ctx.helper().generateCorrelatedFieldExpression(ctx.method(), correlated, ctx.root());

            default -> null;
        };
    }

    /** Generates JPA Selection from bi-entity projection expression AST. */
    public @Nullable ResultHandle generateBiEntityProjection(
            BiEntityContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.ConstructorCall constructorCall ->
                generateBiEntityConstructorCall(ctx, constructorCall);

            case BiEntityFieldAccess biField ->
                generateBiEntityFieldPath(ctx, biField);

            case BiEntityPathExpression biPath ->
                generateBiEntityPathPath(ctx, biPath);

            case LambdaExpression.FieldAccess field ->
                ctx.helper().generateFieldAccess(ctx.method(), field, ctx.root());

            case PathExpression pathExpr ->
                ctx.helper().generatePathExpression(ctx.method(), pathExpr, ctx.root());

            // For other expression types, delegate to generateBiEntityExpressionAsJpaExpression
            default -> generateBiEntityExpressionAsJpaExpression(ctx, expression);
        };
    }

    /** Generates raw value from bi-entity expression (for method arguments). */
    public @Nullable ResultHandle generateBiEntityExpression(
            BiEntityContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case BiEntityFieldAccess biField ->
                generateBiEntityFieldPath(ctx, biField);

            case BiEntityPathExpression biPath ->
                generateBiEntityPathPath(ctx, biPath);

            case LambdaExpression.FieldAccess field ->
                ctx.helper().generateFieldAccess(ctx.method(), field, ctx.root());

            case PathExpression pathExpr ->
                ctx.helper().generatePathExpression(ctx.method(), pathExpr, ctx.root());

            case LambdaExpression.Constant constant ->
                ctx.helper().generateConstant(ctx.method(), constant);

            case LambdaExpression.CapturedVariable capturedVar ->
                ctx.helper().loadCapturedValue(ctx.method(), capturedVar, ctx.capturedValues());

            case LambdaExpression.CorrelatedVariable correlated ->
                ctx.helper().generateCorrelatedFieldExpression(ctx.method(), correlated, ctx.root());

            default -> null;
        };
    }

    // ========== Private Helper Methods ==========

    private ResultHandle getBaseForEntityPosition(EntityPosition position, ResultHandle root, ResultHandle join) {
        return position == EntityPosition.FIRST ? root : join;
    }

    private ResultHandle generateBiEntityFieldPath(BiEntityContext ctx, BiEntityFieldAccess biField) {
        ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), ctx.root(), ctx.join());
        return ctx.helper().generateFieldAccess(ctx.method(),
                new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
    }

    private ResultHandle generateBiEntityPathPath(BiEntityContext ctx, BiEntityPathExpression biPath) {
        ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), ctx.root(), ctx.join());
        return ctx.helper().generatePathExpression(ctx.method(),
                new PathExpression(biPath.segments(), biPath.resultType()), base);
    }

    private ResultHandle generateBiEntityBinaryOperation(BiEntityContext ctx, LambdaExpression.BinaryOp binOp) {
        // Check for string concatenation
        if (ctx.helper().isStringConcatenation(binOp)) {
            ResultHandle left = generateBiEntityExpressionAsJpaExpression(ctx, binOp.left());
            ResultHandle right = generateBiEntityExpressionAsJpaExpression(ctx, binOp.right());
            return ctx.helper().generateStringConcatenation(ctx.method(), ctx.cb(), left, right);
        }

        // Check for arithmetic
        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateBiEntityExpressionAsJpaExpression(ctx, binOp.left());
            ResultHandle right = generateBiEntityExpressionAsJpaExpression(ctx, binOp.right());
            return ArithmeticExpressionBuilder.INSTANCE.buildArithmeticOperation(
                    ctx.method(), binOp.operator(), ctx.cb(), left, right);
        }

        // Logical operations
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateBiEntityPredicate(ctx, binOp.left());
            ResultHandle right = generateBiEntityPredicate(ctx, binOp.right());
            return ctx.helper().combinePredicates(ctx.method(), ctx.cb(), left, right, binOp.operator());
        }

        // Null check
        if (isNullCheckPattern(binOp)) {
            return generateBiEntityNullCheckPredicate(ctx, binOp);
        }

        // Default: comparison operation
        ResultHandle left = generateBiEntityExpressionAsJpaExpression(ctx, binOp.left());
        ResultHandle right = generateBiEntityExpressionAsJpaExpression(ctx, binOp.right());
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(
                ctx.method(), binOp.operator(), ctx.cb(), left, right);
    }

    private ResultHandle generateBiEntityNullCheckPredicate(BiEntityContext ctx, LambdaExpression.BinaryOp binOp) {
        LambdaExpression nonNullExpr = ctx.helper().extractNonNullExpression(binOp);
        ResultHandle expression = generateBiEntityExpressionAsJpaExpression(ctx, nonNullExpr);
        return ctx.helper().generateNullCheckPredicate(ctx.method(), ctx.cb(), expression, binOp.operator());
    }

    private ResultHandle generateBiEntityUnaryOperation(BiEntityContext ctx, LambdaExpression.UnaryOp unOp) {
        ResultHandle operand = generateBiEntityPredicate(ctx, unOp.operand());
        return ctx.helper().applyUnaryOperator(ctx.method(), ctx.cb(), operand, unOp.operator());
    }

    private ResultHandle generateBiEntityBinaryOperationWithSubqueries(
            BiEntitySubqueryContext ctx, LambdaExpression.BinaryOp binOp) {

        // Check for logical operations (AND, OR) - need to recurse with subquery support
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateBiEntityPredicateWithSubqueries(ctx, binOp.left());
            ResultHandle right = generateBiEntityPredicateWithSubqueries(ctx, binOp.right());
            return ctx.helper().combinePredicates(ctx.method(), ctx.cb(), left, right, binOp.operator());
        }

        // Only scalar subqueries can be used in comparisons. EXISTS/IN are predicates, not expressions.
        if (containsScalarSubquery(binOp.left()) || containsScalarSubquery(binOp.right())) {
            ResultHandle left = generateBiEntityExpressionWithSubqueries(ctx, binOp.left());
            ResultHandle right = generateBiEntityExpressionWithSubqueries(ctx, binOp.right());
            return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(
                    ctx.method(), binOp.operator(), ctx.cb(), left, right);
        }

        // This handles bytecode patterns where boolean short-circuit creates comparison to constant.
        if (containsSubquery(binOp.left()) || containsSubquery(binOp.right())) {
            return handleSubqueryPredicate(ctx, binOp);
        }

        // No subqueries - delegate to standard bi-entity binary operation
        return generateBiEntityBinaryOperation(ctx, binOp);
    }

    /** Handles subquery predicate comparison patterns (EXISTS == true, etc.). */
    private ResultHandle handleSubqueryPredicate(BiEntitySubqueryContext ctx, LambdaExpression.BinaryOp binOp) {

        // If comparing a subquery predicate to a boolean constant, simplify
        if (isSubqueryBooleanComparison(binOp)) {
            boolean leftHasSubquery = containsSubquery(binOp.left());
            LambdaExpression subqueryExpr = leftHasSubquery ? binOp.left() : binOp.right();
            LambdaExpression constantExpr = leftHasSubquery ? binOp.right() : binOp.left();
            ResultHandle predicate = generateBiEntityPredicateWithSubqueries(ctx, subqueryExpr);

            // If comparing to false or using NE with true, negate the result
            return isNegatedSubqueryComparison(binOp.operator(), constantExpr)
                    ? ctx.method().invokeInterfaceMethod(CB_NOT, ctx.cb(), predicate)
                    : predicate;
        }

        // For other patterns with subqueries, recursively process with subquery support
        ResultHandle left = generateBiEntityPredicateWithSubqueries(ctx, binOp.left());
        ResultHandle right = generateBiEntityPredicateWithSubqueries(ctx, binOp.right());
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(
                ctx.method(), binOp.operator(), ctx.cb(), left, right);
    }

    private ResultHandle generateBiEntityUnaryOperationWithSubqueries(
            BiEntitySubqueryContext ctx, LambdaExpression.UnaryOp unOp) {

        ResultHandle operand = generateBiEntityPredicateWithSubqueries(ctx, unOp.operand());
        return ctx.helper().applyUnaryOperator(ctx.method(), ctx.cb(), operand, unOp.operator());
    }

    /** Generates bi-entity method call via MethodCallHandlerChain. */
    private ResultHandle generateBiEntityMethodCall(BiEntityContext ctx, LambdaExpression.MethodCall methodCall) {
        // Create bi-entity context for polymorphic dispatch
        BiEntityMethodCallContext context = new BiEntityMethodCallContext(
                ctx.method(),
                methodCall,
                ctx.cb(),
                ctx.root(),
                ctx.join(),
                ctx.capturedValues(),
                ExpressionBuilderRegistry.createDefault(),
                ctx.helper());

        // Delegate to unified handler chain
        GenerationResult result = MethodCallHandlerChain.defaultInstance().handleMethodCall(context);

        return switch (result) {
            case GenerationResult.Success(var handle) -> handle;
            case GenerationResult.Unsupported(var methodName, var reason) ->
                throw new UnsupportedExpressionException(methodCall, "bi-entity method call: " + methodName + " - " + reason);
        };
    }

    private ResultHandle generateBiEntityConstructorCall(
            BiEntityContext ctx, LambdaExpression.ConstructorCall constructorCall) {

        ResultHandle resultClassHandle = ctx.helper().loadDtoClass(ctx.method(), constructorCall.className());

        return buildConstructorExpression(ctx.method(), ctx.cb(), resultClassHandle, constructorCall.arguments(),
                arg -> generateBiEntityExpressionAsJpaExpression(ctx, arg));
    }
}
