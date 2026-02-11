/**
 * Abstract Syntax Tree (AST) definitions for lambda expressions.
 *
 * <h2>Purpose</h2>
 * This package contains the sealed interface hierarchy that represents
 * parsed lambda expressions as a type-safe AST. The AST is the intermediate
 * representation between bytecode analysis and code generation.
 *
 * <h2>Key Types</h2>
 * <ul>
 * <li>{@link LambdaExpression} - Sealed interface for all expression types
 * <li>{@link LambdaExpression.BinaryOp} - Binary operations (AND, OR, EQ, etc.)
 * <li>{@link LambdaExpression.FieldAccess} - Entity field access
 * <li>{@link LambdaExpression.PathExpression} - Relationship navigation
 * </ul>
 *
 * <h2>Design Decisions</h2>
 * <ul>
 * <li>Sealed interface ensures exhaustive pattern matching
 * <li>Records provide immutability and value semantics
 * <li>Factory methods for common construction patterns
 * </ul>
 *
 * <h2>Dependencies</h2>
 * This package has no internal dependencies (leaf package).
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.ast;

import org.jspecify.annotations.NullMarked;
