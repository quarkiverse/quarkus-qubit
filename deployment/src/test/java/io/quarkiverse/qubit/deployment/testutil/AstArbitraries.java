package io.quarkiverse.qubit.deployment.testutil;

import java.math.BigDecimal;
import java.util.List;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import net.jqwik.api.*;

/**
 * Custom jqwik Arbitraries for generating random AST nodes.
 *
 * <p>
 * This class provides generators for property-based testing of AST transformations
 * and operations. The generators produce bounded expression trees to avoid infinite
 * recursion while still providing meaningful test coverage.
 *
 * <p>
 * <strong>Usage:</strong>
 *
 * <pre>{@code
 * @Property
 * void someProperty(@ForAll("leafExpressions") LambdaExpression expr) {
 *     // Test with randomly generated expression
 * }
 * }</pre>
 *
 * <p>
 * <strong>Design Rationale:</strong>
 * Property-based testing generates many random inputs to verify invariants that should
 * hold for ANY valid input. This complements example-based tests by finding edge cases
 * that might not be manually specified.
 *
 * @see LambdaExpression
 */
public class AstArbitraries {

    // ======================================================================
    // Common Types for AST Nodes
    // ======================================================================

    private static final Class<?>[] COMMON_TYPES = {
            String.class, Integer.class, Long.class, Double.class,
            Boolean.class, BigDecimal.class, Object.class
    };

    // Primitive types kept for potential future use
    // private static final Class<?>[] PRIMITIVE_TYPES = {
    //         int.class, long.class, double.class, boolean.class, float.class
    // };

    /**
     * Arbitrary for common Java types used in AST nodes.
     */
    @Provide
    public static Arbitrary<Class<?>> types() {
        return Arbitraries.of(COMMON_TYPES);
    }

    /**
     * Arbitrary for field names (valid Java identifiers).
     */
    @Provide
    public static Arbitrary<String> fieldNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> Character.toLowerCase(s.charAt(0)) + s.substring(1));
    }

    // ======================================================================
    // Leaf Expressions (No Children)
    // ======================================================================

    /**
     * Arbitrary for Constant expressions with various value types.
     */
    @Provide
    public static Arbitrary<Constant> constants() {
        return Arbitraries.oneOf(
                // String constants
                Arbitraries.strings().ofMaxLength(50)
                        .map(s -> new Constant(s, String.class)),
                // Integer constants
                Arbitraries.integers()
                        .map(i -> new Constant(i, Integer.class)),
                // Long constants
                Arbitraries.longs()
                        .map(l -> new Constant(l, Long.class)),
                // Double constants
                Arbitraries.doubles().between(-1e6, 1e6)
                        .map(d -> new Constant(d, Double.class)),
                // Boolean constants
                Arbitraries.of(Constant.TRUE, Constant.FALSE),
                // Null constants
                types().map(t -> new Constant(null, t)));
    }

    /**
     * Arbitrary for FieldAccess expressions.
     */
    @Provide
    public static Arbitrary<FieldAccess> fieldAccesses() {
        return Combinators.combine(fieldNames(), types())
                .as(FieldAccess::new);
    }

    /**
     * Arbitrary for CapturedVariable expressions.
     * Index is bounded to realistic values (0-9).
     */
    @Provide
    public static Arbitrary<CapturedVariable> capturedVariables() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 9),
                types()).as(CapturedVariable::new);
    }

    /**
     * Arbitrary for Parameter expressions.
     */
    @Provide
    public static Arbitrary<Parameter> parameters() {
        return Combinators.combine(
                fieldNames(),
                types(),
                Arbitraries.integers().between(0, 5)).as(Parameter::new);
    }

    /**
     * Arbitrary for NullLiteral expressions.
     */
    @Provide
    public static Arbitrary<NullLiteral> nullLiterals() {
        return types().map(NullLiteral::new);
    }

    /**
     * Arbitrary for any leaf expression (no children).
     */
    @Provide
    public static Arbitrary<LambdaExpression> leafExpressions() {
        return Arbitraries.oneOf(
                constants().map(c -> c),
                fieldAccesses().map(f -> f),
                capturedVariables().map(c -> c),
                parameters().map(p -> p),
                nullLiterals().map(n -> n));
    }

    // ======================================================================
    // Binary and Unary Operators
    // ======================================================================

    /**
     * Arbitrary for BinaryOp.Operator values.
     */
    @Provide
    public static Arbitrary<BinaryOp.Operator> binaryOperators() {
        return Arbitraries.of(BinaryOp.Operator.values());
    }

    /**
     * Arbitrary for comparison operators only.
     */
    @Provide
    public static Arbitrary<BinaryOp.Operator> comparisonOperators() {
        return Arbitraries.of(
                BinaryOp.Operator.EQ, BinaryOp.Operator.NE,
                BinaryOp.Operator.LT, BinaryOp.Operator.LE,
                BinaryOp.Operator.GT, BinaryOp.Operator.GE);
    }

    /**
     * Arbitrary for logical operators only.
     */
    @Provide
    public static Arbitrary<BinaryOp.Operator> logicalOperators() {
        return Arbitraries.of(BinaryOp.Operator.AND, BinaryOp.Operator.OR);
    }

    /**
     * Arbitrary for arithmetic operators only.
     */
    @Provide
    public static Arbitrary<BinaryOp.Operator> arithmeticOperators() {
        return Arbitraries.of(
                BinaryOp.Operator.ADD, BinaryOp.Operator.SUB,
                BinaryOp.Operator.MUL, BinaryOp.Operator.DIV,
                BinaryOp.Operator.MOD);
    }

    /**
     * Arbitrary for UnaryOp.Operator values.
     */
    @Provide
    public static Arbitrary<UnaryOp.Operator> unaryOperators() {
        return Arbitraries.of(UnaryOp.Operator.values());
    }

    // ======================================================================
    // Composite Expressions (Bounded Depth)
    // ======================================================================

    /**
     * Arbitrary for BinaryOp with leaf children (depth 1).
     */
    @Provide
    public static Arbitrary<BinaryOp> shallowBinaryOps() {
        return Combinators.combine(
                leafExpressions(),
                binaryOperators(),
                leafExpressions()).as(BinaryOp::new);
    }

    /**
     * Arbitrary for UnaryOp with leaf child (depth 1).
     */
    @Provide
    public static Arbitrary<UnaryOp> shallowUnaryOps() {
        return Combinators.combine(
                unaryOperators(),
                leafExpressions()).as(UnaryOp::new);
    }

    /**
     * Arbitrary for MethodCall with leaf children (depth 1).
     */
    @Provide
    public static Arbitrary<MethodCall> shallowMethodCalls() {
        return Combinators.combine(
                leafExpressions(),
                fieldNames(),
                types()).as((target, name, returnType) -> new MethodCall(target, name, List.of(), returnType));
    }

    /**
     * Arbitrary for expressions up to depth 1 (leaf or shallow composite).
     */
    @Provide
    public static Arbitrary<LambdaExpression> depth1Expressions() {
        return Arbitraries.oneOf(
                leafExpressions(),
                shallowBinaryOps().map(b -> b),
                shallowUnaryOps().map(u -> u),
                shallowMethodCalls().map(m -> m));
    }

    /**
     * Arbitrary for BinaryOp with depth-1 children (depth 2).
     */
    @Provide
    public static Arbitrary<BinaryOp> depth2BinaryOps() {
        return Combinators.combine(
                depth1Expressions(),
                binaryOperators(),
                depth1Expressions()).as(BinaryOp::new);
    }

    /**
     * Arbitrary for expressions up to depth 2.
     */
    @Provide
    public static Arbitrary<LambdaExpression> depth2Expressions() {
        return Arbitraries.oneOf(
                depth1Expressions(),
                depth2BinaryOps().map(b -> b));
    }

    /**
     * Arbitrary for expressions with captured variables at various depths.
     * These are useful for testing captured variable operations.
     */
    @Provide
    public static Arbitrary<LambdaExpression> expressionsWithCapturedVariables() {
        return Arbitraries.oneOf(
                // Direct captured variable
                capturedVariables().map(c -> c),
                // Captured variable in binary op
                Combinators.combine(
                        capturedVariables().map(c -> (LambdaExpression) c),
                        comparisonOperators(),
                        constants().map(c -> (LambdaExpression) c)).as(BinaryOp::new),
                // Captured variable as method target
                Combinators.combine(
                        capturedVariables().map(c -> (LambdaExpression) c),
                        fieldNames(),
                        types()).as((target, name, returnType) -> new MethodCall(target, name, List.of(), returnType)),
                // Multiple captured variables in binary op
                Combinators.combine(
                        capturedVariables().map(c -> (LambdaExpression) c),
                        logicalOperators(),
                        Combinators.combine(
                                capturedVariables().map(c -> (LambdaExpression) c),
                                comparisonOperators(),
                                constants().map(c -> (LambdaExpression) c)).as(BinaryOp::new).map(b -> (LambdaExpression) b))
                        .as(BinaryOp::new));
    }

    /**
     * Arbitrary for predicate-like expressions (boolean result).
     * Useful for testing predicate combination.
     */
    @Provide
    public static Arbitrary<LambdaExpression> predicateExpressions() {
        return Arbitraries.oneOf(
                // Simple comparison: field > constant
                Combinators.combine(
                        fieldAccesses().map(f -> (LambdaExpression) f),
                        comparisonOperators(),
                        constants().map(c -> (LambdaExpression) c)).as(BinaryOp::new),
                // Captured variable comparison
                Combinators.combine(
                        fieldAccesses().map(f -> (LambdaExpression) f),
                        comparisonOperators(),
                        capturedVariables().map(c -> (LambdaExpression) c)).as(BinaryOp::new),
                // Boolean field
                fieldNames().map(n -> new FieldAccess(n, Boolean.class)));
    }

    // ======================================================================
    // Offset Values for Renumbering Tests
    // ======================================================================

    /**
     * Arbitrary for offset values (non-negative, bounded).
     */
    @Provide
    public static Arbitrary<Integer> offsets() {
        return Arbitraries.integers().between(0, 100);
    }

    /**
     * Arbitrary for positive offset values (excluding zero).
     */
    @Provide
    public static Arbitrary<Integer> positiveOffsets() {
        return Arbitraries.integers().between(1, 100);
    }

    // ======================================================================
    // Lists for Combination Tests
    // ======================================================================

    /**
     * Arbitrary for non-empty lists of predicates.
     */
    @Provide
    public static Arbitrary<List<LambdaExpression>> predicateLists() {
        return predicateExpressions().list().ofMinSize(1).ofMaxSize(5);
    }
}
