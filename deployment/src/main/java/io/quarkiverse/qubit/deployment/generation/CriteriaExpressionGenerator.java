package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isBooleanType;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.BinaryOperationCategory;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsScalarSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.containsSubquery;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isNegatedSubqueryComparison;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isSubqueryBooleanComparison;
import static io.quarkiverse.qubit.deployment.generation.GizmoHelper.createElementArray;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CASE_OTHERWISE_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CASE_WHEN_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_AND;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_BETWEEN_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NULLIF_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_TREAT_ROOT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CONCAT_EXPR_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CONSTRUCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_EQUAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_FALSE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_MEMBER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_NOT_MEMBER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_IS_TRUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_LITERAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NULL_LITERAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT_EQUAL;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_OR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SELECT_CASE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CLASS_FOR_NAME;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EXPRESSION_IN_COLLECTION;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.PATH_GET;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.PATH_TYPE;

import java.util.Objects;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkiverse.qubit.deployment.generation.expression.BiEntityBaseContext;
import io.quarkiverse.qubit.deployment.generation.expression.MathExpressionBuilder;
import io.quarkiverse.qubit.deployment.generation.expression.BiEntitySubqueryContext;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkiverse.qubit.deployment.generation.methodcall.GenerationResult;
import io.quarkiverse.qubit.deployment.generation.methodcall.MethodCallHandlerChain;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Converts lambda expression AST into JPA Criteria API bytecode using Gizmo 2.
 *
 * <p>
 * Implements ExpressionGeneratorHelper to provide common generation methods
 * to specialized builders (BiEntityExpressionBuilder, GroupExpressionBuilder).
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

    /** Generates JPA Predicate from lambda expression AST. Returns null if expression is null. */
    public @Nullable Expr generatePredicate(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperation(bc, binOp, cb, root, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateUnaryOperation(bc, unOp, cb, root, capturedValues);

            case LambdaExpression.FieldAccess field -> {
                Expr path = generateFieldAccess(bc, field, root);
                yield wrapBooleanAsPredicateIfNeeded(bc, cb, path, field.fieldType());
            }

            case PathExpression pathExpr -> {
                Expr path = generatePathExpression(bc, pathExpr, root);
                yield wrapBooleanAsPredicateIfNeeded(bc, cb, path, pathExpr.resultType());
            }

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(bc, methodCall, cb, root, capturedValues);

            case InExpression inExpr ->
                generateInPredicate(bc, inExpr, cb, root, capturedValues);

            case MemberOfExpression memberOfExpr ->
                generateMemberOfPredicate(bc, memberOfExpr, cb, root, capturedValues);

            case LambdaExpression.MathFunction mathFunc ->
                generateMathFunction(bc, cb, root, capturedValues, mathFunc);

            case LambdaExpression.TreatExpression treat ->
                generateTreatExpression(bc, treat, cb, root, capturedValues);

            case LambdaExpression.InstanceOf instanceOf -> {
                // TYPE(e) = SubType check: cb.equal(root.type(), cb.literal(SubType.class))
                Expr rootType = bc.invokeInterface(PATH_TYPE, root);
                Expr typeClass = Const.of(instanceOf.targetType());
                Expr typeLiteral = bc.invokeInterface(CB_LITERAL, cb, typeClass);
                yield bc.invokeInterface(CB_EQUAL, cb, rootType, typeLiteral);
            }

            case LambdaExpression.SqlCast sqlCast ->
                generatePredicate(bc, sqlCast.expression(), cb, root, capturedValues);

            case LambdaExpression.Cast cast ->
                generatePredicate(bc, cast.expression(), cb, root, capturedValues);

            default -> throw new UnsupportedExpressionException(expression, "predicate generation");
        };
    }

    /**
     * Generates JPA Predicate with subquery support (ExistsSubquery, InSubquery, ScalarSubquery).
     * Accepts CriteriaQuery for creating subqueries.
     */
    public @Nullable Expr generatePredicateWithSubqueries(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr query,
            Expr root,
            Expr capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            // Handle subquery expressions first
            case ExistsSubquery existsSubquery ->
                builderRegistry.subqueryBuilder().buildExistsSubquery(bc, existsSubquery, cb, query, root, capturedValues);

            case InSubquery inSubquery ->
                builderRegistry.subqueryBuilder().buildInSubquery(bc, inSubquery, cb, query, root, capturedValues);

            // Handle binary operations that may contain subqueries
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperationWithSubqueries(bc, binOp, cb, query, root, capturedValues);

            case LambdaExpression.UnaryOp unOp ->
                generateUnaryOperationWithSubqueries(bc, unOp, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generatePredicate(bc, expression, cb, root, capturedValues);
        };
    }

    /** Generates JPA Expression with ScalarSubquery support. Returns null if expression is null. */
    public @Nullable Expr generateExpressionWithSubqueries(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr query,
            Expr root,
            Expr capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case ScalarSubquery scalarSubquery ->
                builderRegistry.subqueryBuilder().buildScalarSubquery(bc, scalarSubquery, cb, query, root, capturedValues);

            // For non-subquery expressions, delegate to the original method
            default -> generateExpressionAsJpaExpression(bc, expression, cb, root, capturedValues);
        };
    }

    /**
     * Generates binary operation with subquery support.
     * Handles comparisons that may contain ScalarSubquery on either side.
     */
    private Expr generateBinaryOperationWithSubqueries(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr query,
            Expr root,
            Expr capturedValues) {

        // Check for logical operations (AND, OR)
        if (isLogicalOperation(binOp)) {
            Expr left = generatePredicateWithSubqueries(bc, binOp.left(), cb, query, root, capturedValues);
            Expr right = generatePredicateWithSubqueries(bc, binOp.right(), cb, query, root, capturedValues);
            return combinePredicates(bc, cb, left, right, binOp.operator());
        }

        // Only scalar subqueries can be used in comparisons. EXISTS/IN are predicates, not expressions.
        boolean leftHasScalarSubquery = containsScalarSubquery(binOp.left());
        boolean rightHasScalarSubquery = containsScalarSubquery(binOp.right());

        if (leftHasScalarSubquery || rightHasScalarSubquery) {
            // Generate expressions with subquery support
            Expr left = generateExpressionWithSubqueries(bc, binOp.left(), cb, query, root, capturedValues);
            Expr right = generateExpressionWithSubqueries(bc, binOp.right(), cb, query, root, capturedValues);
            return generateComparisonOperation(bc, binOp.operator(), cb, left, right);
        }

        // Handle EXISTS/IN subquery patterns
        boolean leftHasSubquery = containsSubquery(binOp.left());
        boolean rightHasSubquery = containsSubquery(binOp.right());

        if (leftHasSubquery || rightHasSubquery) {
            return handleSubqueryComparison(bc, binOp, cb, query, root, capturedValues, leftHasSubquery);
        }

        // No subqueries - delegate to original method
        return generateBinaryOperation(bc, binOp, cb, root, capturedValues);
    }

    /** Handles comparison operations involving EXISTS/IN subqueries. */
    private Expr handleSubqueryComparison(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr query,
            Expr root,
            Expr capturedValues,
            boolean leftHasSubquery) {

        // If comparing a subquery predicate to a boolean constant, simplify
        if (isSubqueryBooleanComparison(binOp)) {
            LambdaExpression subqueryExpr = leftHasSubquery ? binOp.left() : binOp.right();
            LambdaExpression constantExpr = leftHasSubquery ? binOp.right() : binOp.left();
            Expr predicate = generatePredicateWithSubqueries(bc, subqueryExpr, cb, query, root, capturedValues);

            // If comparing to false or using NE with true, negate the result
            if (isNegatedSubqueryComparison(binOp.operator(), constantExpr)) {
                return bc.invokeInterface(CB_NOT, cb, predicate);
            }
            return predicate;
        }

        // For other patterns with subqueries, recursively process with subquery support
        Expr left = generatePredicateWithSubqueries(bc, binOp.left(), cb, query, root, capturedValues);
        Expr right = generatePredicateWithSubqueries(bc, binOp.right(), cb, query, root, capturedValues);
        return generateComparisonOperation(bc, binOp.operator(), cb, left, right);
    }

    /**
     * Generates unary operation with subquery support.
     * Handles NOT operations that may wrap subquery predicates.
     */
    private Expr generateUnaryOperationWithSubqueries(
            BlockCreator bc,
            LambdaExpression.UnaryOp unOp,
            Expr cb,
            Expr query,
            Expr root,
            Expr capturedValues) {

        Expr operand = generatePredicateWithSubqueries(bc, unOp.operand(), cb, query, root, capturedValues);
        return applyUnaryOperator(bc, cb, operand, unOp.operator());
    }

    /**
     * Generates raw value from lambda expression.
     * <p>
     * Uses pattern matching switch for cleaner type dispatch.
     */
    @Override
    public @Nullable Expr generateExpression(
            BlockCreator bc,
            @Nullable LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(bc, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(bc, pathExpr, root);

            case LambdaExpression.Constant constant ->
                generateConstant(bc, constant);

            case LambdaExpression.CapturedVariable capturedVar ->
                loadCapturedValue(bc, capturedVar, capturedValues);

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(bc, methodCall, cb, root, capturedValues);

            case LambdaExpression.CorrelatedVariable correlated ->
                generateCorrelatedFieldExpression(bc, correlated, root);

            default -> throw new UnsupportedExpressionException(expression, "raw expression generation");
        };
    }

    /**
     * Generates JPA Expression from lambda expression.
     * Includes Parameter handling for identity sort functions.
     * <p>
     * Uses pattern matching switch for cleaner type dispatch.
     */
    @Override
    public @Nullable Expr generateExpressionAsJpaExpression(
            BlockCreator bc,
            @Nullable LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.FieldAccess field ->
                generateFieldAccess(bc, field, root);

            case PathExpression pathExpr ->
                generatePathExpression(bc, pathExpr, root);

            case LambdaExpression.Constant constant -> {
                Expr constantValue = generateConstant(bc, constant);
                yield wrapAsLiteral(bc, cb, constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                loadAndWrapCapturedValue(bc, cb, capturedVar, capturedValues);

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCall(bc, methodCall, cb, root, capturedValues);

            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOperation(bc, binOp, cb, root, capturedValues);

            case LambdaExpression.ConstructorCall constructorCall ->
                generateConstructorCall(bc, constructorCall, cb, root, capturedValues);

            case LambdaExpression.Parameter _ ->
                // Parameter expressions occur in identity sort functions like (String s) -> s
                // These cannot be directly converted to JPA expressions - return null to signal
                // to caller that special handling is needed
                null;

            case InExpression inExpr ->
                // InExpression is a predicate but Predicate extends Expression<Boolean>
                // so we can return it as a JPA expression
                generateInPredicate(bc, inExpr, cb, root, capturedValues);

            case MemberOfExpression memberOfExpr ->
                // MemberOfExpression is also a predicate that can be used as expression
                generateMemberOfPredicate(bc, memberOfExpr, cb, root, capturedValues);

            case LambdaExpression.UnaryOp unaryOp ->
                // UnaryOp (NOT) is a predicate that can be used as expression
                // This occurs when IFEQ creates NOT(InExpression) for short-circuit evaluation
                generateUnaryOperation(bc, unaryOp, cb, root, capturedValues);

            case LambdaExpression.CorrelatedVariable correlated ->
                generateCorrelatedFieldExpression(bc, correlated, root);

            case LambdaExpression.Conditional conditional -> {
                // NULLIF optimization: detect field == sentinel ? null : field
                PatternDetector.NullifComponents nullif = PatternDetector.detectNullif(conditional);
                if (nullif != null) {
                    yield generateNullifExpression(bc, cb, root, capturedValues, nullif);
                }
                // Standard CASE WHEN handling
                yield generateConditionalExpression(bc, conditional, cb, root, capturedValues);
            }

            case LambdaExpression.NullLiteral(var expectedType) -> {
                // Generate cb.nullLiteral(Type.class) for null values in expressions
                Expr typeClass = Const.of(expectedType);
                yield bc.invokeInterface(CB_NULL_LITERAL, cb, typeClass);
            }

            case LambdaExpression.MathFunction mathFunc ->
                generateMathFunction(bc, cb, root, capturedValues, mathFunc);

            case LambdaExpression.TreatExpression treat ->
                generateTreatExpression(bc, treat, cb, root, capturedValues);

            case LambdaExpression.FoldedMethodCall folded ->
                generateFoldedMethodCall(bc, cb, root, capturedValues, folded);

            case LambdaExpression.SqlCast sqlCast ->
                generateSqlCast(bc, sqlCast, cb, root, capturedValues);

            // Strip CHECKCAST wrappers (from generics erasure) — no SQL semantics
            case LambdaExpression.Cast cast ->
                generateExpressionAsJpaExpression(bc, cast.expression(), cb, root, capturedValues);

            case LambdaExpression.InstanceOf instanceOf -> {
                // TYPE(e) = SubType check: cb.equal(root.type(), cb.literal(SubType.class))
                // Predicate extends Expression<Boolean>, so this is valid as a JPA expression.
                Expr rootType = bc.invokeInterface(PATH_TYPE, root);
                Expr typeClass = Const.of(instanceOf.targetType());
                Expr typeLiteral = bc.invokeInterface(CB_LITERAL, cb, typeClass);
                yield bc.invokeInterface(CB_EQUAL, cb, rootType, typeLiteral);
            }

            default -> throw new UnsupportedExpressionException(expression);
        };
    }

    /**
     * Generates JPA CASE WHEN expression from a ternary conditional.
     * <p>
     * Maps: {@code condition ? trueValue : falseValue}
     * To JPA: {@code cb.selectCase().when(conditionPredicate, trueExpr).otherwise(falseExpr)}
     */
    public Expr generateConditionalExpression(
            BlockCreator bc,
            LambdaExpression.Conditional conditional,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        // Generate and store sub-expressions in LocalVars (Gizmo2 stack discipline requirement)
        LocalVar conditionLocal = bc.localVar("ternaryCondition",
                generatePredicate(bc, conditional.condition(), cb, root, capturedValues));
        LocalVar trueLocal = bc.localVar("ternaryTrue",
                generateExpressionAsJpaExpression(bc, conditional.trueValue(), cb, root, capturedValues));
        LocalVar falseLocal = bc.localVar("ternaryFalse",
                generateExpressionAsJpaExpression(bc, conditional.falseValue(), cb, root, capturedValues));

        // Build: cb.selectCase().when(condition, trueExpr).otherwise(falseExpr)
        LocalVar caseBuilder = bc.localVar("caseBuilder", bc.invokeInterface(CB_SELECT_CASE, cb));
        LocalVar caseWhen = bc.localVar("caseWhen",
                bc.invokeInterface(CASE_WHEN_EXPR, caseBuilder, conditionLocal, trueLocal));
        return bc.invokeInterface(CASE_OTHERWISE_EXPR, caseWhen, falseLocal);
    }

    /** Generates cb.nullif(expression, sentinel) for detected NULLIF patterns. */
    private Expr generateNullifExpression(BlockCreator bc, Expr cb, Expr root, Expr capturedValues,
            PatternDetector.NullifComponents nullif) {
        Expr fieldExpr = generateExpressionAsJpaExpression(bc, nullif.expression(), cb, root, capturedValues);
        Expr sentinelExpr = generateExpressionAsJpaExpression(bc, nullif.sentinel(), cb, root, capturedValues);
        return bc.invokeInterface(CB_NULLIF_EXPR, cb, fieldExpr, sentinelExpr);
    }

    /** Generates a JPA math function expression from a MathFunction AST node. */
    private Expr generateMathFunction(BlockCreator bc, Expr cb, Expr root, Expr capturedValues,
            LambdaExpression.MathFunction mathFunc) {

        // Generate the primary operand as a JPA Expression
        Expr operandExpr = generateExpressionAsJpaExpression(bc, mathFunc.operand(), cb, root, capturedValues);

        // Generate the second operand for binary operations
        Expr secondExpr = null;
        if (mathFunc.op().isBinary()) {
            if (mathFunc.op() == LambdaExpression.MathFunction.MathOp.ROUND) {
                // round() second arg is Integer, not Expression -- use raw value
                secondExpr = generateExpression(bc, mathFunc.secondOperand(), cb, root, capturedValues);
            } else {
                // power() second arg is Expression
                secondExpr = generateExpressionAsJpaExpression(bc, mathFunc.secondOperand(), cb, root, capturedValues);
            }
        }

        return MathExpressionBuilder.build(bc, cb, operandExpr, secondExpr, mathFunc.op());
    }

    /**
     * Generates JPA TREAT expression for inheritance type casting.
     * <p>
     * Maps: {@code ((Dog) a).breed} or {@code a instanceof Dog d && d.breed}
     * To JPA: {@code cb.treat(root, Dog.class).get("breed")}
     */
    private Expr generateTreatExpression(
            BlockCreator bc,
            LambdaExpression.TreatExpression treat,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        // cb.treat(root, Dog.class) -> Root<Dog>
        Expr classLiteral = Const.of(treat.treatType());
        LocalVar treatedRoot = bc.localVar("treatedRoot",
                bc.invokeInterface(CB_TREAT_ROOT, cb, root, classLiteral));

        // Generate inner expression using treatedRoot instead of root
        return generateExpressionAsJpaExpression(bc, treat.inner(), cb, treatedRoot, capturedValues);
    }

    /**
     * Generates runtime evaluation of a folded static method call.
     * Evaluates the static method with the given arguments and wraps the result as a JPA literal.
     */
    private Expr generateFoldedMethodCall(BlockCreator bc, Expr cb, Expr root, Expr capturedValues,
            LambdaExpression.FoldedMethodCall folded) {

        // Generate each argument as a raw value (Constant or CapturedVariable)
        Expr[] argExprs = new Expr[folded.arguments().size()];
        for (int i = 0; i < folded.arguments().size(); i++) {
            argExprs[i] = generateExpression(bc, folded.arguments().get(i), cb, root, capturedValues);
        }

        // Build MethodDesc for the static method
        Class<?>[] paramTypes = DescriptorParser.getParameterTypes(folded.methodDescriptor());
        MethodDesc staticMethod = MethodDesc.of(folded.ownerClass(), folded.methodName(),
                folded.returnType(), paramTypes);

        // Invoke the static method: result = OwnerClass.methodName(arg1, arg2, ...)
        Expr result = bc.invokeStatic(staticMethod, argExprs);

        // Wrap the result as a JPA literal: cb.literal(result)
        return wrapAsLiteral(bc, cb, result);
    }

    /** Generates SQL CAST expression: expression.cast(targetType). */
    private Expr generateSqlCast(BlockCreator bc, LambdaExpression.SqlCast sqlCast, Expr cb, Expr root,
            Expr capturedValues) {
        Expr innerExpr = generateExpressionAsJpaExpression(bc, sqlCast.expression(), cb, root, capturedValues);
        LocalVar innerLocal = bc.localVar("castSource", innerExpr);
        Expr targetClass = Const.of(sqlCast.targetType());
        return bc.invokeInterface(MethodDescriptors.EXPRESSION_CAST, innerLocal, targetClass);
    }

    /**
     * Generates comparison, logical, arithmetic, or string concatenation operation.
     */
    public Expr generateBinaryOperation(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        BinaryOperationCategory category = BinaryOperationCategory.categorize(binOp, this::isStringConcatenation);

        return switch (category) {
            case STRING_CONCATENATION -> {
                Expr left = generateExpressionAsJpaExpression(bc, binOp.left(), cb, root, capturedValues);
                Expr right = generateExpressionAsJpaExpression(bc, binOp.right(), cb, root, capturedValues);
                yield generateStringConcatenation(bc, cb, left, right);
            }

            case ARITHMETIC -> {
                Expr left = generateExpressionAsJpaExpression(bc, binOp.left(), cb, root, capturedValues);
                Expr right = generateExpressionAsJpaExpression(bc, binOp.right(), cb, root, capturedValues);
                yield generateArithmeticOperation(bc, binOp.operator(), cb, left, right);
            }

            case LOGICAL -> {
                // BETWEEN optimization: detect field >= low && field <= high
                if (binOp.operator() == LambdaExpression.BinaryOp.Operator.AND) {
                    PatternDetector.BetweenComponents between = PatternDetector.detectBetween(binOp);
                    if (between != null) {
                        yield generateBetweenPredicate(bc, cb, root, capturedValues, between);
                    }
                }
                // Standard AND/OR handling
                Expr left = generatePredicate(bc, binOp.left(), cb, root, capturedValues);
                Expr right = generatePredicate(bc, binOp.right(), cb, root, capturedValues);
                yield combinePredicates(bc, cb, left, right, binOp.operator());
            }

            case NULL_CHECK -> generateNullCheckPredicate(bc, binOp, cb, root, capturedValues);

            case BOOLEAN_FIELD_CONSTANT -> generateBooleanFieldConstantPredicate(bc, binOp, cb, root, capturedValues);

            case BOOLEAN_FIELD_CAPTURED_VARIABLE ->
                generateBooleanFieldCapturedVariablePredicate(bc, binOp, cb, root, capturedValues);

            case COMPARE_TO_EQUALITY -> {
                // compareTo equality can return null if constant is not 0/false/true
                Expr result = generateCompareToEqualityPredicate(bc, binOp, cb, root, capturedValues);
                yield result != null ? result : generateDefaultComparison(bc, binOp, cb, root, capturedValues);
            }

            case COMPARISON -> generateDefaultComparison(bc, binOp, cb, root, capturedValues);
        };
    }

    /**
     * Generates default comparison operation (EQ, NE, LT, LE, GT, GE).
     * Extracted for reuse in fallthrough case from COMPARE_TO_EQUALITY.
     */
    private Expr generateDefaultComparison(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        Expr left = generateExpressionAsJpaExpression(bc, binOp.left(), cb, root, capturedValues);
        Expr right = generateExpressionAsJpaExpression(bc, binOp.right(), cb, root, capturedValues);
        return generateComparisonOperation(bc, binOp.operator(), cb, left, right);
    }

    /** Generates null check predicate (IS NULL or IS NOT NULL). */
    private Expr generateNullCheckPredicate(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        LambdaExpression nonNullExpr = extractNonNullExpression(binOp);
        Expr expression = generateExpression(bc, nonNullExpr, cb, root, capturedValues);
        return generateNullCheckPredicate(bc, cb, expression, binOp.operator());
    }

    /** Generates boolean field comparison with constant 0 or 1. */
    private Expr generateBooleanFieldConstantPredicate(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        LambdaExpression.Constant constant = (LambdaExpression.Constant) binOp.right();
        Expr field = generateExpression(bc, binOp.left(), cb, root, capturedValues);

        boolean compareToTrue = constant.value().equals(1);
        boolean isEqualOp = binOp.operator() == EQ;
        boolean useIsTrue = (isEqualOp && compareToTrue) || (!isEqualOp && !compareToTrue);

        if (useIsTrue) {
            return bc.invokeInterface(CB_IS_TRUE, cb, field);
        } else {
            return bc.invokeInterface(CB_IS_FALSE, cb, field);
        }
    }

    /** Generates boolean field comparison with captured variable. */
    private Expr generateBooleanFieldCapturedVariablePredicate(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        Expr fieldExpr = generateExpressionAsJpaExpression(bc, binOp.left(), cb, root, capturedValues);
        Expr capturedExpr = generateExpressionAsJpaExpression(bc, binOp.right(), cb, root, capturedValues);

        if (binOp.operator() == EQ) {
            return bc.invokeInterface(CB_EQUAL, cb, fieldExpr, capturedExpr);
        } else {
            return bc.invokeInterface(CB_NOT_EQUAL, cb, fieldExpr, capturedExpr);
        }
    }

    /** Generates compareTo equality pattern predicate. Returns null if not applicable. */
    private Expr generateCompareToEqualityPredicate(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) binOp.left();
        LambdaExpression.Constant constant = (LambdaExpression.Constant) binOp.right();

        boolean isEqualityCheck = constant.value().equals(0) ||
                (constant.value() instanceof Boolean && constant.value().equals(false));
        boolean isInequalityCheck = (constant.value() instanceof Boolean && constant.value().equals(true));

        if (isEqualityCheck || isInequalityCheck) {
            Expr field = generateExpressionAsJpaExpression(bc, methodCall.target(), cb, root, capturedValues);
            Expr argument = generateExpression(bc, methodCall.arguments().getFirst(), cb, root, capturedValues);

            if (isEqualityCheck) {
                return bc.invokeInterface(CB_EQUAL, cb, field, argument);
            } else {
                return bc.invokeInterface(CB_NOT_EQUAL, cb, field, argument);
            }
        }
        return null;
    }

    /** Generates unary NOT operation. */
    public Expr generateUnaryOperation(
            BlockCreator bc,
            LambdaExpression.UnaryOp unOp,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        Expr operand = generatePredicate(bc, unOp.operand(), cb, root, capturedValues);
        return applyUnaryOperator(bc, cb, operand, unOp.operator());
    }

    /** Generates JPA field access expression. */
    @Override
    public Expr generateFieldAccess(
            BlockCreator bc,
            LambdaExpression.FieldAccess field,
            Expr root) {

        Expr fieldName = Const.of(field.fieldName());
        return bc.invokeInterface(PATH_GET, root, fieldName);
    }

    /**
     * Generates JPA path expression for relationship navigation.
     * Converts PathExpression to chained Path.get() calls.
     */
    @Override
    public Expr generatePathExpression(
            BlockCreator bc,
            PathExpression pathExpr,
            Expr root) {

        // Use LocalVar for intermediate path values used across loop iterations (Gizmo2 requirement)
        LocalVar currentPath = bc.localVar("currentPath", root);

        for (PathSegment segment : pathExpr.segments()) {
            Expr fieldName = Const.of(segment.fieldName());
            bc.set(currentPath, bc.invokeInterface(PATH_GET, currentPath, fieldName));
        }

        return currentPath;
    }

    /**
     * Generates constant value bytecode.
     * <p>
     * Delegates to {@link GizmoHelper#loadConstant(BlockCreator, Object)} for the
     * actual bytecode generation.
     */
    @Override
    public Expr generateConstant(BlockCreator bc, LambdaExpression.Constant constant) {
        return GizmoHelper.loadConstant(bc, constant.value());
    }

    /**
     * Generates JPA expression for method call using handler chain.
     *
     * @throws UnsupportedExpressionException if the method call is not supported
     */
    public Expr generateMethodCall(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        GenerationResult result = methodCallHandlerChain.handleMethodCall(
                bc, methodCall, cb, root, capturedValues, builderRegistry, this);

        return switch (result) {
            case GenerationResult.Success(var handle) -> handle;
            case GenerationResult.Unsupported(var methodName, var reason) ->
                throw new UnsupportedExpressionException(methodCall, "method call: " + methodName + " - " + reason);
        };
    }

    /** Generates JPA cb.construct() for DTO projections. */
    private Expr generateConstructorCall(
            BlockCreator bc,
            LambdaExpression.ConstructorCall constructorCall,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        // Load the DTO class at runtime
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        // Store cb and resultClassHandle in LocalVars since they're passed in from another context
        LocalVar cbLocal = bc.localVar("cbLocal", cb);
        LocalVar resultClassLocal = bc.localVar("resultClassLocal", loadDtoClass(bc, constructorCall.className()));

        // Generate JPA expressions for each constructor argument
        int argCount = constructorCall.arguments().size();
        LocalVar selectionsArray = bc.localVar("selectionsArray", bc.newEmptyArray(Selection.class, argCount));

        for (int i = 0; i < argCount; i++) {
            LambdaExpression arg = constructorCall.arguments().get(i);
            Expr argExpression = generateExpressionAsJpaExpression(bc, arg, cb, root, capturedValues);

            // Null check: Parameter expressions return null and are not allowed in constructor arguments
            if (argExpression == null) {
                throw UnsupportedExpressionException.inConstructorArgument(arg, i, constructorCall.className());
            }

            // Store each generated expression in a LocalVar before array assignment
            LocalVar argExprLocal = bc.localVar("arg" + i, argExpression);
            bc.set(selectionsArray.elem(i), argExprLocal);
        }

        // Call cb.construct(resultClass, selections...)
        return bc.invokeInterface(CB_CONSTRUCT, cbLocal, resultClassLocal, selectionsArray);
    }

    /** Generates JPA IN predicate: cities.contains(p.city) -> root.get("city").in(cities). */
    private Expr generateInPredicate(
            BlockCreator bc,
            InExpression inExpr,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        // Generate the field expression (e.g., root.get("city"))
        Expr fieldExpr = generateExpressionAsJpaExpression(bc, inExpr.field(), cb, root, capturedValues);

        // Null check: Parameter expressions return null and are not allowed in IN predicates
        if (fieldExpr == null) {
            throw new UnsupportedExpressionException(inExpr.field(), "IN predicate field");
        }

        // Generate the collection expression (e.g., capturedValues[0])
        Expr collectionExpr = generateExpression(bc, inExpr.collection(), cb, root, capturedValues);

        // Call fieldExpr.in(collection) - Expression.in(Collection)
        // This creates a Predicate that checks if field value is in the collection
        Expr inPredicate = bc.invokeInterface(EXPRESSION_IN_COLLECTION, fieldExpr, collectionExpr);

        // If negated (NOT IN), wrap with cb.not()
        if (inExpr.negated()) {
            return bc.invokeInterface(CB_NOT, cb, inPredicate);
        }

        return inPredicate;
    }

    /** Generates JPA MEMBER OF: p.roles.contains("admin") -> cb.isMember("admin", root.get("roles")). */
    private Expr generateMemberOfPredicate(
            BlockCreator bc,
            MemberOfExpression memberOfExpr,
            Expr cb,
            Expr root,
            Expr capturedValues) {

        // Generate the value expression (e.g., "admin" constant or captured variable)
        Expr valueExpr = generateExpression(bc, memberOfExpr.value(), cb, root, capturedValues);

        // Generate the collection field expression (e.g., root.get("roles"))
        Expr collectionFieldExpr = generateExpressionAsJpaExpression(
                bc, memberOfExpr.collectionField(), cb, root, capturedValues);

        MethodDesc mdMember = memberOfExpr.negated() ? CB_IS_NOT_MEMBER : CB_IS_MEMBER;
        return bc.invokeInterface(mdMember, cb, valueExpr, collectionFieldExpr);
    }

    /** Generates arithmetic operations. Delegates to ArithmeticExpressionBuilder. */
    @Override
    public Expr generateArithmeticOperation(
            BlockCreator bc,
            LambdaExpression.BinaryOp.Operator operator,
            Expr cb,
            Expr left,
            Expr right) {

        return builderRegistry.arithmeticBuilder().buildArithmeticOperation(bc, operator, cb, left, right);
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
            case LambdaExpression.BinaryOp binOp -> isStringConcatenation(binOp); // Recursive: concatenation of concatenations
            case null, default -> false;
        };
    }

    /**
     * Generates string concatenation using JPA CriteriaBuilder.concat().
     * Handles chaining of multiple concat operations.
     */
    @Override
    public Expr generateStringConcatenation(
            BlockCreator bc,
            Expr cb,
            Expr left,
            Expr right) {

        return bc.invokeInterface(CB_CONCAT_EXPR_EXPR, cb, left, right);
    }

    /** Generates comparison operations. Delegates to ComparisonExpressionBuilder. */
    @Override
    public Expr generateComparisonOperation(
            BlockCreator bc,
            LambdaExpression.BinaryOp.Operator operator,
            Expr cb,
            Expr left,
            Expr right) {

        return builderRegistry.comparisonBuilder().buildComparisonOperation(bc, operator, cb, left, right);
    }

    /** Combines two predicates with AND or OR. */
    @Override
    public Expr combinePredicates(
            BlockCreator bc,
            Expr cb,
            Expr left,
            Expr right,
            LambdaExpression.BinaryOp.Operator operator) {

        Expr predicateArray = createElementArray(bc, Predicate.class, left, right);

        MethodDesc combineMethod = switch (operator) {
            case AND -> CB_AND;
            case OR -> CB_OR;
            default -> throw new IllegalArgumentException("Expected AND or OR operator, got: " + operator);
        };

        return bc.invokeInterface(combineMethod, cb, predicateArray);
    }

    /** Generates cb.between(field, lowerBound, upperBound) for detected BETWEEN patterns. */
    private Expr generateBetweenPredicate(BlockCreator bc, Expr cb, Expr root, Expr capturedValues,
            PatternDetector.BetweenComponents between) {
        Expr fieldExpr = generateExpressionAsJpaExpression(bc, between.field(), cb, root, capturedValues);
        Expr lowerExpr = generateExpressionAsJpaExpression(bc, between.lowerBound(), cb, root, capturedValues);
        Expr upperExpr = generateExpressionAsJpaExpression(bc, between.upperBound(), cb, root, capturedValues);
        return bc.invokeInterface(CB_BETWEEN_EXPR, cb, fieldExpr, lowerExpr, upperExpr);
    }

    /** Wraps value as literal Expression. */
    @Override
    public Expr wrapAsLiteral(BlockCreator bc, Expr cb, Expr value) {
        return bc.invokeInterface(CB_LITERAL, cb, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Expr loadCapturedValue(BlockCreator bc,
            LambdaExpression.CapturedVariable capturedVar, Expr capturedValues) {
        Expr value = capturedValues.elem(capturedVar.index());
        Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
        // Use BlockCreator.cast() for type casting in Gizmo2
        return bc.cast(value, targetType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Expr loadAndWrapCapturedValue(BlockCreator bc, Expr cb,
            LambdaExpression.CapturedVariable capturedVar, Expr capturedValues) {
        Expr castedValue = loadCapturedValue(bc, capturedVar, capturedValues);
        return wrapAsLiteral(bc, cb, castedValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Expr loadDtoClass(BlockCreator bc, String internalClassName) {
        // Convert internal class name to fully qualified class name (replace / with .)
        String fqClassName = internalClassName.replace('/', '.');
        // Load the class at runtime using Class.forName()
        Expr classNameHandle = Const.of(fqClassName);
        return bc.invokeStatic(CLASS_FOR_NAME, classNameHandle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Expr wrapBooleanAsPredicateIfNeeded(BlockCreator bc, Expr cb,
            Expr path, Class<?> type) {
        if (isBooleanType(type)) {
            return bc.invokeInterface(CB_IS_TRUE, cb, path);
        }
        return path;
    }

    /** Generates JPA Predicate from bi-entity lambda (for join queries). */
    public Expr generateBiEntityPredicate(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr join,
            Expr capturedValues) {

        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, this);
        return builderRegistry.biEntityBuilder().generateBiEntityPredicate(ctx, expression);
    }

    /** Generates bi-entity JPA Predicate with subquery support. */
    public Expr generateBiEntityPredicateWithSubqueries(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr query,
            Expr root,
            Expr join,
            Expr capturedValues) {

        var ctx = new BiEntitySubqueryContext(bc, cb, query, root, join, capturedValues, this);
        return builderRegistry.biEntityBuilder().generateBiEntityPredicateWithSubqueries(ctx, expression);
    }

    /** Generates JPA Expression from bi-entity lambda. */
    public Expr generateBiEntityExpressionAsJpaExpression(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr join,
            Expr capturedValues) {

        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, this);
        return builderRegistry.biEntityBuilder().generateBiEntityExpressionAsJpaExpression(ctx, expression);
    }

    /** Generates JPA Selection from bi-entity projection. */
    public Expr generateBiEntityProjection(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr join,
            Expr capturedValues) {

        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, this);
        return builderRegistry.biEntityBuilder().generateBiEntityProjection(ctx, expression);
    }

    /** Generates JPA Predicate for HAVING clause in GROUP BY queries. */
    public Expr generateGroupPredicate(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues) {

        return builderRegistry.groupBuilder().generateGroupPredicate(bc, expression, cb, root, groupKeyExpr, capturedValues,
                this);
    }

    /** Generates JPA Expression for GROUP BY SELECT clause. */
    public Expr generateGroupSelectExpression(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues) {

        return builderRegistry.groupBuilder().generateGroupSelectExpression(bc, expression, cb, root, groupKeyExpr,
                capturedValues, this);
    }

    /** Generates JPA Expression for group ORDER BY clause. */
    public Expr generateGroupSortExpression(
            BlockCreator bc,
            LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues) {

        return builderRegistry.groupBuilder().generateGroupSortExpression(bc, expression, cb, root, groupKeyExpr,
                capturedValues, this);
    }

    /** Generates JPA multiselect array for Object[] projections in group context. */
    public Expr generateGroupArraySelections(
            BlockCreator bc,
            LambdaExpression.ArrayCreation arrayCreation,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues) {

        return builderRegistry.groupBuilder().generateGroupArraySelections(bc, arrayCreation, cb, root, groupKeyExpr,
                capturedValues, this);
    }
}
