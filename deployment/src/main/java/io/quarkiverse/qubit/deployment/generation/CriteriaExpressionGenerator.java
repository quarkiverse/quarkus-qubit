package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_ADD;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_CONTAINS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_DIVIDE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_ENDS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_MULTIPLY;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_OF;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_STARTS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_SUBSTRING;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_SUBTRACT;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_AND;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_EQUAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_FALSE;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_MEMBER;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_NOT_MEMBER;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_NOT_NULL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_NULL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_IS_TRUE;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_LITERAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_NOT;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_NOT_EQUAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_OR;
import static io.quarkiverse.qubit.runtime.QubitConstants.PATH_GET;
import static io.quarkiverse.qubit.runtime.QubitConstants.PREFIX_GET;
import static io.quarkiverse.qubit.runtime.QubitConstants.PREFIX_IS;
import static io.quarkiverse.qubit.runtime.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isBooleanFieldCapturedVariableComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isBooleanFieldConstantComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isCompareToEqualityPattern;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNullCheckPattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import jakarta.persistence.criteria.CompoundSelection;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
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

    private static final Set<String> BIG_DECIMAL_ARITHMETIC_METHOD_NAMES = Set.of(
        METHOD_ADD, METHOD_SUBTRACT, METHOD_MULTIPLY, METHOD_DIVIDE
    );
    private static final Set<String> STRING_PATTERN_METHOD_NAMES = Set.of(
        METHOD_STARTS_WITH, METHOD_ENDS_WITH, METHOD_CONTAINS
    );

    /**
     * Registry holding all expression builders for dependency injection (ARCH-004).
     */
    private final ExpressionBuilderRegistry builderRegistry;

    /**
     * Creates a generator with the default expression builder registry.
     *
     * <p>This is the standard constructor for production use.
     */
    public CriteriaExpressionGenerator() {
        this(ExpressionBuilderRegistry.createDefault());
    }

    /**
     * Creates a generator with a custom expression builder registry.
     *
     * <p>This constructor enables testability by allowing injection of mock
     * or custom builder implementations.
     *
     * @param builderRegistry the registry containing expression builders
     * @throws NullPointerException if builderRegistry is null
     */
    public CriteriaExpressionGenerator(ExpressionBuilderRegistry builderRegistry) {
        this.builderRegistry = Objects.requireNonNull(builderRegistry,
                "builderRegistry cannot be null");
    }

    /** Creates MethodDescriptor for method. */
    private static MethodDescriptor methodDescriptor(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        return MethodDescriptor.ofMethod(clazz, methodName, returnType, params);
    }

    /** Creates MethodDescriptor for constructor. */
    private static MethodDescriptor constructorDescriptor(Class<?> clazz, Class<?>... params) {
        return MethodDescriptor.ofConstructor(clazz, params);
    }

    /**
     * Generates JPA Predicate from lambda expression AST.
     * <p>
     * Refactored for Java 21: Uses pattern matching switch for cleaner type dispatch.
     */
    public ResultHandle generatePredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperation(method, binOp, cb, root, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateUnaryOperation(method, unOp, cb, root, capturedValues);

            case LambdaExpression.FieldAccess field -> {
                ResultHandle path = generateFieldAccess(method, field, root);
                if (isBooleanType(field.fieldType())) {
                    yield method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case PathExpression pathExpr -> {
                ResultHandle path = generatePathExpression(method, pathExpr, root);
                if (isBooleanType(pathExpr.resultType())) {
                    yield method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(method, methodCall, cb, root, capturedValues);

            case InExpression inExpr ->
                generateInPredicate(method, inExpr, cb, root, capturedValues);

            case MemberOfExpression memberOfExpr ->
                generateMemberOfPredicate(method, memberOfExpr, cb, root, capturedValues);

            default -> null;
        };
    }

    /**
     * Generates JPA Predicate from lambda expression AST with subquery support.
     * <p>
     * Iteration 8: Overloaded version that accepts CriteriaQuery for creating subqueries.
     * Use this method when the predicate may contain subquery expressions
     * (ExistsSubquery, InSubquery, or comparisons with ScalarSubquery).
     *
     * @param method the method creator for bytecode generation
     * @param expression the lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle (needed for subquery creation)
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle
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

        // Java 21 pattern matching switch for type dispatch
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

    /**
     * Generates JPA Expression from lambda expression with subquery support.
     * <p>
     * Iteration 8: Overloaded version that handles ScalarSubquery expressions
     * which need the CriteriaQuery to create the subquery.
     *
     * @param method the method creator for bytecode generation
     * @param expression the lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle (needed for subquery creation)
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Expression handle
     */
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

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                builderRegistry.subqueryBuilder().buildScalarSubquery(method, scalarSubquery, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generateExpressionAsJpaExpression(method, expression, cb, root, capturedValues);
        };
    }

    /**
     * Generates binary operation with subquery support.
     * <p>
     * Iteration 8: Handles comparisons that may contain ScalarSubquery on either side.
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

        // Check if either side contains a subquery
        boolean leftHasSubquery = containsSubquery(binOp.left());
        boolean rightHasSubquery = containsSubquery(binOp.right());

        if (leftHasSubquery || rightHasSubquery) {
            // Generate expressions with subquery support
            ResultHandle left = generateExpressionWithSubqueries(method, binOp.left(), cb, query, root, capturedValues);
            ResultHandle right = generateExpressionWithSubqueries(method, binOp.right(), cb, query, root, capturedValues);
            return generateComparisonOperation(method, binOp.operator(), cb, left, right);
        }

        // No subqueries - delegate to original method
        return generateBinaryOperation(method, binOp, cb, root, capturedValues);
    }

    /**
     * Generates unary operation with subquery support.
     * <p>
     * Iteration 8: Handles NOT operations that may wrap subquery predicates.
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
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /**
     * Checks if an expression contains a subquery.
     * <p>
     * Iteration 8: Used to determine if subquery-aware methods should be used.
     */
    private boolean containsSubquery(LambdaExpression expr) {
        // Java 21 pattern matching switch for type dispatch
        // Using separate cases because multi-pattern with `_` requires Java 21 preview
        return switch (expr) {
            case ScalarSubquery ignored1 -> true;
            case ExistsSubquery ignored2 -> true;
            case InSubquery ignored3 -> true;
            case LambdaExpression.BinaryOp binOp -> containsSubquery(binOp.left()) || containsSubquery(binOp.right());
            case LambdaExpression.UnaryOp unOp -> containsSubquery(unOp.operand());
            case null, default -> false;
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

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant ->
                generateConstant(method, constant);

            case LambdaExpression.CapturedVariable capturedVar -> {
                ResultHandle index = method.load(capturedVar.index());
                ResultHandle value = method.readArrayValue(capturedValues, index);
                Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
                yield method.checkCast(value, targetType);
            }

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(method, methodCall, cb, root, capturedValues);

            default -> null;
        };
    }

    /**
     * Generates JPA Expression from lambda expression.
     * Phase 3: Added Parameter handling for identity sort functions.
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

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant -> {
                ResultHandle constantValue = generateConstant(method, constant);
                yield wrapAsLiteral(method, cb, constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar -> {
                ResultHandle index = method.load(capturedVar.index());
                ResultHandle value = method.readArrayValue(capturedValues, index);
                Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
                ResultHandle castedValue = method.checkCast(value, targetType);
                yield wrapAsLiteral(method, cb, castedValue);
            }

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(method, methodCall, cb, root, capturedValues);

            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperation(method, binOp, cb, root, capturedValues);

            case LambdaExpression.ConstructorCall constructorCall ->
                generateConstructorCall(method, constructorCall, cb, root, capturedValues);

            case LambdaExpression.Parameter ignored ->
                // Phase 3: Parameter expressions occur in identity sort functions like (String s) -> s
                // These cannot be directly converted to JPA expressions - return null to signal
                // to caller that special handling is needed
                null;

            case InExpression inExpr ->
                // Iteration 5: InExpression is a predicate but Predicate extends Expression<Boolean>
                // so we can return it as a JPA expression
                generateInPredicate(method, inExpr, cb, root, capturedValues);

            case MemberOfExpression memberOfExpr ->
                // Iteration 5: MemberOfExpression is also a predicate that can be used as expression
                generateMemberOfPredicate(method, memberOfExpr, cb, root, capturedValues);

            case LambdaExpression.UnaryOp unaryOp ->
                // Iteration 5: UnaryOp (NOT) is a predicate that can be used as expression
                // This occurs when IFEQ creates NOT(InExpression) for short-circuit evaluation
                generateUnaryOperation(method, unaryOp, cb, root, capturedValues);

            default -> null;
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

        // Check for string concatenation BEFORE arithmetic (both use ADD operator)
        if (isStringConcatenation(binOp)) {
            ResultHandle left = generateExpressionAsJpaExpression(method, binOp.left(), cb, root, capturedValues);
            ResultHandle right = generateExpressionAsJpaExpression(method, binOp.right(), cb, root, capturedValues);

            return generateStringConcatenation(method, cb, left, right);
        }

        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateExpressionAsJpaExpression(method, binOp.left(), cb, root, capturedValues);
            ResultHandle right = generateExpressionAsJpaExpression(method, binOp.right(), cb, root, capturedValues);

            return generateArithmeticOperation(method, binOp.operator(), cb, left, right);
        }

        if (isLogicalOperation(binOp)) {
            ResultHandle left = generatePredicate(method, binOp.left(), cb, root, capturedValues);
            ResultHandle right = generatePredicate(method, binOp.right(), cb, root, capturedValues);

            return combinePredicates(method, cb, left, right, binOp.operator());
        }

        if (isNullCheckPattern(binOp)) {
            return generateNullCheckPredicate(method, binOp, cb, root, capturedValues);
        }

        if (isBooleanFieldConstantComparison(binOp)) {
            return generateBooleanFieldConstantPredicate(method, binOp, cb, root, capturedValues);
        }

        if (isBooleanFieldCapturedVariableComparison(binOp)) {
            return generateBooleanFieldCapturedVariablePredicate(method, binOp, cb, root, capturedValues);
        }

        if (isCompareToEqualityPattern(binOp)) {
            ResultHandle result = generateCompareToEqualityPredicate(method, binOp, cb, root, capturedValues);
            if (result != null) {
                return result;
            }
        }

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

        boolean leftIsNull = binOp.left() instanceof LambdaExpression.NullLiteral;
        LambdaExpression nonNullExpr = leftIsNull ? binOp.right() : binOp.left();
        ResultHandle expression = generateExpression(method, nonNullExpr, cb, root, capturedValues);

        if (binOp.operator() == EQ) {
            return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_NULL, Predicate.class, Expression.class), cb, expression);
        } else {
            return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_NOT_NULL, Predicate.class, Expression.class), cb, expression);
        }
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
            return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, field);
        } else {
            return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_FALSE, Predicate.class, Expression.class), cb, field);
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
            return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_EQUAL, Predicate.class, Expression.class, Object.class), cb, fieldExpr, capturedExpr);
        } else {
            return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_NOT_EQUAL, Predicate.class, Expression.class, Object.class), cb, fieldExpr, capturedExpr);
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
                return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_EQUAL, Predicate.class, Expression.class, Object.class), cb, field, argument);
            } else {
                return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_NOT_EQUAL, Predicate.class, Expression.class, Object.class), cb, field, argument);
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
            case NOT -> method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /** Generates JPA field access expression. */
    @Override
    public ResultHandle generateFieldAccess(
            MethodCreator method,
            LambdaExpression.FieldAccess field,
            ResultHandle root) {

        ResultHandle fieldName = method.load(field.fieldName());
        return method.invokeInterfaceMethod(methodDescriptor(Path.class, PATH_GET, Path.class, String.class), root, fieldName);
    }

    /**
     * Generates JPA path expression for relationship navigation.
     * <p>
     * Converts a PathExpression like {@code p.owner.firstName} to chained
     * {@code Path.get()} calls: {@code root.get("owner").get("firstName")}.
     * <p>
     * This method handles both simple field chains and relationship navigation.
     * For relationships requiring JPA joins, the path segments are marked with
     * their relationship type, though this initial implementation uses simple
     * chained get() calls which work for @ManyToOne and @OneToOne navigation.
     * <p>
     * Example:
     * <pre>
     * // Lambda: phone -> phone.owner.firstName
     * // PathExpression: [owner (MANY_TO_ONE), firstName (FIELD)]
     * // Generated JPA: root.get("owner").get("firstName")
     * </pre>
     *
     * @param method the method creator for bytecode generation
     * @param pathExpr the path expression from the lambda AST
     * @param root the root entity handle (From or Path)
     * @return the final Path handle after all navigation steps
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
                    methodDescriptor(Path.class, PATH_GET, Path.class, String.class),
                    currentPath, fieldName);
        }

        return currentPath;
    }

    /**
     * Generates constant value bytecode.
     * <p>
     * Refactored for Java 21: Uses pattern matching switch for cleaner type dispatch.
     */
    @Override
    public ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        Object value = constant.value();

        // Java 21 pattern matching switch for type dispatch
        return switch (value) {
            case String s -> method.load(s);
            case Integer i -> method.load(i);
            case Long l -> method.load(l);
            case Boolean b -> method.load(b);
            case Double d -> method.load(d);
            case Float f -> method.load(f);

            case BigDecimal bd -> {
                ResultHandle bdString = method.load(bd.toString());
                yield method.newInstance(constructorDescriptor(BigDecimal.class, String.class), bdString);
            }

            case LocalDate ld -> {
                ResultHandle year = method.load(ld.getYear());
                ResultHandle month = method.load(ld.getMonthValue());
                ResultHandle day = method.load(ld.getDayOfMonth());
                yield method.invokeStaticMethod(methodDescriptor(LocalDate.class, METHOD_OF, LocalDate.class, int.class, int.class, int.class), year, month, day);
            }

            case LocalDateTime ldt -> {
                ResultHandle year = method.load(ldt.getYear());
                ResultHandle month = method.load(ldt.getMonthValue());
                ResultHandle day = method.load(ldt.getDayOfMonth());
                ResultHandle hour = method.load(ldt.getHour());
                ResultHandle minute = method.load(ldt.getMinute());
                yield method.invokeStaticMethod(methodDescriptor(LocalDateTime.class, METHOD_OF, LocalDateTime.class, int.class, int.class, int.class, int.class, int.class), year, month, day, hour, minute);
            }

            case LocalTime lt -> {
                ResultHandle hour = method.load(lt.getHour());
                ResultHandle minute = method.load(lt.getMinute());
                yield method.invokeStaticMethod(methodDescriptor(LocalTime.class, METHOD_OF, LocalTime.class, int.class, int.class), hour, minute);
            }

            default -> method.loadNull();
        };
    }

    /**
     * Generates JPA expression for method call.
     *
     * @param method the method creator for bytecode generation
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA expression, or null if the method is not recognized
     */
    public ResultHandle generateMethodCall(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle temporalResult = generateTemporalAccessorFunction(method, methodCall, cb, root, capturedValues);
        if (temporalResult != null) {
            return temporalResult;
        }

        ResultHandle temporalComparisonResult = generateTemporalComparison(method, methodCall, cb, root, capturedValues);
        if (temporalComparisonResult != null) {
            return temporalComparisonResult;
        }

        ResultHandle stringTransformResult = generateStringTransformation(method, methodCall, cb, root, capturedValues);
        if (stringTransformResult != null) {
            return stringTransformResult;
        }

        ResultHandle bigDecimalArithmeticResult = generateBigDecimalArithmetic(method, methodCall, cb, root, capturedValues);
        if (bigDecimalArithmeticResult != null) {
            return bigDecimalArithmeticResult;
        }

        ResultHandle stringLikeResult = generateStringLikePattern(method, methodCall, cb, root, capturedValues);
        if (stringLikeResult != null) {
            return stringLikeResult;
        }

        ResultHandle substringResult = generateStringSubstring(method, methodCall, cb, root, capturedValues);
        if (substringResult != null) {
            return substringResult;
        }

        ResultHandle stringUtilityResult = generateStringUtilityMethod(method, methodCall, cb, root, capturedValues);
        if (stringUtilityResult != null) {
            return stringUtilityResult;
        }

        if (methodCall.methodName().startsWith(PREFIX_GET) || methodCall.methodName().startsWith(PREFIX_IS)) {
            String fieldName = extractFieldName(methodCall.methodName());
            return generateFieldAccess(
                    method,
                    new LambdaExpression.FieldAccess(fieldName, methodCall.returnType()),
                    root);
        }

        return null;
    }

    /**
     * Generates JPA constructor expression for DTO projections.
     * <p>
     * Converts {@code new PersonDTO(p.firstName, p.age)} to
     * {@code cb.construct(PersonDTO.class, root.get("firstName"), root.get("age"))}.
     * <p>
     * Example bytecode generation:
     * <pre>
     * Class dtoClass = Class.forName("io.quarkiverse.qubit.it.dto.PersonNameDTO");
     * Selection[] selections = new Selection[2];
     * selections[0] = root.get("firstName");
     * selections[1] = root.get("age");
     * return cb.construct(dtoClass, selections);
     * </pre>
     *
     * @param method the method creator for bytecode generation
     * @param constructorCall the constructor call expression from the lambda AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA CompoundSelection handle representing the constructor expression
     */
    private ResultHandle generateConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        // Get the DTO class name (e.g., "io/quarkiverse/qubit/it/dto/PersonNameDTO")
        String className = constructorCall.className();

        // Convert internal class name to fully qualified class name (replace / with .)
        String fqClassName = className.replace('/', '.');

        // Load the class at runtime using Class.forName()
        ResultHandle classNameHandle = method.load(fqClassName);
        ResultHandle resultClassHandle = method.invokeStaticMethod(
                MethodDescriptor.ofMethod(Class.class, "forName", Class.class, String.class),
                classNameHandle);

        // Generate JPA expressions for each constructor argument
        int argCount = constructorCall.arguments().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            ResultHandle argExpression = generateExpressionAsJpaExpression(method, arg, cb, root, capturedValues);
            method.writeArrayValue(selectionsArray, i, argExpression);
        }

        // Call cb.construct(resultClass, selections...)
        MethodDescriptor constructMethod = MethodDescriptor.ofMethod(
                CriteriaBuilder.class,
                "construct",
                CompoundSelection.class,
                Class.class,
                Selection[].class);

        return method.invokeInterfaceMethod(constructMethod, cb, resultClassHandle, selectionsArray);
    }

    // =============================================================================================
    // COLLECTION OPERATIONS (Iteration 5: IN and MEMBER OF)
    // =============================================================================================

    /**
     * Generates JPA IN predicate for collection membership testing.
     * <p>
     * Converts {@code cities.contains(p.city)} to JPA {@code root.get("city").in(cities)}.
     * <p>
     * Example:
     * <pre>
     * // Lambda: cities.contains(p.city)
     * // InExpression(field=FieldAccess("city"), collection=CapturedVariable(0), negated=false)
     *
     * // Generated JPA:
     * Expression&lt;String&gt; cityPath = root.get("city");
     * Predicate inPred = cityPath.in(capturedValues[0]); // capturedValues[0] is the cities collection
     * </pre>
     *
     * @param method the method creator for bytecode generation
     * @param inExpr the IN expression from the lambda AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle representing the IN clause
     */
    private ResultHandle generateInPredicate(
            MethodCreator method,
            InExpression inExpr,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        // Generate the field expression (e.g., root.get("city"))
        ResultHandle fieldExpr = generateExpressionAsJpaExpression(method, inExpr.field(), cb, root, capturedValues);

        // Generate the collection expression (e.g., capturedValues[0])
        ResultHandle collectionExpr = generateExpression(method, inExpr.collection(), cb, root, capturedValues);

        // Call fieldExpr.in(collection) - Expression.in(Collection)
        // This creates a Predicate that checks if field value is in the collection
        MethodDescriptor inMethod = MethodDescriptor.ofMethod(
                Expression.class,
                "in",
                Predicate.class,
                Collection.class);

        ResultHandle inPredicate = method.invokeInterfaceMethod(inMethod, fieldExpr, collectionExpr);

        // If negated (NOT IN), wrap with cb.not()
        if (inExpr.negated()) {
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class),
                    cb, inPredicate);
        }

        return inPredicate;
    }

    /**
     * Generates JPA MEMBER OF predicate for collection field membership.
     * <p>
     * Converts {@code p.roles.contains("admin")} to JPA {@code cb.isMember("admin", root.get("roles"))}.
     * <p>
     * Example:
     * <pre>
     * // Lambda: p.roles.contains("admin")
     * // MemberOfExpression(value=Constant("admin"), collectionField=FieldAccess("roles"), negated=false)
     *
     * // Generated JPA:
     * Expression&lt;Collection&lt;String&gt;&gt; rolesPath = root.get("roles");
     * Predicate memberPred = cb.isMember("admin", rolesPath);
     * </pre>
     *
     * @param method the method creator for bytecode generation
     * @param memberOfExpr the MEMBER OF expression from the lambda AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle representing the MEMBER OF clause
     */
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

        // Determine which method to call based on negation
        String memberMethodName = memberOfExpr.negated() ? CB_IS_NOT_MEMBER : CB_IS_MEMBER;

        // Call cb.isMember(value, collection) or cb.isNotMember(value, collection)
        MethodDescriptor memberMethod = MethodDescriptor.ofMethod(
                CriteriaBuilder.class,
                memberMethodName,
                Predicate.class,
                Object.class,
                Expression.class);

        return method.invokeInterfaceMethod(memberMethod, cb, valueExpr, collectionFieldExpr);
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
        // Java 21 pattern matching switch for type dispatch
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

        // cb.concat(left, right)
        MethodDescriptor concatMethod = MethodDescriptor.ofMethod(
                CriteriaBuilder.class,
                "concat",
                Expression.class,
                Expression.class, Expression.class);

        return method.invokeInterfaceMethod(concatMethod, cb, left, right);
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

        ResultHandle predicateArray = method.newArray(Predicate.class, 2);
        method.writeArrayValue(predicateArray, 0, left);
        method.writeArrayValue(predicateArray, 1, right);

        return switch (operator) {
            case AND -> method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_AND, Predicate.class, Predicate[].class), cb, predicateArray);
            case OR -> method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_OR, Predicate.class, Predicate[].class), cb, predicateArray);
            default -> throw new IllegalArgumentException("Expected AND or OR operator, got: " + operator);
        };
    }

    /** Generates temporal accessor functions. Returns null if not a temporal accessor. */
    private ResultHandle generateTemporalAccessorFunction(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);
        return builderRegistry.temporalBuilder().buildTemporalAccessorFunction(method, methodCall, cb, fieldExpression);
    }

    /** Generates String transformations. Returns null if not a transformation. */
    private ResultHandle generateStringTransformation(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);
        return builderRegistry.stringBuilder().buildStringTransformation(method, methodCall, cb, fieldExpression);
    }

    /** Generates temporal comparisons. Returns null if not a temporal comparison. */
    private ResultHandle generateTemporalComparison(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (!TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodCall.methodName())) {
            return null;
        }

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);

        if (methodCall.arguments().isEmpty()) {
            return null;
        }

        ResultHandle argument = generateExpression(method, methodCall.arguments().get(0), cb, root, capturedValues);

        return builderRegistry.temporalBuilder().buildTemporalComparison(method, methodCall, cb, fieldExpression, argument);
    }

    /** Generates BigDecimal arithmetic. Returns null if not a BigDecimal method. */
    private ResultHandle generateBigDecimalArithmetic(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (!BIG_DECIMAL_ARITHMETIC_METHOD_NAMES.contains(methodCall.methodName())) {
            return null;
        }

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);

        if (methodCall.arguments().isEmpty()) {
            return null;
        }

        ResultHandle argument = generateExpressionAsJpaExpression(method, methodCall.arguments().get(0), cb, root, capturedValues);

        return builderRegistry.bigDecimalBuilder().buildBigDecimalArithmetic(method, methodCall, cb, fieldExpression, argument, builderRegistry.arithmeticBuilder());
    }

    /** Generates String LIKE patterns. Returns null if not a pattern method. */
    private ResultHandle generateStringLikePattern(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (!STRING_PATTERN_METHOD_NAMES.contains(methodCall.methodName())) {
            return null;
        }

        ResultHandle fieldExpression = generateExpression(method, methodCall.target(), cb, root, capturedValues);

        if (methodCall.arguments().isEmpty()) {
            return null;
        }

        ResultHandle argument = generateExpression(method, methodCall.arguments().get(0), cb, root, capturedValues);

        return builderRegistry.stringBuilder().buildStringPattern(method, methodCall, cb, fieldExpression, argument);
    }

    /** Generates String substring. Returns null if not substring. */
    private ResultHandle generateStringSubstring(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        if (!methodCall.methodName().equals(METHOD_SUBSTRING)) {
            return null;
        }

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);

        // Generate argument expressions
        List<ResultHandle> arguments = new ArrayList<>();
        for (LambdaExpression arg : methodCall.arguments()) {
            arguments.add(generateExpressionAsJpaExpression(method, arg, cb, root, capturedValues));
        }

        return builderRegistry.stringBuilder().buildStringSubstring(method, methodCall, cb, fieldExpression, arguments);
    }

    /** Generates String utility methods. Returns null if not recognized. */
    private ResultHandle generateStringUtilityMethod(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);

        ResultHandle argument = null;
        if (methodCall.methodName().equals(METHOD_EQUALS) && !methodCall.arguments().isEmpty()) {
            argument = generateExpression(method, methodCall.arguments().get(0), cb, root, capturedValues);
        }

        return builderRegistry.stringBuilder().buildStringUtility(method, methodCall, cb, fieldExpression, argument);
    }

    /** Wraps value as literal Expression. */
    @Override
    public ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value) {
        return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_LITERAL, Expression.class, Object.class), cb, value);
    }

    /** Checks if type is boolean (primitive or wrapper). */
    private static boolean isBooleanType(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    /** Extracts field name from getter: "getAge" to "age". */
    private String extractFieldName(String methodName) {
        if (methodName.startsWith(PREFIX_GET) && methodName.length() > 3) {
            String fieldName = methodName.substring(3);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        if (methodName.startsWith(PREFIX_IS) && methodName.length() > 2) {
            String fieldName = methodName.substring(2);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return methodName;
    }

    // =============================================================================================
    // BI-ENTITY EXPRESSIONS (Iteration 6: Join Queries) - Delegated to BiEntityExpressionBuilder
    // =============================================================================================

    /**
     * Generates JPA Predicate from bi-entity lambda expression AST.
     * <p>
     * Delegates to BiEntityExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (FIRST entity in join)
     * @param join the joined entity handle (SECOND entity in join)
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle
     */
    public ResultHandle generateBiEntityPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityPredicate(method, expression, cb, root, join, capturedValues, this);
    }

    /**
     * Generates JPA Expression from bi-entity lambda expression AST.
     * <p>
     * Delegates to BiEntityExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (FIRST entity in join)
     * @param join the joined entity handle (SECOND entity in join)
     * @param capturedValues the captured variables array handle
     * @return the JPA Expression handle
     */
    public ResultHandle generateBiEntityExpressionAsJpaExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues, this);
    }

    // =============================================================================================
    // BI-ENTITY PROJECTIONS (Iteration 6.6: Join Projections) - Delegated to BiEntityExpressionBuilder
    // =============================================================================================

    /**
     * Generates JPA Selection from bi-entity projection expression AST.
     * <p>
     * Delegates to BiEntityExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param expression the bi-entity projection expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (source entity)
     * @param join the join handle (joined entity)
     * @param capturedValues the captured variables array handle
     * @return the JPA Selection handle representing the projection
     */
    public ResultHandle generateBiEntityProjection(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        return builderRegistry.biEntityBuilder().generateBiEntityProjection(method, expression, cb, root, join, capturedValues, this);
    }

    // =============================================================================================
    // GROUP EXPRESSIONS (Iteration 7: GROUP BY) - Delegated to GroupExpressionBuilder
    // =============================================================================================

    /**
     * Generates JPA Predicate from group lambda expression AST (HAVING clause).
     * <p>
     * Delegates to GroupExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param expression the group lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle for the HAVING clause
     */
    public ResultHandle generateGroupPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupPredicate(method, expression, cb, root, groupKeyExpr, capturedValues, this);
    }

    /**
     * Generates JPA Expression from group lambda expression AST (GROUP BY SELECT).
     * <p>
     * Delegates to GroupExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param expression the group lambda expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @return the JPA Expression handle for the SELECT clause
     */
    public ResultHandle generateGroupSelectExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupSelectExpression(method, expression, cb, root, groupKeyExpr, capturedValues, this);
    }

    /**
     * Generates JPA Expression for group ORDER BY clause.
     * <p>
     * Delegates to GroupExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param expression the sort key expression AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @return the JPA Expression handle for ORDER BY
     */
    public ResultHandle generateGroupSortExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        return builderRegistry.groupBuilder().generateGroupSortExpression(method, expression, cb, root, groupKeyExpr, capturedValues, this);
    }

    /**
     * Generates JPA multiselect array for Object[] projections in group context.
     * <p>
     * Delegates to GroupExpressionBuilder (ARCH-001 extraction).
     *
     * @param method the method creator for bytecode generation
     * @param arrayCreation the array creation expression from the lambda AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @return an array of JPA Selection handles for multiselect
     */
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
