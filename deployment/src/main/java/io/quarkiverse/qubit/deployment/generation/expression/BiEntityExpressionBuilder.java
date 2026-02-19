package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsScalarSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNegatedSubqueryComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNullCheckPattern;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isSubqueryBooleanComparison;
import static io.quarkiverse.qubit.deployment.generation.GizmoHelper.buildConstructorExpression;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_BETWEEN_EXPR;
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
import io.quarkus.gizmo2.Expr;

/**
 * Builds JPA Criteria expressions for bi-entity (join) queries.
 */
public enum BiEntityExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates JPA Predicate from bi-entity lambda AST. */
    public @Nullable Expr generateBiEntityPredicate(
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
                Expr path = generateBiEntityFieldPath(ctx, biField);
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.bc(), ctx.cb(), path, biField.fieldType());
            }

            case BiEntityPathExpression biPath -> {
                Expr path = generateBiEntityPathPath(ctx, biPath);
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.bc(), ctx.cb(), path, biPath.resultType());
            }

            case LambdaExpression.FieldAccess field -> {
                // Single-entity field in bi-entity context (from root)
                Expr path = ctx.helper().generateFieldAccess(ctx.bc(), field, ctx.root());
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.bc(), ctx.cb(), path, field.fieldType());
            }

            case PathExpression pathExpr -> {
                // Single-entity path in bi-entity context (from root)
                Expr path = ctx.helper().generatePathExpression(ctx.bc(), pathExpr, ctx.root());
                yield ctx.helper().wrapBooleanAsPredicateIfNeeded(ctx.bc(), ctx.cb(), path, pathExpr.resultType());
            }

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(ctx, methodCall);

            case LambdaExpression.MathFunction mathFunc ->
                generateBiEntityMathFunction(ctx, mathFunc);

            case LambdaExpression.SqlCast _ -> null; // SqlCast not yet supported in bi-entity context

            default -> null;
        };
    }

    /** Generates JPA Predicate from bi-entity lambda AST with subquery support. */
    public @Nullable Expr generateBiEntityPredicateWithSubqueries(
            BiEntitySubqueryContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ExistsSubquery existsSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildExistsSubquery(
                        ctx.bc(), existsSubquery, ctx.cb(), ctx.query(), ctx.root(), ctx.capturedValues());

            case InSubquery inSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildInSubquery(
                        ctx.bc(), inSubquery, ctx.cb(), ctx.query(), ctx.root(), ctx.capturedValues());

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperationWithSubqueries(ctx, binOp);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperationWithSubqueries(ctx, unOp);

            default -> generateBiEntityPredicate(ctx, expression);
        };
    }

    /** Generates JPA Expression from bi-entity lambda AST with subquery support. */
    public @Nullable Expr generateBiEntityExpressionWithSubqueries(
            BiEntitySubqueryContext ctx, @Nullable LambdaExpression expression) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildScalarSubquery(
                        ctx.bc(), scalarSubquery, ctx.cb(), ctx.query(), ctx.root(), ctx.capturedValues());

            default -> generateBiEntityExpressionAsJpaExpression(ctx, expression);
        };
    }

    /** Generates JPA Expression from bi-entity lambda AST. */
    public @Nullable Expr generateBiEntityExpressionAsJpaExpression(
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
                ctx.helper().generateFieldAccess(ctx.bc(), field, ctx.root());

            case PathExpression pathExpr ->
                // Single-entity path defaults to root
                ctx.helper().generatePathExpression(ctx.bc(), pathExpr, ctx.root());

            case LambdaExpression.Constant constant -> {
                Expr constantValue = ctx.helper().generateConstant(ctx.bc(), constant);
                yield ctx.helper().wrapAsLiteral(ctx.bc(), ctx.cb(), constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                ctx.helper().loadAndWrapCapturedValue(ctx.bc(), ctx.cb(), capturedVar, ctx.capturedValues());

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(ctx, methodCall);

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(ctx, binOp);

            case LambdaExpression.CorrelatedVariable correlated ->
                ctx.helper().generateCorrelatedFieldExpression(ctx.bc(), correlated, ctx.root());

            case LambdaExpression.MathFunction mathFunc ->
                generateBiEntityMathFunction(ctx, mathFunc);

            case LambdaExpression.SqlCast _ -> null; // SqlCast not yet supported in bi-entity context

            default -> null;
        };
    }

    /** Generates JPA Selection from bi-entity projection expression AST. */
    public @Nullable Expr generateBiEntityProjection(
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
                ctx.helper().generateFieldAccess(ctx.bc(), field, ctx.root());

            case PathExpression pathExpr ->
                ctx.helper().generatePathExpression(ctx.bc(), pathExpr, ctx.root());

            // For other expression types, delegate to generateBiEntityExpressionAsJpaExpression
            default -> generateBiEntityExpressionAsJpaExpression(ctx, expression);
        };
    }

    /** Generates raw value from bi-entity expression (for method arguments). */
    public @Nullable Expr generateBiEntityExpression(
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
                ctx.helper().generateFieldAccess(ctx.bc(), field, ctx.root());

            case PathExpression pathExpr ->
                ctx.helper().generatePathExpression(ctx.bc(), pathExpr, ctx.root());

            case LambdaExpression.Constant constant ->
                ctx.helper().generateConstant(ctx.bc(), constant);

            case LambdaExpression.CapturedVariable capturedVar ->
                ctx.helper().loadCapturedValue(ctx.bc(), capturedVar, ctx.capturedValues());

            case LambdaExpression.CorrelatedVariable correlated ->
                ctx.helper().generateCorrelatedFieldExpression(ctx.bc(), correlated, ctx.root());

            case LambdaExpression.SqlCast _ -> null; // SqlCast not yet supported in bi-entity context

            default -> null;
        };
    }

    private Expr getBaseForEntityPosition(EntityPosition position, Expr root, Expr join) {
        return position == EntityPosition.FIRST ? root : join;
    }

    private Expr generateBiEntityFieldPath(BiEntityContext ctx, BiEntityFieldAccess biField) {
        Expr base = getBaseForEntityPosition(biField.entityPosition(), ctx.root(), ctx.join());
        return ctx.helper().generateFieldAccess(ctx.bc(),
                new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
    }

    private Expr generateBiEntityPathPath(BiEntityContext ctx, BiEntityPathExpression biPath) {
        Expr base = getBaseForEntityPosition(biPath.entityPosition(), ctx.root(), ctx.join());
        return ctx.helper().generatePathExpression(ctx.bc(),
                new PathExpression(biPath.segments(), biPath.resultType()), base);
    }

    private Expr generateBiEntityBinaryOperation(BiEntityContext ctx, LambdaExpression.BinaryOp binOp) {
        // Check for string concatenation
        if (ctx.helper().isStringConcatenation(binOp)) {
            Expr left = generateBiEntityExpressionAsJpaExpression(ctx, binOp.left());
            Expr right = generateBiEntityExpressionAsJpaExpression(ctx, binOp.right());
            return ctx.helper().generateStringConcatenation(ctx.bc(), ctx.cb(), left, right);
        }

        // Check for arithmetic
        if (PatternDetector.isArithmeticExpression(binOp)) {
            Expr left = generateBiEntityExpressionAsJpaExpression(ctx, binOp.left());
            Expr right = generateBiEntityExpressionAsJpaExpression(ctx, binOp.right());
            return ArithmeticExpressionBuilder.INSTANCE.buildArithmeticOperation(
                    ctx.bc(), binOp.operator(), ctx.cb(), left, right);
        }

        // Logical operations
        if (isLogicalOperation(binOp)) {
            // BETWEEN optimization: detect field >= low && field <= high
            if (binOp.operator() == LambdaExpression.BinaryOp.Operator.AND) {
                PatternDetector.BetweenComponents between = PatternDetector.detectBetween(binOp);
                if (between != null) {
                    return generateBiEntityBetweenPredicate(ctx, between);
                }
            }
            Expr left = generateBiEntityPredicate(ctx, binOp.left());
            Expr right = generateBiEntityPredicate(ctx, binOp.right());
            return ctx.helper().combinePredicates(ctx.bc(), ctx.cb(), left, right, binOp.operator());
        }

        // Null check
        if (isNullCheckPattern(binOp)) {
            return generateBiEntityNullCheckPredicate(ctx, binOp);
        }

        // Default: comparison operation
        Expr left = generateBiEntityExpressionAsJpaExpression(ctx, binOp.left());
        Expr right = generateBiEntityExpressionAsJpaExpression(ctx, binOp.right());
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(
                ctx.bc(), binOp.operator(), ctx.cb(), left, right);
    }

    private Expr generateBiEntityNullCheckPredicate(BiEntityContext ctx, LambdaExpression.BinaryOp binOp) {
        LambdaExpression nonNullExpr = ctx.helper().extractNonNullExpression(binOp);
        Expr expression = generateBiEntityExpressionAsJpaExpression(ctx, nonNullExpr);
        return ctx.helper().generateNullCheckPredicate(ctx.bc(), ctx.cb(), expression, binOp.operator());
    }

    /** Generates cb.between(field, lowerBound, upperBound) for detected BETWEEN patterns in bi-entity context. */
    private Expr generateBiEntityBetweenPredicate(BiEntityContext ctx, PatternDetector.BetweenComponents between) {
        Expr fieldExpr = generateBiEntityExpressionAsJpaExpression(ctx, between.field());
        Expr lowerExpr = generateBiEntityExpressionAsJpaExpression(ctx, between.lowerBound());
        Expr upperExpr = generateBiEntityExpressionAsJpaExpression(ctx, between.upperBound());
        return ctx.bc().invokeInterface(CB_BETWEEN_EXPR, ctx.cb(), fieldExpr, lowerExpr, upperExpr);
    }

    private Expr generateBiEntityUnaryOperation(BiEntityContext ctx, LambdaExpression.UnaryOp unOp) {
        Expr operand = generateBiEntityPredicate(ctx, unOp.operand());
        return ctx.helper().applyUnaryOperator(ctx.bc(), ctx.cb(), operand, unOp.operator());
    }

    private Expr generateBiEntityBinaryOperationWithSubqueries(
            BiEntitySubqueryContext ctx, LambdaExpression.BinaryOp binOp) {

        // Check for logical operations (AND, OR) - need to recurse with subquery support
        if (isLogicalOperation(binOp)) {
            Expr left = generateBiEntityPredicateWithSubqueries(ctx, binOp.left());
            Expr right = generateBiEntityPredicateWithSubqueries(ctx, binOp.right());
            return ctx.helper().combinePredicates(ctx.bc(), ctx.cb(), left, right, binOp.operator());
        }

        // Only scalar subqueries can be used in comparisons. EXISTS/IN are predicates, not expressions.
        if (containsScalarSubquery(binOp.left()) || containsScalarSubquery(binOp.right())) {
            Expr left = generateBiEntityExpressionWithSubqueries(ctx, binOp.left());
            Expr right = generateBiEntityExpressionWithSubqueries(ctx, binOp.right());
            return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(
                    ctx.bc(), binOp.operator(), ctx.cb(), left, right);
        }

        // This handles bytecode patterns where boolean short-circuit creates comparison to constant.
        if (containsSubquery(binOp.left()) || containsSubquery(binOp.right())) {
            return handleSubqueryPredicate(ctx, binOp);
        }

        // No subqueries - delegate to standard bi-entity binary operation
        return generateBiEntityBinaryOperation(ctx, binOp);
    }

    /** Handles subquery predicate comparison patterns (EXISTS == true, etc.). */
    private Expr handleSubqueryPredicate(BiEntitySubqueryContext ctx, LambdaExpression.BinaryOp binOp) {

        // If comparing a subquery predicate to a boolean constant, simplify
        if (isSubqueryBooleanComparison(binOp)) {
            boolean leftHasSubquery = containsSubquery(binOp.left());
            LambdaExpression subqueryExpr = leftHasSubquery ? binOp.left() : binOp.right();
            LambdaExpression constantExpr = leftHasSubquery ? binOp.right() : binOp.left();
            Expr predicate = generateBiEntityPredicateWithSubqueries(ctx, subqueryExpr);

            // If comparing to false or using NE with true, negate the result
            return isNegatedSubqueryComparison(binOp.operator(), constantExpr)
                    ? ctx.bc().invokeInterface(CB_NOT, ctx.cb(), predicate)
                    : predicate;
        }

        // For other patterns with subqueries, recursively process with subquery support
        Expr left = generateBiEntityPredicateWithSubqueries(ctx, binOp.left());
        Expr right = generateBiEntityPredicateWithSubqueries(ctx, binOp.right());
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(
                ctx.bc(), binOp.operator(), ctx.cb(), left, right);
    }

    private Expr generateBiEntityUnaryOperationWithSubqueries(
            BiEntitySubqueryContext ctx, LambdaExpression.UnaryOp unOp) {

        Expr operand = generateBiEntityPredicateWithSubqueries(ctx, unOp.operand());
        return ctx.helper().applyUnaryOperator(ctx.bc(), ctx.cb(), operand, unOp.operator());
    }

    /** Generates bi-entity method call via MethodCallHandlerChain. */
    private Expr generateBiEntityMethodCall(BiEntityContext ctx, LambdaExpression.MethodCall methodCall) {
        // Create bi-entity context for polymorphic dispatch
        BiEntityMethodCallContext context = new BiEntityMethodCallContext(
                ctx.bc(),
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

    private Expr generateBiEntityConstructorCall(
            BiEntityContext ctx, LambdaExpression.ConstructorCall constructorCall) {

        Expr resultClassHandle = ctx.helper().loadDtoClass(ctx.bc(), constructorCall.className());

        return buildConstructorExpression(ctx.bc(), ctx.cb(), resultClassHandle, constructorCall.arguments(),
                arg -> generateBiEntityExpressionAsJpaExpression(ctx, arg));
    }

    /** Generates JPA math function expression in bi-entity context. */
    private Expr generateBiEntityMathFunction(BiEntityContext ctx, LambdaExpression.MathFunction mathFunc) {
        // Generate the primary operand as a JPA Expression
        Expr operandExpr = generateBiEntityExpressionAsJpaExpression(ctx, mathFunc.operand());

        // Generate the second operand for binary operations
        Expr secondExpr = null;
        if (mathFunc.op().isBinary()) {
            if (mathFunc.op() == LambdaExpression.MathFunction.MathOp.ROUND) {
                // round() second arg is Integer, not Expression -- use raw value
                secondExpr = generateBiEntityExpression(ctx, mathFunc.secondOperand());
            } else {
                // power() second arg is Expression
                secondExpr = generateBiEntityExpressionAsJpaExpression(ctx, mathFunc.secondOperand());
            }
        }

        return MathExpressionBuilder.build(ctx.bc(), ctx.cb(), operandExpr, secondExpr, mathFunc.op());
    }
}
