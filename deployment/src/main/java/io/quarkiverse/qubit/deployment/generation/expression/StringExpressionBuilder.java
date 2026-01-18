package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_CONTAINS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_ENDS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_IS_EMPTY;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_LENGTH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_STARTS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_SUBSTRING;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TO_LOWER_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TO_UPPER_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TRIM;
import static io.quarkiverse.qubit.runtime.QubitConstants.SQL_LIKE_WILDCARD;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_PATTERN_METHOD_NAMES;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_TRANSFORMATION_METHODS;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_UTILITY_METHODS;

import java.util.List;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;

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
public enum StringExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Determines the string operation type for a method call. */
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

    /** Categories of string operations. */
    public enum StringOperationType {
        TRANSFORMATION,  // toLowerCase, toUpperCase, trim
        PATTERN,         // startsWith, endsWith, contains
        SUBSTRING,       // substring
        UTILITY          // equals, length, isEmpty
    }

    /** Generates bytecode for String transformations: toLowerCase, toUpperCase, trim. */
    public BuilderResult buildStringTransformation(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression) {

        MethodDescriptor transformMethod = switch (methodCall.methodName()) {
            case METHOD_TO_LOWER_CASE -> MethodDescriptors.CB_LOWER;
            case METHOD_TO_UPPER_CASE -> MethodDescriptors.CB_UPPER;
            case METHOD_TRIM -> MethodDescriptors.CB_TRIM;
            default -> null;
        };

        if (transformMethod == null) {
            return BuilderResult.notApplicable();
        }

        ResultHandle result = method.invokeInterfaceMethod(transformMethod, cb, fieldExpression);
        return BuilderResult.success(result);
    }

    /** Generates bytecode for LIKE patterns: startsWith, endsWith, contains. */
    public BuilderResult buildStringPattern(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        String methodName = methodCall.methodName();
        if (!STRING_PATTERN_METHOD_NAMES.contains(methodName)) {
            return BuilderResult.notApplicable();
        }

        // Determine wildcard placement: startsWith = suffix, endsWith = prefix, contains = both
        boolean addPrefix = METHOD_ENDS_WITH.equals(methodName) || METHOD_CONTAINS.equals(methodName);
        boolean addSuffix = METHOD_STARTS_WITH.equals(methodName) || METHOD_CONTAINS.equals(methodName);
        ResultHandle pattern = buildWildcardPattern(method, argument, addPrefix, addSuffix);

        ResultHandle result = method.invokeInterfaceMethod(MethodDescriptors.CB_LIKE_STRING, cb, fieldExpression, pattern);
        return BuilderResult.success(result);
    }

    /** Builds SQL LIKE wildcard pattern by adding '%' prefix and/or suffix. */
    private ResultHandle buildWildcardPattern(
            MethodCreator method,
            ResultHandle argument,
            boolean addPrefix,
            boolean addSuffix) {

        ResultHandle result = argument;
        if (addPrefix) {
            ResultHandle percent = method.load(SQL_LIKE_WILDCARD);
            result = method.invokeVirtualMethod(MethodDescriptors.STRING_CONCAT, percent, result);
        }
        if (addSuffix) {
            ResultHandle percent = method.load(SQL_LIKE_WILDCARD);
            result = method.invokeVirtualMethod(MethodDescriptors.STRING_CONCAT, result, percent);
        }
        return result;
    }

    /** Generates bytecode for substring with 0-based to 1-based index conversion. */
    public BuilderResult buildStringSubstring(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            List<ResultHandle> arguments) {

        if (!methodCall.methodName().equals(METHOD_SUBSTRING)) {
            return BuilderResult.notApplicable();
        }

        if (arguments.size() == 1) {
            // substring(start)
            ResultHandle startJava = arguments.get(0);
            ResultHandle startJpa = addOneToExpression(method, cb, startJava);

            ResultHandle result = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_SUBSTRING_2,
                    cb, fieldExpression, startJpa);
            return BuilderResult.success(result);
        } else if (arguments.size() == 2) {
            // substring(start, end)
            ResultHandle startJava = arguments.get(0);
            ResultHandle endJava = arguments.get(1);

            ResultHandle startJpa = addOneToExpression(method, cb, startJava);
            ResultHandle length = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_DIFF,
                    cb, endJava, startJava);

            ResultHandle result = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_SUBSTRING_3,
                    cb, fieldExpression, startJpa, length);
            return BuilderResult.success(result);
        }

        return BuilderResult.notApplicable();
    }

    /** Generates bytecode for utility methods: equals, length, isEmpty. */
    public BuilderResult buildStringUtility(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        String methodName = methodCall.methodName();

        if (methodName.equals(METHOD_EQUALS)) {
            if (argument == null) {
                return BuilderResult.notApplicable();
            }
            ResultHandle result = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_EQUAL,
                    cb, fieldExpression, argument);
            return BuilderResult.success(result);
        }

        if (methodName.equals(METHOD_LENGTH) && methodCall.returnType() == int.class) {
            ResultHandle result = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_LENGTH,
                    cb, fieldExpression);
            return BuilderResult.success(result);
        }

        if (methodName.equals(METHOD_IS_EMPTY)) {
            ResultHandle lengthExpression = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_LENGTH,
                    cb, fieldExpression);

            ResultHandle zeroValue = method.load(0);
            ResultHandle zeroLiteral = wrapAsLiteral(method, cb, zeroValue);

            ResultHandle result = method.invokeInterfaceMethod(
                    MethodDescriptors.CB_EQUAL_EXPR,
                    cb, lengthExpression, zeroLiteral);
            return BuilderResult.success(result);
        }

        return BuilderResult.notApplicable();
    }

    /** Wraps value as literal Expression. */
    private ResultHandle wrapAsLiteral(MethodCreator method, ResultHandle cb, ResultHandle value) {
        return method.invokeInterfaceMethod(
                MethodDescriptors.CB_LITERAL,
                cb, value);
    }

    /** Adds 1 to expression for 0-based to 1-based index conversion. */
    private ResultHandle addOneToExpression(MethodCreator method, ResultHandle cb, ResultHandle expression) {
        ResultHandle one = method.load(1);
        ResultHandle oneLiteral = wrapAsLiteral(method, cb, one);
        return method.invokeInterfaceMethod(
                MethodDescriptors.CB_SUM_BINARY,
                cb, expression, oneLiteral);
    }

}
