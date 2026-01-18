package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.expectedAndOrOperator;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isBooleanType;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.BinaryOperationCategory;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsScalarSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNegatedSubqueryComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isSubqueryBooleanComparison;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_AND;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CONCAT_EXPR_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CONSTRUCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_EQUAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_FALSE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_MEMBER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_NOT_MEMBER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_TRUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_LITERAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT_EQUAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_OR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CLASS_FOR_NAME;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EXPRESSION_IN_COLLECTION;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.PATH_GET;

import java.util.Objects;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkiverse.qubit.deployment.generation.methodcall.GenerationResult;
import io.quarkiverse.qubit.deployment.generation.methodcall.MethodCallHandlerChain;

import static io.quarkiverse.qubit.deployment.generation.GizmoHelper.createElementArray;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

/**
 * Converts lambda expression AST into JPA Criteria API bytecode using Gizmo.
 *
 * <p>Implements ExpressionGeneratorHelper to provide common generation methods
 * to specialized builders (BiEntityExpressionBuilder, GroupExpressionBuilder).
 *
 * many internal generate* methods return ResultHandle, but callers
 * typically know the result is non-null in their specific context.
 */
public class CriteriaExpressionGenerator implements ExpressionGeneratorHelper {

    private final ExpressionBuilderRegistry builderRegistry;
    private final MethodCallHandlerChain methodCallHandlerChain;

    /** Creates a generator with the default expression builder registry. */
    public CriteriaExpressionGenerator() {
        this(ExpressionBuilderRegistry.createDefault(), MethodCallHandlerChain.defaultInstance());
    }

    /** Creates a generator with custom registry for testing. */
    public CriteriaExpressionGenerator(ExpressionBuilderRegistry builderRegistry) {
        this(builderRegistry, MethodCallHandlerChain.defaultInstance());
    }

    /** Creates a generator with full dependency injection for testing. */
    public CriteriaExpressionGenerator(ExpressionBuilderRegistry builderRegistry,
            MethodCallHandlerChain methodCallHandlerChain) {
        this.builderRegistry = Objects.requireNonNull(builderRegistry,
                "builderRegistry cannot be null");
        this.methodCallHandlerChain = Objects.requireNonNull(methodCallHandlerChain,
                "methodCallHandlerChain cannot be null");
    }

    /** Generates JPA Predicate from lambda expression AST. */
    public ResultHandle generatePredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperation(method, binOp, cb, root, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateUnaryOperation(method, unOp, cb, root, capturedValues);

            case LambdaExpression.FieldAccess field -> {
                ResultHandle path = generateFieldAccess(method, field, root);
                yield wrapBooleanAsPredicateIfNeeded(method, cb, path, field.fieldType());
            }

            case PathExpression pathExpr -> {
                ResultHandle path = generatePathExpression(method, pathExpr, root);
                yield wrapBooleanAsPredicateIfNeeded(method, cb, path, pathExpr.resultType());
            }

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(method, methodCall, cb, root, capturedValues);

            case InExpression inExpr ->
                generateInPredicate(method, inExpr, cb, root, capturedValues);

            case MemberOfExpression memberOfExpr ->
                generateMemberOfPredicate(method, memberOfExpr, cb, root, capturedValues);

            default -> throw new UnsupportedExpressionException(expression, "predicate generation");
        };
    }

    /**
     * Generates JPA Predicate with subquery support (ExistsSubquery, InSubquery, ScalarSubquery).
     * Accepts CriteriaQuery for creating subqueries.
     */
    public ResultHandle generatePredicateWithSubqueries(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            // Handle subquery expressions first
            case ExistsSubquery existsSubquery ->
                builderRegistry.subqueryBuilder().buildExistsSubquery(method, existsSubquery, cb, query, root, capturedValues);

            case InSubquery inSubquery ->
                builderRegistry.subqueryBuilder().buildInSubquery(method, inSubquery, cb, query, root, capturedValues);

            // Handle binary operations that may contain subqueries
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperationWithSubqueries(method, binOp, cb, query, root, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateUnaryOperationWithSubqueries(method, unOp, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generatePredicate(method, expression, cb, root, capturedValues);
        };
    }

    /** Generates JPA Expression with ScalarSubquery support. */
    public ResultHandle generateExpressionWithSubqueries(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                builderRegistry.subqueryBuilder().buildScalarSubquery(method, scalarSubquery, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generateExpressionAsJpaExpression(method, expression, cb, root, capturedValues);
        };
    }

    /**
     * Generates binary operation with subquery support.
     * Handles comparisons that may contain ScalarSubquery on either side.
     */
    private ResultHandle generateBinaryOperationWithSubqueries(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle capturedValues) {

        // Check for logical operations (AND, OR)
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generatePredicateWithSubqueries(method, binOp.left(), cb, query, root, capturedValues);
            ResultHandle right = generatePredicateWithSubqueries(method, binOp.right(), cb, query, root, capturedValues);
            return combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Only scalar subqueries can be used in comparisons. EXISTS/IN are predicates, not expressions.
        boolean leftHasScalarSubquery = containsScalarSubquery(binOp.left());
        boolean rightHasScalarSubquery = containsScalarSubquery(binOp.right());

        if (leftHasScalarSubquery || rightHasScalarSubquery) {
            // Generate expressions with subquery support
            ResultHandle left = generateExpressionWithSubqueries(method, binOp.left(), cb, query, root, capturedValues);
            ResultHandle right = generateExpressionWithSubqueries(method, binOp.right(), cb, query, root, capturedValues);
            return generateComparisonOperation(method, binOp.operator(), cb, left, right);
        }

        // Pattern: ExistsSubquery == true → just return the ExistsSubquery predicate
        // This handles bytecode patterns where boolean short-circuit creates comparison to constant.
        boolean leftHasSubquery = containsSubquery(binOp.left());
        boolean rightHasSubquery = containsSubquery(binOp.right());

        if (leftHasSubquery || rightHasSubquery) {
            // If comparing a subquery predicate to a boolean constant, simplify
            if (isSubqueryBooleanComparison(binOp)) {
                // Return just the subquery predicate (EXISTS == true → EXISTS)
                LambdaExpression subqueryExpr = leftHasSubquery ? binOp.left() : binOp.right();
                LambdaExpression constantExpr = leftHasSubquery ? binOp.right() : binOp.left();
                ResultHandle predicate = generatePredicateWithSubqueries(method, subqueryExpr, cb, query, root, capturedValues);

                // If comparing to false or using NE with true, negate the result
                if (isNegatedSubqueryComparison(binOp.operator(), constantExpr)) {
                    return method.invokeInterfaceMethod(CB_NOT, cb, predicate);
                }
                return predicate;
            }

            // For other patterns with subqueries, recursively process with subquery support
            ResultHandle left = generatePredicateWithSubqueries(method, binOp.left(), cb, query, root, capturedValues);
            ResultHandle right = generatePredicateWithSubqueries(method, binOp.right(), cb, query, root, capturedValues);
            return generateComparisonOperation(method, binOp.operator(), cb, left, right);
        }

        // No subqueries - delegate to original method
        return generateBinaryOperation(method, binOp, cb, root, capturedValues);
    }

    /**
     * Generates unary operation with subquery support.
     * Handles NOT operations that may wrap subquery predicates.
     */
    private ResultHandle generateUnaryOperationWithSubqueries(
            MethodCreator method,
            LambdaExpression.UnaryOp unOp,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle operand = generatePredicateWithSubqueries(method, unOp.operand(), cb, query, root, capturedValues);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(
                    CB_NOT, cb, operand);
        };
    }

    /**
     * Generates raw value from lambda expression.
     * <p>
     * Refactored for Java 21: Uses pattern matching switch for cleaner type dispatch.
     */
    public ResultHandle generateExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant ->
                generateConstant(method, constant);

            case LambdaExpression.CapturedVariable capturedVar ->
                loadCapturedValue(method, capturedVar, capturedValues);

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(method, methodCall, cb, root, capturedValues);

            case LambdaExpression.CorrelatedVariable correlated ->
                generateCorrelatedFieldExpression(method, correlated, root);

            default -> throw new UnsupportedExpressionException(expression, "raw expression generation");
        };
    }

    /**
     * Generates JPA Expression from lambda expression.
     * Includes Parameter handling for identity sort functions.
     * <p>
     * Refactored for Java 21: Uses pattern matching switch for cleaner type dispatch.
     */
    public ResultHandle generateExpressionAsJpaExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant -> {
                ResultHandle constantValue = generateConstant(method, constant);
                yield wrapAsLiteral(method, cb, constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                loadAndWrapCapturedValue(method, cb, capturedVar, capturedValues);

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(method, methodCall, cb, root, capturedValues);

            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperation(method, binOp, cb, root, capturedValues);

            case LambdaExpression.ConstructorCall constructorCall ->
                generateConstructorCall(method, constructorCall, cb, root, capturedValues);

            case LambdaExpression.Parameter ignored ->
                // Parameter expressions occur in identity sort functions like (String s) -> s
                // These cannot be directly converted to JPA expressions - return null to signal
                // to caller that special handling is needed
                null;

            case InExpression inExpr ->
                // InExpression is a predicate but Predicate extends Expression<Boolean>
                // so we can return it as a JPA expression
                generateInPredicate(method, inExpr, cb, root, capturedValues);

            case MemberOfExpression memberOfExpr ->
                // MemberOfExpression is also a predicate that can be used as expression
                generateMemberOfPredicate(method, memberOfExpr, cb, root, capturedValues);

            case LambdaExpression.UnaryOp unaryOp ->
                // UnaryOp (NOT) is a predicate that can be used as expression
                // This occurs when IFEQ creates NOT(InExpression) for short-circuit evaluation
                generateUnaryOperation(method, unaryOp, cb, root, capturedValues);

            case LambdaExpression.CorrelatedVariable correlated ->
                generateCorrelatedFieldExpression(method, correlated, root);

            default -> throw new UnsupportedExpressionException(expression);
        };
    }

    /**
     * Generates comparison, logical, arithmetic, or string concatenation operation.
     */
    public ResultHandle generateBinaryOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        BinaryOperationCategory category = BinaryOperationCategory.categorize(binOp, this::isStringConcatenation);

        return switch (category) {
            case STRING_CONCATENATION -> {
                ResultHandle left = generateExpressionAsJpaExpression(method, binOp.left(), cb, root, capturedValues);
                ResultHandle right = generateExpressionAsJpaExpression(method, binOp.right(), cb, root, capturedValues);
                yield generateStringConcatenation(method, cb, left, right);
            }

            case ARITHMETIC -> {
                ResultHandle left = generateExpressionAsJpaExpression(method, binOp.left(), cb, root, capturedValues);
                ResultHandle right = generateExpressionAsJpaExpression(method, binOp.right(), cb, root, capturedValues);
                yield generateArithmeticOperation(method, binOp.operator(), cb, left, right);
            }

            case LOGICAL -> {
                ResultHandle left = generatePredicate(method, binOp.left(), cb, root, capturedValues);
                ResultHandle right = generatePredicate(method, binOp.right(), cb, root, capturedValues);
                yield combinePredicates(method, cb, left, right, binOp.operator());
            }

            case NULL_CHECK -> generateNullCheckPredicate(method, binOp, cb, root, capturedValues);

            case BOOLEAN_FIELD_CONSTANT -> generateBooleanFieldConstantPredicate(method, binOp, cb, root, capturedValues);

            case BOOLEAN_FIELD_CAPTURED_VARIABLE -> generateBooleanFieldCapturedVariablePredicate(method, binOp, cb, root, capturedValues);

            case COMPARE_TO_EQUALITY -> {
                // compareTo equality can return null if constant is not 0/false/true
                ResultHandle result = generateCompareToEqualityPredicate(method, binOp, cb, root, capturedValues);
                yield result != null ? result : generateDefaultComparison(method, binOp, cb, root, capturedValues);
            }

            case COMPARISON -> generateDefaultComparison(method, binOp, cb, root, capturedValues);
        };
    }

    /**
     * Generates default comparison operation (EQ, NE, LT, LE, GT, GE).
     * Extracted for reuse in fallthrough case from COMPARE_TO_EQUALITY.
     */
    private ResultHandle generateDefaultComparison(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle left = generateExpressionAsJpaExpression(method, binOp.left(), cb, root, capturedValues);
        ResultHandle right = generateExpressionAsJpaExpression(method, binOp.right(), cb, root, capturedValues);
        return generateComparisonOperation(method, binOp.operator(), cb, left, right);
    }

    /** Generates null check predicate (IS NULL or IS NOT NULL). */
    private ResultHandle generateNullCheckPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        LambdaExpression nonNullExpr = extractNonNullExpression(binOp);
        ResultHandle expression = generateExpression(method, nonNullExpr, cb, root, capturedValues);
        return generateNullCheckPredicate(method, cb, expression, binOp.operator());
    }

    /** Generates boolean field comparison with constant 0 or 1. */
    private ResultHandle generateBooleanFieldConstantPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        LambdaExpression.Constant constant = (LambdaExpression.Constant) binOp.right();
        ResultHandle field = generateExpression(method, binOp.left(), cb, root, capturedValues);

        boolean compareToTrue = constant.value().equals(1);
        boolean isEqualOp = binOp.operator() == EQ;
        boolean useIsTrue = (isEqualOp && compareToTrue) || (!isEqualOp && !compareToTrue);

        if (useIsTrue) {
            return method.invokeInterfaceMethod(CB_IS_TRUE, cb, field);
        } else {
            return method.invokeInterfaceMethod(CB_IS_FALSE, cb, field);
        }
    }

    /** Generates boolean field comparison with captured variable. */
    private ResultHandle generateBooleanFieldCapturedVariablePredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle fieldExpr = generateExpressionAsJpaExpression(method, binOp.left(), cb, root, capturedValues);
        ResultHandle capturedExpr = generateExpressionAsJpaExpression(method, binOp.right(), cb, root, capturedValues);

        if (binOp.operator() == EQ) {
            return method.invokeInterfaceMethod(CB_EQUAL, cb, fieldExpr, capturedExpr);
        } else {
            return method.invokeInterfaceMethod(CB_NOT_EQUAL, cb, fieldExpr, capturedExpr);
        }
    }

    /** Generates compareTo equality pattern predicate. Returns null if not applicable. */
    private ResultHandle generateCompareToEqualityPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) binOp.left();
        LambdaExpression.Constant constant = (LambdaExpression.Constant) binOp.right();

        boolean isEqualityCheck = constant.value().equals(0) ||
                                  (constant.value() instanceof Boolean && constant.value().equals(false));
        boolean isInequalityCheck = (constant.value() instanceof Boolean && constant.value().equals(true));

        if (isEqualityCheck || isInequalityCheck) {
            ResultHandle field = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);
            ResultHandle argument = generateExpression(method, methodCall.arguments().get(0), cb, root, capturedValues);

            if (isEqualityCheck) {
                return method.invokeInterfaceMethod(CB_EQUAL, cb, field, argument);
            } else {
                return method.invokeInterfaceMethod(CB_NOT_EQUAL, cb, field, argument);
            }
        }
        return null;
    }

    /** Generates unary NOT operation. */
    public ResultHandle generateUnaryOperation(
            MethodCreator method,
            LambdaExpression.UnaryOp unOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle operand = generatePredicate(method, unOp.operand(), cb, root, capturedValues);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(CB_NOT, cb, operand);
        };
    }

    /** Generates JPA field access expression. */
    @Override
    public ResultHandle generateFieldAccess(
            MethodCreator method,
            LambdaExpression.FieldAccess field,
            ResultHandle root) {

        ResultHandle fieldName = method.load(field.fieldName());
        return method.invokeInterfaceMethod(PATH_GET, root, fieldName);
    }

    /**
     * Generates JPA path expression for relationship navigation.
     * Converts PathExpression to chained Path.get() calls.
     */
    @Override
    public ResultHandle generatePathExpression(
            MethodCreator method,
            PathExpression pathExpr,
            ResultHandle root) {

        ResultHandle currentPath = root;

        for (PathSegment segment : pathExpr.segments()) {
            ResultHandle fieldName = method.load(segment.fieldName());
            currentPath = method.invokeInterfaceMethod(
                    PATH_GET,
                    currentPath, fieldName);
        }

        return currentPath;
    }

    /**
     * Generates constant value bytecode.
     * <p>
     * Delegates to {@link GizmoHelper#loadConstant(MethodCreator, Object)} for the
     * actual bytecode generation.
     */
    @Override
    public ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        return GizmoHelper.loadConstant(method, constant.value());
    }

    /**
     * Generates JPA expression for method call using handler chain.
     * @throws UnsupportedExpressionException if the method call is not supported
     */
    public ResultHandle generateMethodCall(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        GenerationResult result = methodCallHandlerChain.handleMethodCall(
                method, methodCall, cb, root, capturedValues, builderRegistry, this);

        return switch (result) {
            case GenerationResult.Success(var handle) -> handle;
            case GenerationResult.Unsupported(var methodName, var reason) ->
                throw new UnsupportedExpressionException(methodCall, "method call: " + methodName + " - " + reason);
        };
    }

    /** Generates JPA cb.construct() for DTO projections. */
    private ResultHandle generateConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        // Load the DTO class at runtime
        ResultHandle resultClassHandle = loadDtoClass(method, constructorCall.className());

        // Generate JPA expressions for each constructor argument
        int argCount = constructorCall.arguments().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            ResultHandle argExpression = generateExpressionAsJpaExpression(method, arg, cb, root, capturedValues);

            // Null check: Parameter expressions return null and are not allowed in constructor arguments
            if (argExpression == null) {
                throw UnsupportedExpressionException.inConstructorArgument(arg, i, constructorCall.className());
            }

            method.writeArrayValue(selectionsArray, i, argExpression);
        }

        // Call cb.construct(resultClass, selections...)
        return method.invokeInterfaceMethod(CB_CONSTRUCT, cb, resultClassHandle, selectionsArray);
    }

    // ========== Collection Operations (IN, MEMBER OF) ==========

    /** Generates JPA IN predicate: cities.contains(p.city) -> root.get("city").in(cities). */
    private ResultHandle generateInPredicate(
            MethodCreator method,
            InExpression inExpr,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        // Generate the field expression (e.g., root.get("city"))
        ResultHandle fieldExpr = generateExpressionAsJpaExpression(method, inExpr.field(), cb, root, capturedValues);

        // Null check: Parameter expressions return null and are not allowed in IN predicates
        if (fieldExpr == null) {
            throw new UnsupportedExpressionException(inExpr.field(), "IN predicate field");
        }

        // Generate the collection expression (e.g., capturedValues[0])
        ResultHandle collectionExpr = generateExpression(method, inExpr.collection(), cb, root, capturedValues);

        // Call fieldExpr.in(collection) - Expression.in(Collection)
        // This creates a Predicate that checks if field value is in the collection
        ResultHandle inPredicate = method.invokeInterfaceMethod(EXPRESSION_IN_COLLECTION, fieldExpr, collectionExpr);

        // If negated (NOT IN), wrap with cb.not()
        if (inExpr.negated()) {
            return method.invokeInterfaceMethod(
                    CB_NOT,
                    cb, inPredicate);
        }

        return inPredicate;
    }

    /** Generates JPA MEMBER OF: p.roles.contains("admin") -> cb.isMember("admin", root.get("roles")). */
    private ResultHandle generateMemberOfPredicate(
            MethodCreator method,
            MemberOfExpression memberOfExpr,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        // Generate the value expression (e.g., "admin" constant or captured variable)
        ResultHandle valueExpr = generateExpression(method, memberOfExpr.value(), cb, root, capturedValues);

        // Generate the collection field expression (e.g., root.get("roles"))
        ResultHandle collectionFieldExpr = generateExpressionAsJpaExpression(
                method, memberOfExpr.collectionField(), cb, root, capturedValues);

        MethodDescriptor mdMember = memberOfExpr.negated() ? CB_IS_NOT_MEMBER : CB_IS_MEMBER;
        return method.invokeInterfaceMethod(mdMember, cb, valueExpr, collectionFieldExpr);
    }

    /** Generates arithmetic operations. Delegates to ArithmeticExpressionBuilder. */
    @Override
    public ResultHandle generateArithmeticOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        return builderRegistry.arithmeticBuilder().buildArithmeticOperation(method, operator, cb, left, right);
    }

    /**
     * Detects if binary operation is string concatenation.
     * Returns true if operator is ADD and at least one operand is a String type.
     */
    @Override
    public boolean isStringConcatenation(LambdaExpression.BinaryOp binOp) {
        if (binOp.operator() != LambdaExpression.BinaryOp.Operator.ADD) {
            return false;
        }

        return isStringType(binOp.left()) || isStringType(binOp.right());
    }

    /**
     * Checks if expression evaluates to String type.
     */
    private boolean isStringType(LambdaExpression expr) {
        return switch (expr) {
            case LambdaExpression.FieldAccess field -> field.fieldType() == String.class;
            case LambdaExpression.Constant constant -> constant.value() instanceof String;
            case LambdaExpression.CapturedVariable capturedVar -> capturedVar.type() == String.class;
            case LambdaExpression.BinaryOp binOp -> isStringConcatenation(binOp);  // Recursive: concatenation of concatenations
            case null, default -> false;
        };
    }

    /**
     * Generates string concatenation using JPA CriteriaBuilder.concat().
     * Handles chaining of multiple concat operations.
     */
    @Override
    public ResultHandle generateStringConcatenation(
            MethodCreator method,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        return method.invokeInterfaceMethod(CB_CONCAT_EXPR_EXPR, cb, left, right);
    }

    /** Generates comparison operations. Delegates to ComparisonExpressionBuilder. */
    @Override
    public ResultHandle generateComparisonOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        return builderRegistry.comparisonBuilder().buildComparisonOperation(method, operator, cb, left, right);
    }

    /** Combines two predicates with AND or OR. */
    @Override
    public ResultHandle combinePredicates(
            MethodCreator method,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right,
            LambdaExpression.BinaryOp.Operator operator) {

        ResultHandle predicateArray = createElementArray(method, Predicate.class, left, right);

        MethodDescriptor combineMethod = switch (operator) {
            case AND -> CB_AND;
            case OR -> CB_OR;
            default -> throw new IllegalArgumentException(expectedAndOrOperator(operator));
        };

        return method.invokeInterfaceMethod(combineMethod, cb, predicateArray);
    }

    /** Wraps value as literal Expression. */
    @Override
    public ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value) {
        return method.invokeInterfaceMethod(CB_LITERAL, cb, value);
    }

    // ========== Captured Variable Utilities ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultHandle loadCapturedValue(MethodCreator method,
            LambdaExpression.CapturedVariable capturedVar, ResultHandle capturedValues) {
        ResultHandle index = method.load(capturedVar.index());
        ResultHandle value = method.readArrayValue(capturedValues, index);
        Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
        return method.checkCast(value, targetType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultHandle loadAndWrapCapturedValue(MethodCreator method, ResultHandle cb,
            LambdaExpression.CapturedVariable capturedVar, ResultHandle capturedValues) {
        ResultHandle castedValue = loadCapturedValue(method, capturedVar, capturedValues);
        return wrapAsLiteral(method, cb, castedValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultHandle loadDtoClass(MethodCreator method, String internalClassName) {
        // Convert internal class name to fully qualified class name (replace / with .)
        String fqClassName = internalClassName.replace('/', '.');
        // Load the class at runtime using Class.forName()
        ResultHandle classNameHandle = method.load(fqClassName);
        return method.invokeStaticMethod(CLASS_FOR_NAME, classNameHandle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultHandle wrapBooleanAsPredicateIfNeeded(MethodCreator method, ResultHandle cb,
            ResultHandle path, Class<?> type) {
        if (isBooleanType(type)) {
            return method.invokeInterfaceMethod(CB_IS_TRUE, cb, path);
        }
        return path;
    }

    // ========== Bi-Entity Expressions (Join Queries) ==========

    /** Generates JPA Predicate from bi-entity lambda (for join queries). */
    public ResultHandle generateBiEntityPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityPredicate(method, expression, cb, root, join, capturedValues, this);
    }

    /** Generates bi-entity JPA Predicate with subquery support. */
    public ResultHandle generateBiEntityPredicateWithSubqueries(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityPredicateWithSubqueries(method, expression, cb, query, root, join, capturedValues, this);
    }

    /** Generates JPA Expression from bi-entity lambda. */
    public ResultHandle generateBiEntityExpressionAsJpaExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues, this);
    }

    // ========== Bi-Entity Projections ==========

    /** Generates JPA Selection from bi-entity projection. */
    public ResultHandle generateBiEntityProjection(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityProjection(method, expression, cb, root, join, capturedValues, this);
    }

    // ========== Group Expressions (GROUP BY) ==========

    /** Generates JPA Predicate for HAVING clause in GROUP BY queries. */
    public ResultHandle generateGroupPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupPredicate(method, expression, cb, root, groupKeyExpr, capturedValues, this);
    }

    /** Generates JPA Expression for GROUP BY SELECT clause. */
    public ResultHandle generateGroupSelectExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupSelectExpression(method, expression, cb, root, groupKeyExpr, capturedValues, this);
    }

    /** Generates JPA Expression for group ORDER BY clause. */
    public ResultHandle generateGroupSortExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupSortExpression(method, expression, cb, root, groupKeyExpr, capturedValues, this);
    }

    /** Generates JPA multiselect array for Object[] projections in group context. */
    public ResultHandle generateGroupArraySelections(
            MethodCreator method,
            LambdaExpression.ArrayCreation arrayCreation,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupArraySelections(method, arrayCreation, cb, root, groupKeyExpr, capturedValues, this);
    }
}
