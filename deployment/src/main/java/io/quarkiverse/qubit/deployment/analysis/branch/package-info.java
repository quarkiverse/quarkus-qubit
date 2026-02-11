/**
 * Branch instruction analysis for control flow handling.
 *
 * <h2>Purpose</h2>
 * This package handles branch instructions (IFEQ, IFNE, IF_ICMPLE, etc.)
 * and transforms them into logical expressions in the AST.
 *
 * <h2>Key Types</h2>
 * <ul>
 * <li>{@link BranchCoordinator} - Coordinates branch analysis
 * <li>{@link BranchState} - Tracks branch analysis state
 * <li>{@link BranchHandler} - Interface for branch instruction handlers
 * <li>{@link SingleOperandComparisonHandler} - Handles single-operand comparisons
 * <li>{@link TwoOperandComparisonHandler} - Handles two-operand comparisons
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
package io.quarkiverse.qubit.deployment.analysis.branch;

import org.jspecify.annotations.NullMarked;
