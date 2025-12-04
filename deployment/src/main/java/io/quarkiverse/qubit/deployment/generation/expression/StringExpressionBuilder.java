package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.runtime.QubitConstants.CB_DIFF;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_EQUAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_LENGTH;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_LIKE;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_LITERAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_LOWER;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_SUBSTRING;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_SUM;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_TRIM;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_UPPER;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_ENDS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_IS_EMPTY;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_LENGTH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_STARTS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_SUBSTRING;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TO_LOWER_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TO_UPPER_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TRIM;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_CONCAT;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_PATTERN_METHOD_NAMES;

import java.util.List;
import java.util.Set;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Builds JPA Criteria API expressions for String operations.
 *
 * <p>Supported operations:
 * <ul>
 *   <li><b>Transformations:</b> toLowerCase(), toUpperCase(), trim()</li>
 *   <li><b>Pattern Matching:</b> startsWith(), endsWith(), contains() → LIKE</li>
 *   <li><b>Substring:</b> substring(start), substring(start, end) with 0-to-1 index conversion</li>
 *   <li><b>Utility:</b> equals(), length(), isEmpty()</li>
 * </ul>
 *
 * <p><b>Note:</b> Java's substring() uses 0-based indexing, but JPA uses 1-based.
 * This builder automatically adds 1 to the start index.
 */
public class StringExpressionBuilder implements ExpressionBuilder {

    /**
     * String transformation methods.
     */
    private static final Set<String> STRING_TRANSFORMATION_METHODS = Set.of(
        METHOD_TO_LOWER_CASE, METHOD_TO_UPPER_CASE, METHOD_TRIM
    );

    /**
     * String utility methods.
     */
    private static final Set<String> STRING_UTILITY_METHODS = Set.of(
        METHOD_EQUALS, METHOD_LENGTH, METHOD_IS_EMPTY
    );

    /**
     * Determines the string operation type for a method call.
     *
     * @param methodCall the method call expression
     * @return the operation type, or null if not a string operation
     */
    public StringOperationType getOperationType(LambdaExpression.MethodCall methodCall) {
        String methodName = methodCall.methodName();

        if (STRING_TRANSFORMATION_METHODS.contains(methodName)) {
            return StringOperationType.TRANSFORMATION;
        }
        if (STRING_PATTERN_METHOD_NAMES.contains(methodName)) {
            return StringOperationType.PATTERN;
        }
        if (methodName.equals(METHOD_SUBSTRING)) {
            return StringOperationType.SUBSTRING;
        }
        if (STRING_UTILITY_METHODS.contains(methodName)) {
            return StringOperationType.UTILITY;
        }

        return null;
    }

    /**
     * Categories of string operations.
     */
    public enum StringOperationType {
        TRANSFORMATION,  // toLowerCase, toUpperCase, trim
        PATTERN,         // startsWith, endsWith, contains
        SUBSTRING,       // substring
        UTILITY          // equals, length, isEmpty
    }

    /**
     * Generates bytecode for String transformations: toLowerCase, toUpperCase, trim.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @return the transformation Expression, or null if not a recognized transformation
     */
    public ResultHandle buildStringTransformation(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression) {

        MethodDescriptor transformMethod = switch (methodCall.methodName()) {
            case METHOD_TO_LOWER_CASE -> md(CB_LOWER);
            case METHOD_TO_UPPER_CASE -> md(CB_UPPER);
            case METHOD_TRIM -> md(CB_TRIM);
            default -> null;
        };

        if (transformMethod == null) {
            return null;
        }

        return method.invokeInterfaceMethod(transformMethod, cb, fieldExpression);
    }

    /**
     * Generates bytecode for LIKE patterns: startsWith, endsWith, contains.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @param argument the pattern argument
     * @return the LIKE Predicate, or null if not a pattern method
     */
    public ResultHandle buildStringPattern(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        if (!STRING_PATTERN_METHOD_NAMES.contains(methodCall.methodName())) {
            return null;
        }

        ResultHandle pattern;
        if (methodCall.methodName().equals(METHOD_STARTS_WITH)) {
            ResultHandle percent = method.load("%");
            pattern = method.invokeVirtualMethod(md(STRING_CONCAT), argument, percent);
        } else if (methodCall.methodName().equals(METHOD_ENDS_WITH)) {
            ResultHandle percent = method.load("%");
            pattern = method.invokeVirtualMethod(md(STRING_CONCAT), percent, argument);
        } else {
            // contains
            ResultHandle percentPrefix = method.load("%");
            ResultHandle withPrefix = method.invokeVirtualMethod(md(STRING_CONCAT), percentPrefix, argument);
            ResultHandle percentSuffix = method.load("%");
            pattern = method.invokeVirtualMethod(md(STRING_CONCAT), withPrefix, percentSuffix);
        }

        return method.invokeInterfaceMethod(md(CB_LIKE), cb, fieldExpression, pattern);
    }

    /**
     * Generates bytecode for substring with 0-based to 1-based index conversion.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @param arguments the argument expressions (start or start+end)
     * @return the substring Expression, or null if not a substring method or wrong arg count
     */
    public ResultHandle buildStringSubstring(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            List<ResultHandle> arguments) {

        if (!methodCall.methodName().equals(METHOD_SUBSTRING)) {
            return null;
        }

        if (arguments.size() == 1) {
            // substring(start)
            ResultHandle startJava = arguments.get(0);
            ResultHandle startJpa = addOneToExpression(method, cb, startJava);

            return method.invokeInterfaceMethod(
                    md(CB_SUBSTRING, Expression.class, Expression.class),
                    cb, fieldExpression, startJpa);
        } else if (arguments.size() == 2) {
            // substring(start, end)
            ResultHandle startJava = arguments.get(0);
            ResultHandle endJava = arguments.get(1);

            ResultHandle startJpa = addOneToExpression(method, cb, startJava);
            ResultHandle length = method.invokeInterfaceMethod(
                    md(CB_DIFF),
                    cb, endJava, startJava);

            return method.invokeInterfaceMethod(
                    md(CB_SUBSTRING, Expression.class, Expression.class, Expression.class),
                    cb, fieldExpression, startJpa, length);
        }

        return null;
    }

    /**
     * Generates bytecode for utility methods: equals, length, isEmpty.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @param argument the argument (for equals), or null for length/isEmpty
     * @return the Predicate or Expression, or null if not a recognized utility method
     */
    public ResultHandle buildStringUtility(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        String methodName = methodCall.methodName();

        if (methodName.equals(METHOD_EQUALS)) {
            if (argument == null) {
                return null;
            }
            return method.invokeInterfaceMethod(
                    md(CB_EQUAL, Expression.class, Object.class),
                    cb, fieldExpression, argument);
        }

        if (methodName.equals(METHOD_LENGTH) && methodCall.returnType() == int.class) {
            return method.invokeInterfaceMethod(
                    md(CB_LENGTH),
                    cb, fieldExpression);
        }

        if (methodName.equals(METHOD_IS_EMPTY)) {
            ResultHandle lengthExpression = method.invokeInterfaceMethod(
                    md(CB_LENGTH),
                    cb, fieldExpression);

            ResultHandle zeroValue = method.load(0);
            ResultHandle zeroLiteral = wrapAsLiteral(method, cb, zeroValue);

            return method.invokeInterfaceMethod(
                    md(CB_EQUAL, Expression.class, Expression.class),
                    cb, lengthExpression, zeroLiteral);
        }

        return null;
    }

    /**
     * Wraps value as literal Expression.
     */
    private ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value) {
        return method.invokeInterfaceMethod(
                md(CB_LITERAL, Object.class),
                cb, value);
    }

    /**
     * Adds 1 to expression for 0-based to 1-based index conversion.
     */
    private ResultHandle addOneToExpression(MethodCreator method, ResultHandle cb, ResultHandle expression) {
        ResultHandle one = method.load(1);
        ResultHandle oneLiteral = wrapAsLiteral(method, cb, one);
        return method.invokeInterfaceMethod(
                md(CB_SUM),
                cb, expression, oneLiteral);
    }

    /**
     * Creates MethodDescriptor for CriteriaBuilder methods.
     */
    private static MethodDescriptor md(String methodName, Class<?>... params) {
        if (methodName.equals(CB_LOWER) || methodName.equals(CB_UPPER) || methodName.equals(CB_TRIM)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Expression.class,
                    Expression.class);
        }
        if (methodName.equals(CB_LIKE)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Predicate.class,
                    Expression.class,
                    String.class);
        }
        if (methodName.equals(CB_SUBSTRING)) {
            if (params.length == 2) {
                // substring(expr, start)
                return MethodDescriptor.ofMethod(
                        CriteriaBuilder.class,
                        methodName,
                        Expression.class,
                        Expression.class,
                        Expression.class);
            } else {
                // substring(expr, start, length)
                return MethodDescriptor.ofMethod(
                        CriteriaBuilder.class,
                        methodName,
                        Expression.class,
                        Expression.class,
                        Expression.class,
                        Expression.class);
            }
        }
        if (methodName.equals(CB_LENGTH)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Expression.class,
                    Expression.class);
        }
        if (methodName.equals(CB_EQUAL) && params.length == 2) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Predicate.class,
                    params[0],
                    params[1]);
        }
        if (methodName.equals(CB_LITERAL)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Expression.class,
                    Object.class);
        }
        if (methodName.equals(CB_SUM) || methodName.equals(CB_DIFF)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Expression.class,
                    Expression.class,
                    Expression.class);
        }
        if (methodName.equals(STRING_CONCAT)) {
            return MethodDescriptor.ofMethod(
                    String.class,
                    methodName,
                    String.class,
                    String.class);
        }
        throw new IllegalArgumentException("Unknown method: " + methodName);
    }
}
