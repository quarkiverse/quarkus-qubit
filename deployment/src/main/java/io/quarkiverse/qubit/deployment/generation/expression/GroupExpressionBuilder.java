package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CONSTRUCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_TUPLE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CLASS_FOR_NAME;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_NOT;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregation;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupKeyReference;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

/**
 * Builds JPA Criteria API expressions for GROUP BY queries.
 *
 * <p>Iteration 7: Extracted from CriteriaExpressionGenerator to reduce class size
 * and improve maintainability (addresses ARCH-001).
 *
 * <p>Handles group expressions including:
 * <ul>
 *   <li>HAVING predicates: {@code g -> g.count() > 5}</li>
 *   <li>Group select projections: {@code g -> new DeptStats(g.key(), g.count())}</li>
 *   <li>Group aggregation functions: COUNT, AVG, SUM, MIN, MAX</li>
 *   <li>Group key references: {@code g.key()}</li>
 * </ul>
 *
 * internal generate* methods return ResultHandle, but callers
 * typically know the result is non-null in their specific context.
 *
 * @see io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator
 */
public class GroupExpressionBuilder implements ExpressionBuilder {

    /**
     * Delegate builders for specialized operations.
     */
    private final ArithmeticExpressionBuilder arithmeticBuilder = new ArithmeticExpressionBuilder();
    private final ComparisonExpressionBuilder comparisonBuilder = new ComparisonExpressionBuilder();

    /**
     * Generates JPA Predicate from group lambda expression AST (HAVING clause).
     * <p>
     * Used for group query HAVING predicates like {@code g -> g.count() > 5}.
     *
     * @param method the method creator for bytecode generation
     * @param expression the group lambda expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Predicate handle for the HAVING clause, or null if expression is null or unhandled
     */
    public ResultHandle generateGroupPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(method, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateGroupUnaryOperation(method, unOp, cb, root, groupKeyExpr, capturedValues, helper);

            case GroupAggregation groupAgg ->
                // Aggregation used as a boolean predicate (rare, but possible)
                generateGroupAggregationExpression(method, groupAgg, cb, root, capturedValues, helper);

            case GroupKeyReference ignored ->
                // Key reference as boolean (if key is boolean type)
                groupKeyExpr;

            default -> null;
        };
    }

    /**
     * Generates JPA Expression from group lambda expression AST (GROUP BY SELECT).
     * <p>
     * Used for group query select projections like {@code g -> new DeptStats(g.key(), g.count())}.
     *
     * @param method the method creator for bytecode generation
     * @param expression the group lambda expression AST, or null
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Expression handle for the SELECT clause, or null if expression is null or unhandled
     */
    public ResultHandle generateGroupSelectExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case GroupKeyReference ignored ->
                // g.key() -> use the pre-computed grouping key expression
                groupKeyExpr;

            case GroupAggregation groupAgg ->
                // g.count(), g.avg(), etc. -> generate aggregation expression
                generateGroupAggregationExpression(method, groupAgg, cb, root, capturedValues, helper);

            case LambdaExpression.ArrayCreation arrayCreation ->
                // Iteration 7: Object[] projection using cb.tuple()
                generateGroupArrayCreation(method, arrayCreation, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.ConstructorCall constructorCall ->
                // DTO constructor with group elements
                generateGroupConstructorCall(method, constructorCall, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.FieldAccess field ->
                // Field access in group context (from nested lambda in aggregation)
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
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

            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(method, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            default -> null;
        };
    }

    /**
     * Generates JPA Expression for group ORDER BY clause.
     * <p>
     * Used for sorting group query results by group key or aggregation values.
     *
     * @param method the method creator for bytecode generation
     * @param expression the sort key expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return the JPA Expression handle for ORDER BY
     */
    public ResultHandle generateGroupSortExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Delegate to generateGroupSelectExpression since they handle the same types
        return generateGroupSelectExpression(method, expression, cb, root, groupKeyExpr, capturedValues, helper);
    }

    /**
     * Generates JPA multiselect array for Object[] projections in group context.
     *
     * @param method the method creator for bytecode generation
     * @param arrayCreation the array creation expression from the lambda AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @param helper the helper for common expression generation
     * @return an array of JPA Selection handles for multiselect
     */
    public ResultHandle generateGroupArraySelections(
            MethodCreator method,
            LambdaExpression.ArrayCreation arrayCreation,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        int elementCount = arrayCreation.elements().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, elementCount);

        for (int i = 0; i < elementCount; i++) {
            LambdaExpression element = arrayCreation.elements().get(i);
            ResultHandle elementSelection = generateGroupSelectExpression(
                    method, element, cb, root, groupKeyExpr, capturedValues, helper);
            method.writeArrayValue(selectionsArray, i, elementSelection);
        }

        return selectionsArray;
    }

    // ========== Private Helper Methods ==========

    /**
     * Generates JPA aggregation expression for GroupAggregation AST node.
     */
    private ResultHandle generateGroupAggregationExpression(
            MethodCreator method,
            GroupAggregation groupAgg,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        GroupAggregationType aggType = groupAgg.aggregationType();

        // Handle COUNT specially - it operates on the root, not a field
        if (aggType == GroupAggregationType.COUNT) {
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "count", Expression.class, Expression.class),
                    cb, root);
        }

        // For all other aggregations, we need to extract the field expression
        LambdaExpression fieldExpr = groupAgg.fieldExpression();
        ResultHandle fieldPath = helper.generateExpressionAsJpaExpression(method, fieldExpr, cb, root, capturedValues);

        if (fieldPath == null) {
            // Fallback: if field expression is null, use root
            fieldPath = root;
        }

        // CS-008: Added default case for future-proofing
        return switch (aggType) {
            case COUNT_DISTINCT -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "countDistinct", Expression.class, Expression.class),
                    cb, fieldPath);
            case AVG -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "avg", Expression.class, Expression.class),
                    cb, fieldPath);
            case SUM_INTEGER -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "sum", Expression.class, Expression.class),
                    cb, fieldPath);
            case SUM_LONG -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "sumAsLong", Expression.class, Expression.class),
                    cb, fieldPath);
            case SUM_DOUBLE -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "sumAsDouble", Expression.class, Expression.class),
                    cb, fieldPath);
            case MIN -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "min", Expression.class, Expression.class),
                    cb, fieldPath);
            case MAX -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "max", Expression.class, Expression.class),
                    cb, fieldPath);
            case COUNT -> throw new IllegalStateException("COUNT should be handled above");
            default -> throw new IllegalStateException("Unexpected group aggregation type: " + aggType);
        };
    }

    /**
     * Generates group binary operation (comparison, logical, arithmetic).
     */
    private ResultHandle generateGroupBinaryOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Logical operations (AND, OR)
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateGroupPredicate(method, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            ResultHandle right = generateGroupPredicate(method, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
            return helper.combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Arithmetic operations
        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateGroupSelectExpression(method, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            ResultHandle right = generateGroupSelectExpression(method, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
            return arithmeticBuilder.buildArithmeticOperation(method, binOp.operator(), cb, left, right);
        }

        // Comparison operations (most common in HAVING)
        ResultHandle left = generateGroupSelectExpression(method, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
        ResultHandle right = generateGroupSelectExpression(method, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
        return comparisonBuilder.buildComparisonOperation(method, binOp.operator(), cb, left, right);
    }

    /**
     * Generates group unary NOT operation.
     */
    private ResultHandle generateGroupUnaryOperation(
            MethodCreator method,
            LambdaExpression.UnaryOp unOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        ResultHandle operand = generateGroupPredicate(method, unOp.operand(), cb, root, groupKeyExpr, capturedValues, helper);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /**
     * Generates JPA tuple for Object[] projections in group context.
     */
    private ResultHandle generateGroupArrayCreation(
            MethodCreator method,
            LambdaExpression.ArrayCreation arrayCreation,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Generate Selection array for all elements
        ResultHandle selectionsArray = generateGroupArraySelections(
                method, arrayCreation, cb, root, groupKeyExpr, capturedValues, helper);

        // Use cb.tuple() to create a compound selection (PERF-001: cached descriptor)
        return method.invokeInterfaceMethod(CB_TUPLE, cb, selectionsArray);
    }

    /**
     * Generates JPA constructor expression for DTO projections in group context.
     */
    private ResultHandle generateGroupConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Get the DTO class name
        String className = constructorCall.className();
        String fqClassName = className.replace('/', '.');

        // Load the class at runtime (PERF-001: cached descriptor)
        ResultHandle classNameHandle = method.load(fqClassName);
        ResultHandle resultClassHandle = method.invokeStaticMethod(CLASS_FOR_NAME, classNameHandle);

        // Generate JPA expressions for each constructor argument
        int argCount = constructorCall.arguments().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            ResultHandle argExpression = generateGroupSelectExpression(method, arg, cb, root, groupKeyExpr, capturedValues, helper);
            method.writeArrayValue(selectionsArray, i, argExpression);
        }

        // Call cb.construct(resultClass, selections...) (PERF-001: cached descriptor)
        return method.invokeInterfaceMethod(CB_CONSTRUCT, cb, resultClassHandle, selectionsArray);
    }

    /**
     * Creates MethodDescriptor for method.
     */
    private static MethodDescriptor methodDescriptor(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        return MethodDescriptor.ofMethod(clazz, methodName, returnType, params);
    }
}
