/**
 * JPA Criteria API code generation from AST.
 *
 * <h2>Purpose</h2>
 * This package generates Quarkus Gizmo bytecode that implements
 * JPA Criteria API queries from the lambda expression AST.
 *
 * <h2>Key Types</h2>
 * <ul>
 * <li>{@link QueryExecutorClassGenerator} - Generates executor classes
 * <li>{@link CriteriaExpressionGenerator} - Generates JPA expressions
 * </ul>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 * <li>{@code expression} - Specialized expression builders
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <ul>
 * <li>{@code ast} - For AST node types (input)
 * <li>{@code common} - For shared utilities
 * <li>{@code util} - For type conversion
 * </ul>
 *
 * <h2>Design Pattern</h2>
 * Uses Visitor pattern: {@link CriteriaExpressionGenerator} visits AST nodes
 * and delegates to specialized builders in {@code expression} sub-package.
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.generation;

import org.jspecify.annotations.NullMarked;
