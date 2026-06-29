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
 * Provides generators for property-based testing of AST transformations.
 * Generators produce bounded expression trees to avoid infinite recursion.
 *
 * @see LambdaExpression
 */
public class AstArbitraries {

    private static final Class<?>[] COMMON_TYPES = {
            String.class, Integer.class, Long.class, Double.class,
            Boolean.class, BigDecimal.class, Object.class
    };

    @Provide
    public static Arbitrary<Class<?>> types() {
        return Arbitraries.of(COMMON_TYPES);
    }

    @Provide
    public static Arbitrary<String> fieldNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> Character.toLowerCase(s.charAt(0)) + s.substring(1));
    }

    @Provide
    public static Arbitrary<Constant> constants() {
        return Arbitraries.oneOf(
                Arbitraries.strings().ofMaxLength(50)
                        .map(s -> new Constant(s, String.class)),
                Arbitraries.integers()
                        .map(i -> new Constant(i, Integer.class)),
                Arbitraries.longs()
                        .map(l -> new Constant(l, Long.class)),
                Arbitraries.doubles().between(-1e6, 1e6)
                        .map(d -> new Constant(d, Double.class)),
                Arbitraries.of(Constant.TRUE, Constant.FALSE));
    }

    @Provide
    public static Arbitrary<FieldAccess> fieldAccesses() {
        return Combinators.combine(fieldNames(), types())
                .as(FieldAccess::new);
    }

    @Provide
    public static Arbitrary<CapturedVariable> capturedVariables() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 9),
                types()).as(CapturedVariable::new);
    }

    @Provide
    public static Arbitrary<LambdaExpression> leafExpressions() {
        return Arbitraries.oneOf(
                constants().map(c -> c),
                fieldAccesses().map(f -> f),
                capturedVariables().map(c -> c),
                parameters().map(p -> p),
                nullLiterals().map(n -> n));
    }

    @Provide
    public static Arbitrary<BinaryOp> shallowBinaryOps() {
        return Combinators.combine(
                leafExpressions(),
                binaryOperators(),
                leafExpressions()).as(BinaryOp::new);
    }

    @Provide
    public static Arbitrary<LambdaExpression> expressionsWithCapturedVariables() {
        return Arbitraries.oneOf(
                capturedVariables().map(c -> c),
                Combinators.combine(
                        capturedVariables().map(c -> (LambdaExpression) c),
                        comparisonOperators(),
                        constants().map(c -> (LambdaExpression) c)).as(BinaryOp::new),
                Combinators.combine(
                        capturedVariables().map(c -> (LambdaExpression) c),
                        fieldNames(),
                        types()).as((target, name, returnType) -> new MethodCall(target, name, List.of(), returnType)),
                Combinators.combine(
                        capturedVariables().map(c -> (LambdaExpression) c),
                        logicalOperators(),
                        Combinators.combine(
                                capturedVariables().map(c -> (LambdaExpression) c),
                                comparisonOperators(),
                                constants().map(c -> (LambdaExpression) c)).as(BinaryOp::new).map(b -> (LambdaExpression) b))
                        .as(BinaryOp::new));
    }

    @Provide
    public static Arbitrary<LambdaExpression> predicateExpressions() {
        return Arbitraries.oneOf(
                Combinators.combine(
                        fieldAccesses().map(f -> (LambdaExpression) f),
                        comparisonOperators(),
                        constants().map(c -> (LambdaExpression) c)).as(BinaryOp::new),
                Combinators.combine(
                        fieldAccesses().map(f -> (LambdaExpression) f),
                        comparisonOperators(),
                        capturedVariables().map(c -> (LambdaExpression) c)).as(BinaryOp::new),
                fieldNames().map(n -> new FieldAccess(n, Boolean.class)));
    }

    @Provide
    public static Arbitrary<List<LambdaExpression>> predicateLists() {
        return predicateExpressions().list().ofMinSize(1).ofMaxSize(5);
    }

    // Internal composition methods — used by public generators above

    private static Arbitrary<Parameter> parameters() {
        return Combinators.combine(
                fieldNames(),
                types(),
                Arbitraries.integers().between(0, 5)).as(Parameter::new);
    }

    private static Arbitrary<NullLiteral> nullLiterals() {
        return types().map(NullLiteral::new);
    }

    private static Arbitrary<BinaryOp.Operator> binaryOperators() {
        return Arbitraries.of(BinaryOp.Operator.values());
    }

    private static Arbitrary<BinaryOp.Operator> comparisonOperators() {
        return Arbitraries.of(
                BinaryOp.Operator.EQ, BinaryOp.Operator.NE,
                BinaryOp.Operator.LT, BinaryOp.Operator.LE,
                BinaryOp.Operator.GT, BinaryOp.Operator.GE);
    }

    private static Arbitrary<BinaryOp.Operator> logicalOperators() {
        return Arbitraries.of(BinaryOp.Operator.AND, BinaryOp.Operator.OR);
    }

}
