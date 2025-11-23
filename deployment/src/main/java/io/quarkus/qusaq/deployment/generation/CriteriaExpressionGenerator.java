package io.quarkus.qusaq.deployment.generation;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_ADD;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_CONTAINS;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_DIVIDE;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_ENDS_WITH;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_EQUALS;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_IS_AFTER;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_IS_BEFORE;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_IS_EQUAL;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_MULTIPLY;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_OF;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_STARTS_WITH;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_SUBSTRING;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_SUBTRACT;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_AND;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_EQUAL;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_IS_FALSE;
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
import io.quarkus.qusaq.deployment.analysis.PatternDetector;
import io.quarkus.qusaq.deployment.generation.builders.ArithmeticExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.BigDecimalExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.ComparisonExpressionBuilder;
import io.quarkus.qusaq.deployment.generation.builders.StringExpressionBuilder;
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
    private static final Set<String> TEMPORAL_COMPARISON_METHOD_NAMES = Set.of(
        METHOD_IS_AFTER, METHOD_IS_BEFORE, METHOD_IS_EQUAL
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

        if (expression instanceof LambdaExpression.BinaryOp binOp) {
            return generateBinaryOperation(method, binOp, cb, root, capturedValues);
        } else if (expression instanceof LambdaExpression.UnaryOp unOp) {
            return generateUnaryOperation(method, unOp, cb, root, capturedValues);
        } else if (expression instanceof LambdaExpression.FieldAccess field) {
            ResultHandle path = generateFieldAccess(method, field, root);
            if (isBooleanType(field.fieldType())) {
                return method.invokeInterfaceMethod(methodDescriptor(CriteriaBuilder.class, CB_IS_TRUE, Predicate.class, Expression.class), cb, path);
            }
            return path;
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            return generateMethodCall(method, methodCall, cb, root, capturedValues);
        }

        return null;
    }

    /**
     * Generates raw value from lambda expression.
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

        if (expression instanceof LambdaExpression.FieldAccess field) {
            return generateFieldAccess(method, field, root);
        } else if (expression instanceof LambdaExpression.Constant constant) {
            return generateConstant(method, constant);
        } else if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
            ResultHandle index = method.load(capturedVar.index());
            ResultHandle value = method.readArrayValue(capturedValues, index);
            Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
            return method.checkCast(value, targetType);
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            return generateMethodCall(method, methodCall, cb, root, capturedValues);
        }

        return null;
    }

    /**
     * Generates JPA Expression from lambda expression.
     * Phase 3: Added Parameter handling for identity sort functions.
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

        if (expression instanceof LambdaExpression.FieldAccess field) {
            return generateFieldAccess(method, field, root);
        } else if (expression instanceof LambdaExpression.Constant constant) {
            ResultHandle constantValue = generateConstant(method, constant);
            return wrapAsLiteral(method, cb, constantValue);
        } else if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
            ResultHandle index = method.load(capturedVar.index());
            ResultHandle value = method.readArrayValue(capturedValues, index);
            Class<?> targetType = TypeConverter.getBoxedType(capturedVar.type());
            ResultHandle castedValue = method.checkCast(value, targetType);
            return wrapAsLiteral(method, cb, castedValue);
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            return generateMethodCall(method, methodCall, cb, root, capturedValues);
        } else if (expression instanceof LambdaExpression.BinaryOp binOp) {
            return generateBinaryOperation(method, binOp, cb, root, capturedValues);
        } else if (expression instanceof LambdaExpression.ConstructorCall constructorCall) {
            return generateConstructorCall(method, constructorCall, cb, root, capturedValues);
        } else if (expression instanceof LambdaExpression.Parameter) {
            // Phase 3: Parameter expressions occur in identity sort functions like (String s) -> s
            // These cannot be directly converted to JPA expressions - return null to signal
            // to caller that special handling is needed
            return null;
        }

        return null;
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

    /** Generates constant value bytecode. */
    public ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        Object value = constant.value();

        if (value == null) {
            return method.loadNull();
        } else if (value instanceof String s) {
            return method.load(s);
        } else if (value instanceof Integer i) {
            return method.load(i);
        } else if (value instanceof Long l) {
            return method.load(l);
        } else if (value instanceof Boolean b) {
            return method.load(b);
        } else if (value instanceof Double d) {
            return method.load(d);
        } else if (value instanceof Float f) {
            return method.load(f);
        } else if (value instanceof BigDecimal bd) {
            ResultHandle bdString = method.load(bd.toString());
            return method.newInstance(constructorDescriptor(BigDecimal.class, String.class), bdString);
        } else if (value instanceof LocalDate ld) {
            ResultHandle year = method.load(ld.getYear());
            ResultHandle month = method.load(ld.getMonthValue());
            ResultHandle day = method.load(ld.getDayOfMonth());
            return method.invokeStaticMethod(methodDescriptor(LocalDate.class, METHOD_OF, LocalDate.class, int.class, int.class, int.class), year, month, day);
        } else if (value instanceof LocalDateTime ldt) {
            ResultHandle year = method.load(ldt.getYear());
            ResultHandle month = method.load(ldt.getMonthValue());
            ResultHandle day = method.load(ldt.getDayOfMonth());
            ResultHandle hour = method.load(ldt.getHour());
            ResultHandle minute = method.load(ldt.getMinute());
            return method.invokeStaticMethod(methodDescriptor(LocalDateTime.class, METHOD_OF, LocalDateTime.class, int.class, int.class, int.class, int.class, int.class), year, month, day, hour, minute);
        } else if (value instanceof LocalTime lt) {
            ResultHandle hour = method.load(lt.getHour());
            ResultHandle minute = method.load(lt.getMinute());
            return method.invokeStaticMethod(methodDescriptor(LocalTime.class, METHOD_OF, LocalTime.class, int.class, int.class), hour, minute);
        } else {
            return method.loadNull();
        }
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
        if (expr instanceof LambdaExpression.FieldAccess field) {
            return field.fieldType() == String.class;
        } else if (expr instanceof LambdaExpression.Constant constant) {
            return constant.value() instanceof String;
        } else if (expr instanceof LambdaExpression.CapturedVariable capturedVar) {
            return capturedVar.type() == String.class;
        } else if (expr instanceof LambdaExpression.BinaryOp binOp) {
            // Recursive: concatenation of concatenations
            return isStringConcatenation(binOp);
        }
        return false;
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
}
