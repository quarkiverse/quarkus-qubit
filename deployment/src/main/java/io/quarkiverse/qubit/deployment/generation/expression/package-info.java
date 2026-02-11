/**
 * Specialized expression builders for JPA Criteria generation.
 *
 * <h2>Purpose</h2>
 * This package contains specialized builders for different types of
 * JPA Criteria expressions (arithmetic, string, temporal, etc.).
 *
 * <h2>Key Types</h2>
 * <ul>
 * <li>{@link ExpressionBuilder} - Marker interface for expression builders
 * <li>{@link ExpressionBuilderRegistry} - Registry for expression builders
 * <li>{@link ArithmeticExpressionBuilder} - Handles arithmetic operations
 * <li>{@link StringExpressionBuilder} - Handles string method expressions
 * <li>{@link TemporalExpressionBuilder} - Handles date/time expressions
 * <li>{@link SubqueryExpressionBuilder} - Handles subquery expressions
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <ul>
 * <li>{@code ast} - For AST node types
 * <li>{@code common} - For shared utilities
 * </ul>
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.generation.expression;

import org.jspecify.annotations.NullMarked;
