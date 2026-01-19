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
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

/** Builds JPA Criteria expressions for bi-entity (join) queries. */
public enum BiEntityExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates JPA Predicate from bi-entity lambda AST. */
    public @Nullable ResultHandle generateBiEntityPredicate(
            MethodCreator method,
            @Nullable LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperation(method, unOp, cb, root, join, capturedValues, helper);

            case BiEntityFieldAccess biField -> {
                ResultHandle path = generateBiEntityFieldPath(method, biField, root, join, helper);
                yield helper.wrapBooleanAsPredicateIfNeeded(method, cb, path, biField.fieldType());
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle path = generateBiEntityPathPath(method, biPath, root, join, helper);
                yield helper.wrapBooleanAsPredicateIfNeeded(method, cb, path, biPath.resultType());
            }

            case LambdaExpression.FieldAccess field -> {
                // Single-entity field in bi-entity context (from root)
                ResultHandle path = helper.generateFieldAccess(method, field, root);
                yield helper.wrapBooleanAsPredicateIfNeeded(method, cb, path, field.fieldType());
            }

            case PathExpression pathExpr -> {
                // Single-entity path in bi-entity context (from root)
                ResultHandle path = helper.generatePathExpression(method, pathExpr, root);
                yield helper.wrapBooleanAsPredicateIfNeeded(method, cb, path, pathExpr.resultType());
            }

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(method, methodCall, cb, root, join, capturedValues, helper);

            default -> null;
        };
    }

    /** Generates JPA Predicate from bi-entity lambda AST with subquery support. */
    public @Nullable ResultHandle generateBiEntityPredicateWithSubqueries(
            MethodCreator method,
            @Nullable LambdaExpression expression,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ExistsSubquery existsSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildExistsSubquery(method, existsSubquery, cb, query, root, capturedValues);

            case InSubquery inSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildInSubquery(method, inSubquery, cb, query, root, capturedValues);

            // Handle binary operations that may contain subqueries
            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperationWithSubqueries(method, binOp, cb, query, root, join, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperationWithSubqueries(method, unOp, cb, query, root, join, capturedValues, helper);

            // For non-subquery expressions, delegate to the original method
            default -> generateBiEntityPredicate(method, expression, cb, root, join, capturedValues, helper);
        };
    }

    /** Generates JPA Expression from bi-entity lambda AST with subquery support. */
    public @Nullable ResultHandle generateBiEntityExpressionWithSubqueries(
            MethodCreator method,
            @Nullable LambdaExpression expression,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                SubqueryExpressionBuilder.INSTANCE.buildScalarSubquery(method, scalarSubquery, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues, helper);
        };
    }

    /** Generates JPA Expression from bi-entity lambda AST. */
    public @Nullable ResultHandle generateBiEntityExpressionAsJpaExpression(
            MethodCreator method,
            @Nullable LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case BiEntityFieldAccess biField ->
                generateBiEntityFieldPath(method, biField, root, join, helper);

            case BiEntityPathExpression biPath ->
                generateBiEntityPathPath(method, biPath, root, join, helper);

            case LambdaExpression.FieldAccess field ->
                // Single-entity field defaults to root
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                // Single-entity path defaults to root
                helper.generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant -> {
                ResultHandle constantValue = helper.generateConstant(method, constant);
                yield helper.wrapAsLiteral(method, cb, constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                helper.loadAndWrapCapturedValue(method, cb, capturedVar, capturedValues);

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(method, methodCall, cb, root, join, capturedValues, helper);

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues, helper);

            case LambdaExpression.CorrelatedVariable correlated ->
                helper.generateCorrelatedFieldExpression(method, correlated, root);

            default -> null;
        };
    }

    /** Generates JPA Selection from bi-entity projection expression AST. */
    public @Nullable ResultHandle generateBiEntityProjection(
            MethodCreator method,
            @Nullable LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.ConstructorCall constructorCall ->
                generateBiEntityConstructorCall(method, constructorCall, cb, root, join, capturedValues, helper);

            case BiEntityFieldAccess biField ->
                generateBiEntityFieldPath(method, biField, root, join, helper);

            case BiEntityPathExpression biPath ->
                generateBiEntityPathPath(method, biPath, root, join, helper);

            case LambdaExpression.FieldAccess field ->
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                helper.generatePathExpression(method, pathExpr, root);

            // For other expression types, delegate to generateBiEntityExpressionAsJpaExpression
            default -> generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues, helper);
        };
    }

    /** Generates raw value from bi-entity expression (for method arguments). */
    public @Nullable ResultHandle generateBiEntityExpression(
            MethodCreator method,
            @Nullable LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case BiEntityFieldAccess biField ->
                generateBiEntityFieldPath(method, biField, root, join, helper);

            case BiEntityPathExpression biPath ->
                generateBiEntityPathPath(method, biPath, root, join, helper);

            case LambdaExpression.FieldAccess field ->
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                helper.generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant ->
                helper.generateConstant(method, constant);

            case LambdaExpression.CapturedVariable capturedVar ->
                helper.loadCapturedValue(method, capturedVar, capturedValues);

            case LambdaExpression.CorrelatedVariable correlated ->
                helper.generateCorrelatedFieldExpression(method, correlated, root);

            default -> null;
        };
    }

    // ========== Private Helper Methods ==========

    private ResultHandle getBaseForEntityPosition(EntityPosition position, ResultHandle root, ResultHandle join) {
        return position == EntityPosition.FIRST ? root : join;
    }

    private ResultHandle generateBiEntityFieldPath(
            MethodCreator method,
            BiEntityFieldAccess biField,
            ResultHandle root,
            ResultHandle join,
            ExpressionGeneratorHelper helper) {
        ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
        return helper.generateFieldAccess(method,
                new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
    }

    private ResultHandle generateBiEntityPathPath(
            MethodCreator method,
            BiEntityPathExpression biPath,
            ResultHandle root,
            ResultHandle join,
            ExpressionGeneratorHelper helper) {
        ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
        return helper.generatePathExpression(method,
                new PathExpression(biPath.segments(), biPath.resultType()), base);
    }

    private ResultHandle generateBiEntityBinaryOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Check for string concatenation
        if (helper.isStringConcatenation(binOp)) {
            ResultHandle left = generateBiEntityExpressionAsJpaExpression(method, binOp.left(), cb, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityExpressionAsJpaExpression(method, binOp.right(), cb, root, join, capturedValues, helper);
            return helper.generateStringConcatenation(method, cb, left, right);
        }

        // Check for arithmetic
        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateBiEntityExpressionAsJpaExpression(method, binOp.left(), cb, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityExpressionAsJpaExpression(method, binOp.right(), cb, root, join, capturedValues, helper);
            return ArithmeticExpressionBuilder.INSTANCE.buildArithmeticOperation(method, binOp.operator(), cb, left, right);
        }

        // Logical operations
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateBiEntityPredicate(method, binOp.left(), cb, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityPredicate(method, binOp.right(), cb, root, join, capturedValues, helper);
            return helper.combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Null check
        if (isNullCheckPattern(binOp)) {
            return generateBiEntityNullCheckPredicate(method, binOp, cb, root, join, capturedValues, helper);
        }

        // Default: comparison operation
        ResultHandle left = generateBiEntityExpressionAsJpaExpression(method, binOp.left(), cb, root, join, capturedValues, helper);
        ResultHandle right = generateBiEntityExpressionAsJpaExpression(method, binOp.right(), cb, root, join, capturedValues, helper);
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(method, binOp.operator(), cb, left, right);
    }

    private ResultHandle generateBiEntityNullCheckPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        LambdaExpression nonNullExpr = helper.extractNonNullExpression(binOp);
        ResultHandle expression = generateBiEntityExpressionAsJpaExpression(method, nonNullExpr, cb, root, join, capturedValues, helper);
        return helper.generateNullCheckPredicate(method, cb, expression, binOp.operator());
    }

    private ResultHandle generateBiEntityUnaryOperation(
            MethodCreator method,
            LambdaExpression.UnaryOp unOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        ResultHandle operand = generateBiEntityPredicate(method, unOp.operand(), cb, root, join, capturedValues, helper);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(CB_NOT, cb, operand);
        };
    }

    private ResultHandle generateBiEntityBinaryOperationWithSubqueries(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Check for logical operations (AND, OR) - need to recurse with subquery support
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateBiEntityPredicateWithSubqueries(method, binOp.left(), cb, query, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityPredicateWithSubqueries(method, binOp.right(), cb, query, root, join, capturedValues, helper);
            return helper.combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Only scalar subqueries can be used in comparisons. EXISTS/IN are predicates, not expressions.
        boolean leftHasScalarSubquery = containsScalarSubquery(binOp.left());
        boolean rightHasScalarSubquery = containsScalarSubquery(binOp.right());

        if (leftHasScalarSubquery || rightHasScalarSubquery) {
            // Generate expressions with subquery support
            ResultHandle left = generateBiEntityExpressionWithSubqueries(method, binOp.left(), cb, query, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityExpressionWithSubqueries(method, binOp.right(), cb, query, root, join, capturedValues, helper);
            return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(method, binOp.operator(), cb, left, right);
        }

        // This handles bytecode patterns where boolean short-circuit creates comparison to constant.
        boolean leftHasSubquery = containsSubquery(binOp.left());
        boolean rightHasSubquery = containsSubquery(binOp.right());

        if (leftHasSubquery || rightHasSubquery) {
            // If comparing a subquery predicate to a boolean constant, simplify
            if (isSubqueryBooleanComparison(binOp)) {
                // Return just the subquery predicate (EXISTS == true → EXISTS)
                LambdaExpression subqueryExpr = leftHasSubquery ? binOp.left() : binOp.right();
                LambdaExpression constantExpr = leftHasSubquery ? binOp.right() : binOp.left();
                ResultHandle predicate = generateBiEntityPredicateWithSubqueries(
                        method, subqueryExpr, cb, query, root, join, capturedValues, helper);

                // If comparing to false or using NE with true, negate the result
                if (isNegatedSubqueryComparison(binOp.operator(), constantExpr)) {
                    return method.invokeInterfaceMethod(CB_NOT, cb, predicate);
                }
                return predicate;
            }

            // For other patterns with subqueries, recursively process with subquery support
            ResultHandle left = generateBiEntityPredicateWithSubqueries(
                    method, binOp.left(), cb, query, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityPredicateWithSubqueries(
                    method, binOp.right(), cb, query, root, join, capturedValues, helper);
            return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(method, binOp.operator(), cb, left, right);
        }

        // No subqueries - delegate to standard bi-entity binary operation
        return generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues, helper);
    }

    private ResultHandle generateBiEntityUnaryOperationWithSubqueries(
            MethodCreator method,
            LambdaExpression.UnaryOp unOp,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        ResultHandle operand = generateBiEntityPredicateWithSubqueries(method, unOp.operand(), cb, query, root, join, capturedValues, helper);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(CB_NOT, cb, operand);
        };
    }

    /** Generates bi-entity method call via MethodCallHandlerChain. */
    private ResultHandle generateBiEntityMethodCall(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Create bi-entity context for polymorphic dispatch
        BiEntityMethodCallContext context = new BiEntityMethodCallContext(
                method,
                methodCall,
                cb,
                root,
                join,
                capturedValues,
                ExpressionBuilderRegistry.createDefault(),
                helper);

        // Delegate to unified handler chain
        GenerationResult result = MethodCallHandlerChain.defaultInstance().handleMethodCall(context);

        return switch (result) {
            case GenerationResult.Success(var handle) -> handle;
            case GenerationResult.Unsupported(var methodName, var reason) ->
                throw new UnsupportedExpressionException(methodCall, "bi-entity method call: " + methodName + " - " + reason);
        };
    }

    private ResultHandle generateBiEntityConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        ResultHandle resultClassHandle = helper.loadDtoClass(method, constructorCall.className());

        return buildConstructorExpression(method, cb, resultClassHandle, constructorCall.arguments(),
                arg -> generateBiEntityExpressionAsJpaExpression(method, arg, cb, root, join, capturedValues, helper));
    }
}
