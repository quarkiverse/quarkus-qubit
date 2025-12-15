package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.extractFieldName;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isBooleanType;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsScalarSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNegatedSubqueryComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNullCheckPattern;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isSubqueryBooleanComparison;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_EQUAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_NOT_NULL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_NULL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_TRUE;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_NOT;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.QubitConstants.PREFIX_GET;
import static io.quarkiverse.qubit.runtime.QubitConstants.PREFIX_IS;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_PATTERN_METHOD_NAMES;
import static io.quarkiverse.qubit.runtime.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CONSTRUCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CLASS_FOR_NAME;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

/**
 * Builds JPA Criteria API expressions for bi-entity (join) queries.
 * <p>
 * Handles expressions involving two entities (e.g., Person p, Phone ph) in join queries:
 * <ul>
 *   <li>Bi-entity predicates: {@code (p, ph) -> ph.type.equals("mobile")}</li>
 *   <li>Bi-entity expressions: {@code (p, ph) -> new DTO(p.firstName, ph.number)}</li>
 *   <li>Bi-entity projections for SELECT clauses</li>
 * </ul>
 *
 * internal generate* methods return ResultHandle, but callers
 * typically know the result is non-null in their specific context.
 *
 * @see io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator
 */
public class BiEntityExpressionBuilder implements ExpressionBuilder {

    /**
     * Delegate builders for specialized operations.
     */
    private final StringExpressionBuilder stringBuilder = new StringExpressionBuilder();
    private final TemporalExpressionBuilder temporalBuilder = new TemporalExpressionBuilder();
    private final ArithmeticExpressionBuilder arithmeticBuilder = new ArithmeticExpressionBuilder();
    private final ComparisonExpressionBuilder comparisonBuilder = new ComparisonExpressionBuilder();
    private final SubqueryExpressionBuilder subqueryBuilder = new SubqueryExpressionBuilder();

    /**
     * Generates JPA Predicate from bi-entity lambda expression AST.
     * <p>
     * Used for join query predicates like {@code (Person p, Phone ph) -> ph.type.equals("mobile")}.
     * Unlike single-entity predicates, bi-entity predicates need access to both the root entity
     * and the joined entity to correctly resolve field paths.
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity lambda expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (FIRST entity in join)
     * @param join the joined entity handle (SECOND entity in join)
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Predicate handle, or null if expression is null or unhandled
     */
    public ResultHandle generateBiEntityPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperation(method, unOp, cb, root, join, capturedValues, helper);

            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                ResultHandle path = helper.generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
                if (isBooleanType(biField.fieldType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                ResultHandle path = helper.generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
                if (isBooleanType(biPath.resultType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case LambdaExpression.FieldAccess field -> {
                // Single-entity field in bi-entity context (from root)
                ResultHandle path = helper.generateFieldAccess(method, field, root);
                if (isBooleanType(field.fieldType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case PathExpression pathExpr -> {
                // Single-entity path in bi-entity context (from root)
                ResultHandle path = helper.generatePathExpression(method, pathExpr, root);
                if (isBooleanType(pathExpr.resultType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(method, methodCall, cb, root, join, capturedValues, helper);

            default -> null;
        };
    }

    /**
     * Generates JPA Predicate from bi-entity lambda expression AST with subquery support.
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity lambda expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle (needed for subquery creation)
     * @param root the root entity handle (FIRST entity in join)
     * @param join the joined entity handle (SECOND entity in join)
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Predicate handle, or null if expression is null or unhandled
     */
    public ResultHandle generateBiEntityPredicateWithSubqueries(
            MethodCreator method,
            LambdaExpression expression,
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
                subqueryBuilder.buildExistsSubquery(method, existsSubquery, cb, query, root, capturedValues);

            case InSubquery inSubquery ->
                subqueryBuilder.buildInSubquery(method, inSubquery, cb, query, root, capturedValues);

            // Handle binary operations that may contain subqueries
            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperationWithSubqueries(method, binOp, cb, query, root, join, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperationWithSubqueries(method, unOp, cb, query, root, join, capturedValues, helper);

            // For non-subquery expressions, delegate to the original method
            default -> generateBiEntityPredicate(method, expression, cb, root, join, capturedValues, helper);
        };
    }

    /**
     * Generates JPA Expression from bi-entity lambda expression AST with subquery support.
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity lambda expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle (needed for subquery creation)
     * @param root the root entity handle (FIRST entity in join)
     * @param join the joined entity handle (SECOND entity in join)
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Expression handle, or null if expression is null or unhandled
     */
    public ResultHandle generateBiEntityExpressionWithSubqueries(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                subqueryBuilder.buildScalarSubquery(method, scalarSubquery, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues, helper);
        };
    }

    /**
     * Generates JPA Expression from bi-entity lambda expression AST.
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity lambda expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (FIRST entity in join)
     * @param join the joined entity handle (SECOND entity in join)
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Expression handle, or null if expression is null or unhandled
     */
    public ResultHandle generateBiEntityExpressionAsJpaExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                yield helper.generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                yield helper.generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
            }

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

            case LambdaExpression.CapturedVariable capturedVar -> {
                ResultHandle index = method.load(capturedVar.index());
                ResultHandle value = method.readArrayValue(capturedValues, index);
                Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
                ResultHandle castedValue = method.checkCast(value, targetType);
                yield helper.wrapAsLiteral(method, cb, castedValue);
            }

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(method, methodCall, cb, root, join, capturedValues, helper);

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues, helper);

            case LambdaExpression.CorrelatedVariable correlated -> {
                LambdaExpression fieldExpr = correlated.fieldExpression();
                // CorrelatedVariable always references the outer query's root entity
                yield switch (fieldExpr) {
                    case LambdaExpression.FieldAccess field ->
                        helper.generateFieldAccess(method, field, root);
                    case PathExpression path ->
                        helper.generatePathExpression(method, path, root);
                    default -> null;
                };
            }

            default -> null;
        };
    }

    /**
     * Generates JPA Selection from bi-entity projection expression AST.
     * <p>
     * Used for join query projections like {@code (p, ph) -> new PersonPhoneDTO(p.firstName, ph.number)}.
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity projection expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (source entity)
     * @param join the join handle (joined entity)
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Selection handle representing the projection, or null if expression is null
     */
    public ResultHandle generateBiEntityProjection(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.ConstructorCall constructorCall ->
                generateBiEntityConstructorCall(method, constructorCall, cb, root, join, capturedValues, helper);

            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                yield helper.generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                yield helper.generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
            }

            case LambdaExpression.FieldAccess field ->
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                helper.generatePathExpression(method, pathExpr, root);

            // For other expression types, delegate to generateBiEntityExpressionAsJpaExpression
            default -> generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues, helper);
        };
    }

    /**
     * Generates raw value from bi-entity expression.
     * Used for method arguments and captured variables.
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity expression, or null
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param join the join handle
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the generated expression, or null if expression is null or unhandled
     */
    public ResultHandle generateBiEntityExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                yield helper.generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                yield helper.generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
            }

            case LambdaExpression.FieldAccess field ->
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                helper.generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant ->
                helper.generateConstant(method, constant);

            case LambdaExpression.CapturedVariable capturedVar -> {
                ResultHandle index = method.load(capturedVar.index());
                ResultHandle value = method.readArrayValue(capturedValues, index);
                Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
                yield method.checkCast(value, targetType);
            }

            case LambdaExpression.CorrelatedVariable correlated -> {
                LambdaExpression fieldExpr = correlated.fieldExpression();
                // CorrelatedVariable always references the outer query's root entity
                yield switch (fieldExpr) {
                    case LambdaExpression.FieldAccess field ->
                        helper.generateFieldAccess(method, field, root);
                    case PathExpression path ->
                        helper.generatePathExpression(method, path, root);
                    default -> null;
                };
            }

            default -> null;
        };
    }

    // ========== Private Helper Methods ==========

    /**
     * Returns the appropriate JPA base handle (root or join) based on entity position.
     */
    private ResultHandle getBaseForEntityPosition(EntityPosition position, ResultHandle root, ResultHandle join) {
        return position == EntityPosition.FIRST ? root : join;
    }

    /**
     * Generates bi-entity binary operation (comparison, logical, arithmetic).
     */
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
            return arithmeticBuilder.buildArithmeticOperation(method, binOp.operator(), cb, left, right);
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
        return comparisonBuilder.buildComparisonOperation(method, binOp.operator(), cb, left, right);
    }

    /**
     * Generates bi-entity null check predicate.
     */
    private ResultHandle generateBiEntityNullCheckPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        boolean leftIsNull = binOp.left() instanceof LambdaExpression.NullLiteral;
        LambdaExpression nonNullExpr = leftIsNull ? binOp.right() : binOp.left();
        ResultHandle expression = generateBiEntityExpressionAsJpaExpression(method, nonNullExpr, cb, root, join, capturedValues, helper);

        if (binOp.operator() == LambdaExpression.BinaryOp.Operator.EQ) {
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_IS_NULL, Predicate.class, Expression.class), cb, expression);
        } else {
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_IS_NOT_NULL, Predicate.class, Expression.class), cb, expression);
        }
    }

    /**
     * Generates bi-entity unary NOT operation.
     */
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
            case NOT -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /**
     * Generates bi-entity binary operation with subquery support.
     */
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
            return comparisonBuilder.buildComparisonOperation(method, binOp.operator(), cb, left, right);
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
                    return method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class),
                            cb, predicate);
                }
                return predicate;
            }

            // For other patterns with subqueries, recursively process with subquery support
            ResultHandle left = generateBiEntityPredicateWithSubqueries(
                    method, binOp.left(), cb, query, root, join, capturedValues, helper);
            ResultHandle right = generateBiEntityPredicateWithSubqueries(
                    method, binOp.right(), cb, query, root, join, capturedValues, helper);
            return comparisonBuilder.buildComparisonOperation(method, binOp.operator(), cb, left, right);
        }

        // No subqueries - delegate to standard bi-entity binary operation
        return generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues, helper);
    }

    /**
     * Generates bi-entity unary operation with subquery support.
     */
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
            case NOT -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /**
     * Generates bi-entity method call (e.g., ph.type.equals("mobile")).
     *
     * @param method the method creator for bytecode generation
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param join the join handle
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the generated expression, or null if method is not recognized
     */
    private ResultHandle generateBiEntityMethodCall(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        String methodName = methodCall.methodName();

        // Handle equals() method
        if (METHOD_EQUALS.equals(methodName) && !methodCall.arguments().isEmpty()) {
            ResultHandle targetExpr = generateBiEntityExpressionAsJpaExpression(
                    method, methodCall.target(), cb, root, join, capturedValues, helper);
            ResultHandle argExpr = generateBiEntityExpression(
                    method, methodCall.arguments().get(0), cb, root, join, capturedValues, helper);
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_EQUAL, Predicate.class, Expression.class, Object.class),
                    cb, targetExpr, argExpr);
        }

        // Handle string pattern methods (startsWith, endsWith, contains)
        if (STRING_PATTERN_METHOD_NAMES.contains(methodName) && !methodCall.arguments().isEmpty()) {
            ResultHandle fieldExpr = generateBiEntityExpression(
                    method, methodCall.target(), cb, root, join, capturedValues, helper);
            ResultHandle argExpr = generateBiEntityExpression(
                    method, methodCall.arguments().get(0), cb, root, join, capturedValues, helper);
            return stringBuilder.buildStringPattern(method, methodCall, cb, fieldExpr, argExpr);
        }

        // Handle temporal comparison methods (isAfter, isBefore, isEqual)
        if (TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodName) && !methodCall.arguments().isEmpty()) {
            ResultHandle fieldExpr = generateBiEntityExpressionAsJpaExpression(
                    method, methodCall.target(), cb, root, join, capturedValues, helper);
            ResultHandle argExpr = generateBiEntityExpression(
                    method, methodCall.arguments().get(0), cb, root, join, capturedValues, helper);
            return temporalBuilder.buildTemporalComparison(method, methodCall, cb, fieldExpr, argExpr);
        }

        // Handle getter methods (getX, isX)
        if (methodName.startsWith(PREFIX_GET) || methodName.startsWith(PREFIX_IS)) {
            String fieldName = extractFieldName(methodName);
            ResultHandle targetExpr = generateBiEntityExpressionAsJpaExpression(
                    method, methodCall.target(), cb, root, join, capturedValues, helper);
            if (targetExpr == null) {
                targetExpr = root; // Default to root if target cannot be resolved
            }
            return helper.generateFieldAccess(method,
                    new LambdaExpression.FieldAccess(fieldName, methodCall.returnType()), targetExpr);
        }

        return null;
    }

    /**
     * Generates JPA ConstructorCall for bi-entity projections.
     */
    private ResultHandle generateBiEntityConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Get the DTO class name
        String className = constructorCall.className();
        String fqClassName = className.replace('/', '.');

        ResultHandle classNameHandle = method.load(fqClassName);
        ResultHandle resultClassHandle = method.invokeStaticMethod(CLASS_FOR_NAME, classNameHandle);

        // Generate JPA expressions for each constructor argument using bi-entity resolution
        int argCount = constructorCall.arguments().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            ResultHandle argExpression = generateBiEntityExpressionAsJpaExpression(
                    method, arg, cb, root, join, capturedValues, helper);
            method.writeArrayValue(selectionsArray, i, argExpression);
        }

        return method.invokeInterfaceMethod(CB_CONSTRUCT, cb, resultClassHandle, selectionsArray);
    }

    /**
     * Creates MethodDescriptor for method.
     */
    private static MethodDescriptor methodDescriptor(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        return MethodDescriptor.ofMethod(clazz, methodName, returnType, params);
    }
}
