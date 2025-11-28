package io.quarkus.qusaq.deployment.generation;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_ADD;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_CONTAINS;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_DIVIDE;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_ENDS_WITH;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_EQUALS;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_MULTIPLY;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_OF;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_STARTS_WITH;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_SUBSTRING;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_SUBTRACT;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_AND;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_EQUAL;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_FALSE;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_MEMBER;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_NOT_MEMBER;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_NOT_NULL;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_NULL;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_TRUE;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_LITERAL;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_NOT;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_NOT_EQUAL;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_OR;
import static io.quarkus.qusaq.runtime.QusaqConstants.PATH_GET;
import static io.quarkus.qusaq.runtime.QusaqConstants.PREFIX_GET;
import static io.quarkus.qusaq.runtime.QusaqConstants.PREFIX_IS;
import static io.quarkus.qusaq.runtime.QusaqConstants.TEMPORAL_COMPARISON_METHOD_NAMES;
import static io.quarkus.qusaq.deployment.analysis.PatternDetector.isBooleanFieldCapturedVariableComparison;
import static io.quarkus.qusaq.deployment.analysis.PatternDetector.isBooleanFieldConstantComparison;
import static io.quarkus.qusaq.deployment.analysis.PatternDetector.isCompareToEqualityPattern;
import static io.quarkus.qusaq.deployment.analysis.PatternDetector.isLogicalOperation;
import static io.quarkus.qusaq.deployment.analysis.PatternDetector.isNullCheckPattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.BiEntityFieldAccess;
import io.quarkus.qusaq.deployment.LambdaExpression.BiEntityPathExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.EntityPosition;
import io.quarkus.qusaq.deployment.LambdaExpression.ExistsSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.GroupAggregation;
import io.quarkus.qusaq.deployment.LambdaExpression.GroupAggregationType;
import io.quarkus.qusaq.deployment.LambdaExpression.GroupKeyReference;
import io.quarkus.qusaq.deployment.LambdaExpression.InExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.InSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.MemberOfExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.PathExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.PathSegment;
import io.quarkus.qusaq.deployment.LambdaExpression.ScalarSubquery;
import io.quarkus.qusaq.deployment.analysis.PatternDetector;
import io.quarkus.qusaq.deployment.generation.builders.ArithmeticExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.BigDecimalExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.ComparisonExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.StringExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.SubqueryExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.TemporalExpressionBuilder;
import io.quarkus.qusaq.deployment.util.TypeConverter;
import jakarta.persistence.criteria.CompoundSelection;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

/**
 * Converts lambda expression AST into JPA Criteria API bytecode using Gizmo.
 */
public class CriteriaExpressionGenerator {

    private static final Set<String> BIG_DECIMAL_ARITHMETIC_METHOD_NAMES = Set.of(
        METHOD_ADD, METHOD_SUBTRACT, METHOD_MULTIPLY, METHOD_DIVIDE
    );
    private static final Set<String> STRING_PATTERN_METHOD_NAMES = Set.of(
        METHOD_STARTS_WITH, METHOD_ENDS_WITH, METHOD_CONTAINS
    );

    /**
     * Delegate builders for specialized expression generation.
     */
    private final ArithmeticExpressionBuilder arithmeticBuilder = new ArithmeticExpressionBuilder();
    private final ComparisonExpressionBuilder comparisonBuilder = new ComparisonExpressionBuilder();
    private final StringExpressionBuilder stringBuilder = new StringExpressionBuilder();
    private final TemporalExpressionBuilder temporalBuilder = new TemporalExpressionBuilder();
    private final BigDecimalExpressionBuilder bigDecimalBuilder = new BigDecimalExpressionBuilder();
    private final SubqueryExpressionBuilder subqueryBuilder = new SubqueryExpressionBuilder();

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
                subqueryBuilder.buildExistsSubquery(method, existsSubquery, cb, query, root, capturedValues);

            case InSubquery inSubquery ->
                subqueryBuilder.buildInSubquery(method, inSubquery, cb, query, root, capturedValues);

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
                subqueryBuilder.buildScalarSubquery(method, scalarSubquery, cb, query, root, capturedValues);

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

    /** Generates compareTo equality pattern predicate. */
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
    public ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        Object value = constant.value();

        if (value == null) {
            return method.loadNull();
        }

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

    /** Generates JPA expression for method call. */
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
     * Class dtoClass = Class.forName("io.quarkus.qusaq.it.dto.PersonNameDTO");
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

        // Get the DTO class name (e.g., "io/quarkus/qusaq/it/dto/PersonNameDTO")
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
                java.util.Collection.class);

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
    private ResultHandle generateArithmeticOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        return arithmeticBuilder.buildArithmeticOperation(method, operator, cb, left, right);
    }

    /**
     * Detects if binary operation is string concatenation.
     * Returns true if operator is ADD and at least one operand is a String type.
     */
    private boolean isStringConcatenation(LambdaExpression.BinaryOp binOp) {
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
    private ResultHandle generateStringConcatenation(
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
    private ResultHandle generateComparisonOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        return comparisonBuilder.buildComparisonOperation(method, operator, cb, left, right);
    }

    /** Combines two predicates with AND or OR. */
    private ResultHandle combinePredicates(
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

    /** Generates temporal accessor functions. */
    private ResultHandle generateTemporalAccessorFunction(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);
        return temporalBuilder.buildTemporalAccessorFunction(method, methodCall, cb, fieldExpression);
    }

    /** Generates String transformations. */
    private ResultHandle generateStringTransformation(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        ResultHandle fieldExpression = generateExpressionAsJpaExpression(method, methodCall.target(), cb, root, capturedValues);
        return stringBuilder.buildStringTransformation(method, methodCall, cb, fieldExpression);
    }

    /** Generates temporal comparisons. */
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

        return temporalBuilder.buildTemporalComparison(method, methodCall, cb, fieldExpression, argument);
    }

    /** Generates BigDecimal arithmetic. */
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

        return bigDecimalBuilder.buildBigDecimalArithmetic(method, methodCall, cb, fieldExpression, argument, arithmeticBuilder);
    }

    /** Generates String LIKE patterns. */
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

        return stringBuilder.buildStringPattern(method, methodCall, cb, fieldExpression, argument);
    }

    /** Generates String substring with 0-based to 1-based index conversion. */
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

        return stringBuilder.buildStringSubstring(method, methodCall, cb, fieldExpression, arguments);
    }

    /** Generates String utility methods. */
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

        return stringBuilder.buildStringUtility(method, methodCall, cb, fieldExpression, argument);
    }

    /** Wraps value as literal Expression. */
    private ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value) {
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
    // BI-ENTITY EXPRESSIONS (Iteration 6: Join Queries)
    // =============================================================================================

    /**
     * Generates JPA Predicate from bi-entity lambda expression AST.
     * <p>
     * Used for join query predicates like {@code (Person p, Phone ph) -> ph.type.equals("mobile")}.
     * Unlike single-entity predicates, bi-entity predicates need access to both the root entity
     * and the joined entity to correctly resolve field paths.
     * <p>
     * Example:
     * <pre>
     * // Lambda: (Person p, Phone ph) -> ph.type.equals("mobile") && p.active
     * // Generated JPA:
     * //   - ph.type -> join.get("type")
     * //   - p.active -> root.get("active")
     * </pre>
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

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateBiEntityUnaryOperation(method, unOp, cb, root, join, capturedValues);

            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                ResultHandle path = generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
                if (isBooleanType(biField.fieldType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                ResultHandle path = generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
                if (isBooleanType(biPath.resultType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case LambdaExpression.FieldAccess field -> {
                // Single-entity field in bi-entity context (from root)
                ResultHandle path = generateFieldAccess(method, field, root);
                if (isBooleanType(field.fieldType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case PathExpression pathExpr -> {
                // Single-entity path in bi-entity context (from root)
                ResultHandle path = generatePathExpression(method, pathExpr, root);
                if (isBooleanType(pathExpr.resultType())) {
                    yield method.invokeInterfaceMethod(
                            methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
                }
                yield path;
            }

            case LambdaExpression.MethodCall methodCall ->
                generateBiEntityMethodCall(method, methodCall, cb, root, join, capturedValues);

            default -> null;
        };
    }

    /**
     * Generates JPA Expression from bi-entity lambda expression AST.
     * <p>
     * Returns the base expression handle (Path, etc.) for bi-entity expressions,
     * selecting root or join based on entity position.
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

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                yield generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                yield generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
            }

            case LambdaExpression.FieldAccess field ->
                // Single-entity field defaults to root
                generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                // Single-entity path defaults to root
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
                generateBiEntityMethodCall(method, methodCall, cb, root, join, capturedValues);

            case LambdaExpression.BinaryOp binOp ->
                generateBiEntityBinaryOperation(method, binOp, cb, root, join, capturedValues);

            default -> null;
        };
    }

    /**
     * Returns the appropriate JPA base handle (root or join) based on entity position.
     *
     * @param position the entity position (FIRST or SECOND)
     * @param root the root entity handle
     * @param join the joined entity handle
     * @return root for FIRST position, join for SECOND position
     */
    private ResultHandle getBaseForEntityPosition(
            EntityPosition position,
            ResultHandle root,
            ResultHandle join) {
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
            ResultHandle capturedValues) {

        // Check for string concatenation
        if (isStringConcatenation(binOp)) {
            ResultHandle left = generateBiEntityExpressionAsJpaExpression(method, binOp.left(), cb, root, join, capturedValues);
            ResultHandle right = generateBiEntityExpressionAsJpaExpression(method, binOp.right(), cb, root, join, capturedValues);
            return generateStringConcatenation(method, cb, left, right);
        }

        // Check for arithmetic
        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateBiEntityExpressionAsJpaExpression(method, binOp.left(), cb, root, join, capturedValues);
            ResultHandle right = generateBiEntityExpressionAsJpaExpression(method, binOp.right(), cb, root, join, capturedValues);
            return generateArithmeticOperation(method, binOp.operator(), cb, left, right);
        }

        // Logical operations
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateBiEntityPredicate(method, binOp.left(), cb, root, join, capturedValues);
            ResultHandle right = generateBiEntityPredicate(method, binOp.right(), cb, root, join, capturedValues);
            return combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Null check
        if (isNullCheckPattern(binOp)) {
            return generateBiEntityNullCheckPredicate(method, binOp, cb, root, join, capturedValues);
        }

        // Default: comparison operation
        ResultHandle left = generateBiEntityExpressionAsJpaExpression(method, binOp.left(), cb, root, join, capturedValues);
        ResultHandle right = generateBiEntityExpressionAsJpaExpression(method, binOp.right(), cb, root, join, capturedValues);
        return generateComparisonOperation(method, binOp.operator(), cb, left, right);
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
            ResultHandle capturedValues) {

        boolean leftIsNull = binOp.left() instanceof LambdaExpression.NullLiteral;
        LambdaExpression nonNullExpr = leftIsNull ? binOp.right() : binOp.left();
        ResultHandle expression = generateBiEntityExpressionAsJpaExpression(method, nonNullExpr, cb, root, join, capturedValues);

        if (binOp.operator() == EQ) {
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
            ResultHandle capturedValues) {

        ResultHandle operand = generateBiEntityPredicate(method, unOp.operand(), cb, root, join, capturedValues);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /**
     * Generates bi-entity method call (e.g., ph.type.equals("mobile")).
     * <p>
     * Handles string methods like equals(), startsWith(), contains(), etc.
     * The target of the method call is resolved using bi-entity expression generation.
     */
    private ResultHandle generateBiEntityMethodCall(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        String methodName = methodCall.methodName();

        // Handle equals() method
        if (METHOD_EQUALS.equals(methodName) && !methodCall.arguments().isEmpty()) {
            ResultHandle targetExpr = generateBiEntityExpressionAsJpaExpression(
                    method, methodCall.target(), cb, root, join, capturedValues);
            ResultHandle argExpr = generateBiEntityExpression(
                    method, methodCall.arguments().get(0), cb, root, join, capturedValues);
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_EQUAL, Predicate.class, Expression.class, Object.class),
                    cb, targetExpr, argExpr);
        }

        // Handle string pattern methods (startsWith, endsWith, contains)
        if (STRING_PATTERN_METHOD_NAMES.contains(methodName) && !methodCall.arguments().isEmpty()) {
            ResultHandle fieldExpr = generateBiEntityExpression(
                    method, methodCall.target(), cb, root, join, capturedValues);
            ResultHandle argExpr = generateBiEntityExpression(
                    method, methodCall.arguments().get(0), cb, root, join, capturedValues);
            return stringBuilder.buildStringPattern(method, methodCall, cb, fieldExpr, argExpr);
        }

        // Handle temporal comparison methods (isAfter, isBefore, isEqual)
        if (TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodName) && !methodCall.arguments().isEmpty()) {
            ResultHandle fieldExpr = generateBiEntityExpressionAsJpaExpression(
                    method, methodCall.target(), cb, root, join, capturedValues);
            ResultHandle argExpr = generateBiEntityExpression(
                    method, methodCall.arguments().get(0), cb, root, join, capturedValues);
            return temporalBuilder.buildTemporalComparison(method, methodCall, cb, fieldExpr, argExpr);
        }

        // Handle getter methods (getX, isX)
        if (methodName.startsWith(PREFIX_GET) || methodName.startsWith(PREFIX_IS)) {
            String fieldName = extractFieldName(methodName);
            ResultHandle targetExpr = generateBiEntityExpressionAsJpaExpression(
                    method, methodCall.target(), cb, root, join, capturedValues);
            if (targetExpr == null) {
                targetExpr = root; // Default to root if target cannot be resolved
            }
            return generateFieldAccess(method,
                    new LambdaExpression.FieldAccess(fieldName, methodCall.returnType()), targetExpr);
        }

        return null;
    }

    /**
     * Generates raw value from bi-entity expression.
     * Used for method arguments and captured variables.
     */
    private ResultHandle generateBiEntityExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                yield generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                yield generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
            }

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

            default -> null;
        };
    }

    // =============================================================================================
    // BI-ENTITY PROJECTIONS (Iteration 6.6: Join Projections)
    // =============================================================================================

    /**
     * Generates JPA Selection from bi-entity projection expression AST.
     * <p>
     * Used for join query projections like {@code (p, ph) -> new PersonPhoneDTO(p.firstName, ph.number)}.
     * Bi-entity projections can reference fields from both the source entity (root) and
     * joined entity (join).
     * <p>
     * Supports:
     * - Simple field projection: {@code (p, ph) -> ph.number} → query.select(join.get("number"))
     * - DTO constructor: {@code (p, ph) -> new DTO(p.firstName, ph.number)} → cb.construct(...)
     * <p>
     * Example:
     * <pre>
     * // Lambda: (p, ph) -> new PersonPhoneDTO(p.firstName, ph.number)
     * // Generated JPA: cb.construct(PersonPhoneDTO.class, root.get("firstName"), join.get("number"))
     * </pre>
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

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.ConstructorCall constructorCall ->
                generateBiEntityConstructorCall(method, constructorCall, cb, root, join, capturedValues);

            case BiEntityFieldAccess biField -> {
                ResultHandle base = getBaseForEntityPosition(biField.entityPosition(), root, join);
                yield generateFieldAccess(method,
                        new LambdaExpression.FieldAccess(biField.fieldName(), biField.fieldType()), base);
            }

            case BiEntityPathExpression biPath -> {
                ResultHandle base = getBaseForEntityPosition(biPath.entityPosition(), root, join);
                yield generatePathExpression(method,
                        new PathExpression(biPath.segments(), biPath.resultType()), base);
            }

            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(method, pathExpr, root);

            // For other expression types, delegate to generateBiEntityExpressionAsJpaExpression
            default -> generateBiEntityExpressionAsJpaExpression(method, expression, cb, root, join, capturedValues);
        };
    }

    /**
     * Generates JPA ConstructorCall for bi-entity projections (Iteration 6.6).
     * <p>
     * Converts DTO constructor call with bi-entity field access to JPA cb.construct() call.
     * <p>
     * Example:
     * <pre>
     * // Lambda: (p, ph) -> new PersonPhoneDTO(p.firstName, ph.number)
     * // Generated JPA:
     * Class<?> dtoClass = Class.forName("...PersonPhoneDTO");
     * cb.construct(dtoClass, root.get("firstName"), join.get("number"))
     * </pre>
     *
     * @param method the method creator for bytecode generation
     * @param constructorCall the constructor call expression
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle (source entity)
     * @param join the join handle (joined entity)
     * @param capturedValues the captured variables array handle
     * @return the JPA CompoundSelection handle representing the constructor expression
     */
    private ResultHandle generateBiEntityConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle join,
            ResultHandle capturedValues) {

        // Get the DTO class name (e.g., "io/quarkus/qusaq/it/dto/PersonPhoneDTO")
        String className = constructorCall.className();

        // Convert internal class name to fully qualified class name (replace / with .)
        String fqClassName = className.replace('/', '.');

        // Load the class at runtime using Class.forName()
        ResultHandle classNameHandle = method.load(fqClassName);
        ResultHandle resultClassHandle = method.invokeStaticMethod(
                MethodDescriptor.ofMethod(Class.class, "forName", Class.class, String.class),
                classNameHandle);

        // Generate JPA expressions for each constructor argument using bi-entity resolution
        int argCount = constructorCall.arguments().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            ResultHandle argExpression = generateBiEntityExpressionAsJpaExpression(
                    method, arg, cb, root, join, capturedValues);
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
    // GROUP EXPRESSIONS (Iteration 7: GROUP BY)
    // =============================================================================================

    /**
     * Generates JPA Predicate from group lambda expression AST (HAVING clause).
     * <p>
     * Used for group query HAVING predicates like {@code g -> g.count() > 5}.
     * Group expressions can reference the grouping key ({@code g.key()}) or
     * aggregation functions ({@code g.count()}, {@code g.avg()}, etc.).
     * <p>
     * Example:
     * <pre>
     * // Lambda: g -> g.count() > 5
     * // Generated JPA: cb.greaterThan(cb.count(root), 5L)
     * </pre>
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

        if (expression == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(method, binOp, cb, root, groupKeyExpr, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateGroupUnaryOperation(method, unOp, cb, root, groupKeyExpr, capturedValues);

            case GroupAggregation groupAgg ->
                // Aggregation used as a boolean predicate (rare, but possible)
                generateGroupAggregationExpression(method, groupAgg, cb, root, capturedValues);

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
     * Converts GroupKeyReference and GroupAggregation nodes to JPA expressions.
     * <p>
     * Example:
     * <pre>
     * // Lambda: g -> g.count()
     * // Generated JPA: cb.count(root)
     *
     * // Lambda: g -> g.key()
     * // Generated JPA: groupKeyExpr (the pre-computed grouping key expression)
     *
     * // Lambda: g -> g.avg(p -> p.salary)
     * // Generated JPA: cb.avg(root.get("salary"))
     * </pre>
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
                generateGroupAggregationExpression(method, groupAgg, cb, root, capturedValues);

            case LambdaExpression.ArrayCreation arrayCreation ->
                // Iteration 7: Object[] projection using cb.tuple()
                generateGroupArrayCreation(method, arrayCreation, cb, root, groupKeyExpr, capturedValues);

            case LambdaExpression.ConstructorCall constructorCall ->
                // DTO constructor with group elements
                generateGroupConstructorCall(method, constructorCall, cb, root, groupKeyExpr, capturedValues);

            case LambdaExpression.FieldAccess field ->
                // Field access in group context (from nested lambda in aggregation)
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

            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(method, binOp, cb, root, groupKeyExpr, capturedValues);

            default -> null;
        };
    }

    /**
     * Generates JPA Expression for group ORDER BY clause.
     * <p>
     * Used for sorting group query results by group key or aggregation values.
     * <p>
     * Example:
     * <pre>
     * // sortedBy(g -> g.count()) -> cb.count(root)
     * // sortedBy(g -> g.key()) -> groupKeyExpr
     * </pre>
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

        // Delegate to generateGroupSelectExpression since they handle the same types
        return generateGroupSelectExpression(method, expression, cb, root, groupKeyExpr, capturedValues);
    }

    /**
     * Generates JPA aggregation expression for GroupAggregation AST node.
     * <p>
     * Maps GroupAggregationType to JPA CriteriaBuilder aggregation methods:
     * <ul>
     *   <li>COUNT -> cb.count(root)</li>
     *   <li>COUNT_DISTINCT -> cb.countDistinct(fieldExpr)</li>
     *   <li>AVG -> cb.avg(fieldExpr)</li>
     *   <li>SUM_INTEGER -> cb.sum(fieldExpr) [returns Integer]</li>
     *   <li>SUM_LONG -> cb.sumAsLong(fieldExpr)</li>
     *   <li>SUM_DOUBLE -> cb.sumAsDouble(fieldExpr)</li>
     *   <li>MIN -> cb.min(fieldExpr) or cb.least(fieldExpr) for non-numeric</li>
     *   <li>MAX -> cb.max(fieldExpr) or cb.greatest(fieldExpr) for non-numeric</li>
     * </ul>
     *
     * @param method the method creator for bytecode generation
     * @param groupAgg the group aggregation expression
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Expression handle for the aggregation
     */
    private ResultHandle generateGroupAggregationExpression(
            MethodCreator method,
            GroupAggregation groupAgg,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues) {

        GroupAggregationType aggType = groupAgg.aggregationType();

        // Handle COUNT specially - it operates on the root, not a field
        if (aggType == GroupAggregationType.COUNT) {
            return method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, "count", Expression.class, Expression.class),
                    cb, root);
        }

        // For all other aggregations, we need to extract the field expression
        // The fieldExpression in GroupAggregation is the analyzed nested lambda (e.g., p -> p.salary)
        // which should be a FieldAccess or PathExpression
        LambdaExpression fieldExpr = groupAgg.fieldExpression();
        ResultHandle fieldPath = generateExpressionAsJpaExpression(method, fieldExpr, cb, root, capturedValues);

        if (fieldPath == null) {
            // Fallback: if field expression is null, use root
            fieldPath = root;
        }

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
        };
    }

    /**
     * Generates group binary operation (comparison, logical, arithmetic).
     * <p>
     * Handles operations like {@code g.count() > 5} or {@code g.avg() < limit}.
     */
    private ResultHandle generateGroupBinaryOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        // Logical operations (AND, OR)
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateGroupPredicate(method, binOp.left(), cb, root, groupKeyExpr, capturedValues);
            ResultHandle right = generateGroupPredicate(method, binOp.right(), cb, root, groupKeyExpr, capturedValues);
            return combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Arithmetic operations
        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateGroupSelectExpression(method, binOp.left(), cb, root, groupKeyExpr, capturedValues);
            ResultHandle right = generateGroupSelectExpression(method, binOp.right(), cb, root, groupKeyExpr, capturedValues);
            return generateArithmeticOperation(method, binOp.operator(), cb, left, right);
        }

        // Comparison operations (most common in HAVING)
        ResultHandle left = generateGroupSelectExpression(method, binOp.left(), cb, root, groupKeyExpr, capturedValues);
        ResultHandle right = generateGroupSelectExpression(method, binOp.right(), cb, root, groupKeyExpr, capturedValues);
        return generateComparisonOperation(method, binOp.operator(), cb, left, right);
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
            ResultHandle capturedValues) {

        ResultHandle operand = generateGroupPredicate(method, unOp.operand(), cb, root, groupKeyExpr, capturedValues);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(
                    methodDescriptor(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class), cb, operand);
        };
    }

    /**
     * Generates JPA multiselect array for Object[] projections in group context.
     * <p>
     * Iteration 7: Converts {@code new Object[]{g.key(), g.count()}} to an array of
     * JPA Selection elements that will be used with {@code query.multiselect()}.
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

        int elementCount = arrayCreation.elements().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, elementCount);

        for (int i = 0; i < elementCount; i++) {
            LambdaExpression element = arrayCreation.elements().get(i);
            ResultHandle elementSelection = generateGroupSelectExpression(
                    method, element, cb, root, groupKeyExpr, capturedValues);
            method.writeArrayValue(selectionsArray, i, elementSelection);
        }

        return selectionsArray;
    }

    /**
     * Generates JPA tuple for Object[] projections in group context.
     * <p>
     * Iteration 7: Uses cb.tuple() to create a compound selection for Object[] projections.
     * The resulting Tuple objects will be converted to Object[] at runtime.
     */
    private ResultHandle generateGroupArrayCreation(
            MethodCreator method,
            LambdaExpression.ArrayCreation arrayCreation,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        // Generate Selection array for all elements
        ResultHandle selectionsArray = generateGroupArraySelections(
                method, arrayCreation, cb, root, groupKeyExpr, capturedValues);

        // Use cb.tuple() to create a compound selection
        MethodDescriptor tupleMethod = MethodDescriptor.ofMethod(
                CriteriaBuilder.class,
                "tuple",
                CompoundSelection.class,
                Selection[].class);

        return method.invokeInterfaceMethod(tupleMethod, cb, selectionsArray);
    }

    /**
     * Generates JPA constructor expression for DTO projections in group context.
     * <p>
     * Converts {@code new DeptStats(g.key(), g.count())} to
     * {@code cb.construct(DeptStats.class, groupKeyExpr, cb.count(root))}.
     *
     * @param method the method creator for bytecode generation
     * @param constructorCall the constructor call expression from the lambda AST
     * @param cb the CriteriaBuilder handle
     * @param root the root entity handle
     * @param groupKeyExpr the JPA expression for the grouping key
     * @param capturedValues the captured variables array handle
     * @return the JPA CompoundSelection handle representing the constructor expression
     */
    private ResultHandle generateGroupConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues) {

        // Get the DTO class name
        String className = constructorCall.className();
        String fqClassName = className.replace('/', '.');

        // Load the class at runtime
        ResultHandle classNameHandle = method.load(fqClassName);
        ResultHandle resultClassHandle = method.invokeStaticMethod(
                MethodDescriptor.ofMethod(Class.class, "forName", Class.class, String.class),
                classNameHandle);

        // Generate JPA expressions for each constructor argument
        int argCount = constructorCall.arguments().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            ResultHandle argExpression = generateGroupSelectExpression(method, arg, cb, root, groupKeyExpr, capturedValues);
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
}
