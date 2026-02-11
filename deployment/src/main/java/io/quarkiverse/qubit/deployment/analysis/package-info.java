/**
 * Lambda bytecode analysis and transformation.
 *
 * <h2>Purpose</h2>
 * This package analyzes Java lambda bytecode and transforms it into
 * the AST representation defined in {@code io.quarkiverse.qubit.deployment.ast}.
 *
 * <h2>Key Types</h2>
 * <ul>
 * <li>{@link LambdaBytecodeAnalyzer} - Entry point for lambda analysis
 * <li>{@link CallSiteProcessor} - Processes lambda call sites
 * <li>{@link InvokeDynamicScanner} - Scans for invokedynamic instructions
 * <li>{@link LambdaDeduplicator} - Deduplicates equivalent lambdas
 * </ul>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 * <li>{@code instruction} - Individual bytecode instruction handlers
 * <li>{@code branch} - Branch instruction analysis
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <ul>
 * <li>{@code ast} - For AST node types
 * <li>{@code common} - For shared utilities
 * <li>{@code util} - For parsing utilities
 * </ul>
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.analysis;

import org.jspecify.annotations.NullMarked;
