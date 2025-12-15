package io.quarkiverse.qubit.deployment.generation.expression;

/**
 * Marker interface for JPA Criteria API expression builders.
 *
 * <p>Expression builders transform lambda expression AST nodes into JPA Criteria API
 * bytecode using Gizmo. Each builder is specialized for a specific domain:
 *
 * <h2>Binary Operator Builders</h2>
 * <p>Handle operators from {@link io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp}:
 * <ul>
 *   <li>{@link ArithmeticExpressionBuilder} - ADD, SUB, MUL, DIV, MOD</li>
 *   <li>{@link ComparisonExpressionBuilder} - EQ, NE, GT, GE, LT, LE</li>
 * </ul>
 *
 * <h2>Method Call Builders</h2>
 * <p>Handle method invocations from {@link io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall}:
 * <ul>
 *   <li>{@link StringExpressionBuilder} - toLowerCase, toUpperCase, trim, startsWith, etc.</li>
 *   <li>{@link TemporalExpressionBuilder} - getYear, getMonth, isAfter, isBefore, etc.</li>
 *   <li>{@link BigDecimalExpressionBuilder} - add, subtract, multiply, divide</li>
 * </ul>
 *
 * <h2>Higher-Level Builders</h2>
 * <p>Handle complex query constructs requiring delegation to {@link ExpressionGeneratorHelper}:
 * <ul>
 *   <li>{@link BiEntityExpressionBuilder} - join query expressions</li>
 *   <li>{@link GroupExpressionBuilder} - GROUP BY expressions</li>
 *   <li>{@link SubqueryExpressionBuilder} - subquery expressions</li>
 * </ul>
 *
 * <h2>Design Rationale</h2>
 * <p>This is a marker interface rather than a functional interface because:
 * <ol>
 *   <li><b>Different input types</b>: Binary operator builders take {@code BinaryOp.Operator},
 *       while method call builders take {@code MethodCall} expressions</li>
 *   <li><b>Different method signatures</b>: StringExpressionBuilder has 4 specialized build methods
 *       (transformation, pattern, substring, utility) with different parameters</li>
 *   <li><b>No polymorphic dispatch needed</b>: CriteriaExpressionGenerator knows which builder
 *       to call based on expression type - no need for {@code canHandle()} + generic {@code build()}</li>
 *   <li><b>Delegation pattern</b>: BigDecimalExpressionBuilder demonstrates reuse through delegation
 *       to ArithmeticExpressionBuilder rather than inheritance</li>
 * </ol>
 *
 * <p>The interface provides:
 * <ul>
 *   <li>Type-level documentation of expression builder classes</li>
 *   <li>IDE navigation to find all builder implementations</li>
 *   <li>Clear organizational pattern within the builders package</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator
 * @see ExpressionGeneratorHelper
 */
public interface ExpressionBuilder {
    // Marker interface - see class-level documentation for design rationale
}
