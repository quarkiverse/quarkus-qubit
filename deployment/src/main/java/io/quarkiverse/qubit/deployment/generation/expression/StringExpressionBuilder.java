package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_CONTAINS;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_ENDS_WITH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_BLANK;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_EMPTY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_LENGTH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_STARTS_WITH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_INDEX_OF;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_REPLACE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUBSTRING;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_TO_LOWER_CASE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_TO_UPPER_CASE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_TRIM;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.SQL_LIKE_WILDCARD;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.STRING_PATTERN_METHOD_NAMES;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.STRING_TRANSFORMATION_METHODS;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.STRING_UTILITY_METHODS;

import java.util.List;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Builds JPA Criteria API expressions for String operations.
 *
 * <p>
 * Supported operations:
 * <ul>
 * <li><b>Transformations:</b> toLowerCase(), toUpperCase(), trim()</li>
 * <li><b>Pattern Matching:</b> startsWith(), endsWith(), contains() → LIKE</li>
 * <li><b>Substring:</b> substring(start), substring(start, end) with 0-to-1 index conversion</li>
 * <li><b>Utility:</b> equals(), length(), isEmpty()</li>
 * </ul>
 *
 * <p>
 * <b>Note:</b> Java's substring() uses 0-based indexing, but JPA uses 1-based.
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
        if (methodName.equals(METHOD_INDEX_OF)) {
            return StringOperationType.INDEX_OF;
        }
        if (methodName.equals(METHOD_REPLACE)) {
            return StringOperationType.REPLACE;
        }
        if (STRING_UTILITY_METHODS.contains(methodName)) {
            return StringOperationType.UTILITY;
        }

        return null;
    }

    /** Categories of string operations. */
    public enum StringOperationType {
        TRANSFORMATION, // toLowerCase, toUpperCase, trim
        PATTERN, // startsWith, endsWith, contains
        SUBSTRING, // substring
        INDEX_OF, // indexOf
        REPLACE, // replace
        UTILITY // equals, length, isEmpty
    }

    /** Generates bytecode for String transformations: toLowerCase, toUpperCase, trim. */
    public BuilderResult buildStringTransformation(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression) {

        MethodDesc transformMethod = switch (methodCall.methodName()) {
            case METHOD_TO_LOWER_CASE -> MethodDescriptors.CB_LOWER;
            case METHOD_TO_UPPER_CASE -> MethodDescriptors.CB_UPPER;
            case METHOD_TRIM -> MethodDescriptors.CB_TRIM;
            default -> null;
        };

        if (transformMethod == null) {
            return BuilderResult.notApplicable();
        }

        Expr result = bc.invokeInterface(transformMethod, cb, fieldExpression);
        return BuilderResult.success(result);
    }

    /** Generates bytecode for LIKE patterns: startsWith, endsWith, contains. */
    public BuilderResult buildStringPattern(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression,
            Expr argument) {

        String methodName = methodCall.methodName();
        if (!STRING_PATTERN_METHOD_NAMES.contains(methodName)) {
            return BuilderResult.notApplicable();
        }

        // Determine wildcard placement: startsWith = suffix, endsWith = prefix, contains = both
        boolean addPrefix = METHOD_ENDS_WITH.equals(methodName) || METHOD_CONTAINS.equals(methodName);
        boolean addSuffix = METHOD_STARTS_WITH.equals(methodName) || METHOD_CONTAINS.equals(methodName);
        Expr pattern = buildWildcardPattern(bc, argument, addPrefix, addSuffix);

        Expr result = bc.invokeInterface(MethodDescriptors.CB_LIKE_STRING, cb, fieldExpression, pattern);
        return BuilderResult.success(result);
    }

    /** Builds SQL LIKE wildcard pattern by adding '%' prefix and/or suffix. */
    private Expr buildWildcardPattern(
            BlockCreator bc,
            Expr argument,
            boolean addPrefix,
            boolean addSuffix) {

        // Use LocalVar when argument may be used in multiple operations (Gizmo2 requirement)
        LocalVar result = bc.localVar("patternResult", argument);
        if (addPrefix) {
            Expr percent = Const.of(SQL_LIKE_WILDCARD);
            bc.set(result, bc.invokeVirtual(MethodDescriptors.STRING_CONCAT, percent, result));
        }
        if (addSuffix) {
            Expr percent = Const.of(SQL_LIKE_WILDCARD);
            bc.set(result, bc.invokeVirtual(MethodDescriptors.STRING_CONCAT, result, percent));
        }
        return result;
    }

    /** Generates bytecode for substring with 0-based to 1-based index conversion. */
    public BuilderResult buildStringSubstring(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression,
            List<Expr> arguments) {

        if (!methodCall.methodName().equals(METHOD_SUBSTRING)) {
            return BuilderResult.notApplicable();
        }

        // cb and fieldExpression are expected to be LocalVar/ParamVar from caller (Gizmo2 requirement)
        // The caller must ensure these are stored in LocalVars if using raw Expr

        if (arguments.size() == 1) {
            // substring(start)
            Expr startJava = arguments.getFirst();
            Expr startJpa = addOneToExpression(bc, cb, startJava);

            Expr result = bc.invokeInterface(
                    MethodDescriptors.CB_SUBSTRING_2,
                    cb, fieldExpression, startJpa);
            return BuilderResult.success(result);
        } else if (arguments.size() == 2) {
            // substring(start, end)
            // Arguments are expected to be LocalVars from caller (Gizmo2 requirement)
            Expr startJava = arguments.getFirst();
            Expr endJava = arguments.get(1);

            Expr startJpa = addOneToExpression(bc, cb, startJava);
            Expr length = bc.invokeInterface(
                    MethodDescriptors.CB_DIFF,
                    cb, endJava, startJava);

            Expr result = bc.invokeInterface(
                    MethodDescriptors.CB_SUBSTRING_3,
                    cb, fieldExpression, startJpa, length);
            return BuilderResult.success(result);
        }

        return BuilderResult.notApplicable();
    }

    /**
     * Generates bytecode for indexOf with 1-based to 0-based result conversion.
     *
     * <p>
     * JPA LOCATE returns 1-based positions (1 = first char, 0 = not found).
     * Java indexOf returns 0-based positions (0 = first char, -1 = not found).
     * Conversion: {@code cb.diff(cb.locate(...), cb.literal(1))}
     * This correctly maps: LOCATE 0 (not found) -> -1, LOCATE 1 -> 0, LOCATE 2 -> 1, etc.
     */
    public BuilderResult buildStringIndexOf(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression,
            List<Expr> arguments) {

        if (!methodCall.methodName().equals(METHOD_INDEX_OF)) {
            return BuilderResult.notApplicable();
        }

        Expr locateResult;

        if (arguments.size() == 1) {
            // indexOf(String) -> cb.locate(field, pattern)
            Expr pattern = arguments.getFirst();
            locateResult = bc.invokeInterface(MethodDescriptors.CB_LOCATE_2, cb, fieldExpression, pattern);
        } else if (arguments.size() == 2) {
            // indexOf(String, int fromIndex) -> cb.locate(field, pattern, fromIndex + 1)
            Expr pattern = arguments.getFirst();
            Expr fromIndex = arguments.get(1);
            Expr fromJpa = addOneToExpression(bc, cb, fromIndex);
            locateResult = bc.invokeInterface(MethodDescriptors.CB_LOCATE_3, cb, fieldExpression, pattern, fromJpa);
        } else {
            return BuilderResult.notApplicable();
        }

        // Subtract 1 from result: LOCATE returns 1-based, indexOf expects 0-based
        // LOCATE returns 0 for not-found -> 0 - 1 = -1 (matches Java indexOf not-found)
        Expr one = Const.of(1);
        LocalVar oneLiteral = bc.localVar("indexOfOneLiteral", wrapAsLiteral(bc, cb, one));
        Expr result = bc.invokeInterface(MethodDescriptors.CB_DIFF, cb, locateResult, oneLiteral);

        return BuilderResult.success(result);
    }

    /** Generates bytecode for utility methods: equals, length, isEmpty, isBlank. */
    public BuilderResult buildStringUtility(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression,
            Expr argument) {

        String methodName = methodCall.methodName();

        if (methodName.equals(METHOD_EQUALS)) {
            if (argument == null) {
                return BuilderResult.notApplicable();
            }
            Expr result = bc.invokeInterface(
                    MethodDescriptors.CB_EQUAL,
                    cb, fieldExpression, argument);
            return BuilderResult.success(result);
        }

        if (methodName.equals(METHOD_LENGTH) && methodCall.returnType() == int.class) {
            Expr result = bc.invokeInterface(
                    MethodDescriptors.CB_LENGTH,
                    cb, fieldExpression);
            return BuilderResult.success(result);
        }

        if (methodName.equals(METHOD_IS_EMPTY)) {
            // cb is expected to be a LocalVar/ParamVar from caller (Gizmo2 requirement)
            // The caller must ensure cb is stored in a LocalVar if using raw Expr
            Expr lengthExpression = bc.invokeInterface(
                    MethodDescriptors.CB_LENGTH,
                    cb, fieldExpression);

            Expr zeroValue = Const.of(0);
            Expr zeroLiteral = wrapAsLiteral(bc, cb, zeroValue);

            Expr result = bc.invokeInterface(
                    MethodDescriptors.CB_EQUAL_EXPR,
                    cb, lengthExpression, zeroLiteral);
            return BuilderResult.success(result);
        }

        if (methodName.equals(METHOD_IS_BLANK)) {
            // isBlank() = TRIM(field) = '' (empty string after trimming whitespace)
            // Generate: cb.equal(cb.trim(fieldExpression), cb.literal(""))
            Expr trimmedExpression = bc.invokeInterface(
                    MethodDescriptors.CB_TRIM,
                    cb, fieldExpression);

            Expr emptyString = Const.of("");
            Expr emptyLiteral = wrapAsLiteral(bc, cb, emptyString);

            Expr result = bc.invokeInterface(
                    MethodDescriptors.CB_EQUAL_EXPR,
                    cb, trimmedExpression, emptyLiteral);
            return BuilderResult.success(result);
        }

        return BuilderResult.notApplicable();
    }

    /** Generates bytecode for cb.replace(field, target, replacement). */
    public BuilderResult buildStringReplace(
            BlockCreator bc,
            Expr cb,
            Expr fieldExpression,
            Expr target,
            Expr replacement) {
        Expr result = bc.invokeInterface(MethodDescriptors.CB_REPLACE, cb, fieldExpression, target, replacement);
        return BuilderResult.success(result);
    }

    /** Wraps value as literal Expression. */
    private Expr wrapAsLiteral(BlockCreator bc, Expr cb, Expr value) {
        return bc.invokeInterface(
                MethodDescriptors.CB_LITERAL,
                cb, value);
    }

    /** Adds 1 to expression for 0-based to 1-based index conversion. */
    private Expr addOneToExpression(BlockCreator bc, Expr cb, Expr expression) {
        Expr one = Const.of(1);
        // Store literal result in LocalVar to ensure proper bytecode ordering (Gizmo2 requirement)
        LocalVar oneLiteral = bc.localVar("oneLiteral", wrapAsLiteral(bc, cb, one));
        return bc.invokeInterface(
                MethodDescriptors.CB_SUM_BINARY,
                cb, expression, oneLiteral);
    }

}
