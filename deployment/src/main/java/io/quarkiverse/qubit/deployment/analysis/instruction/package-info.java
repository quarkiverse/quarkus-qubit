/**
 * Bytecode instruction handlers for lambda analysis.
 *
 * <h2>Purpose</h2>
 * This package contains handlers for individual JVM bytecode instructions
 * that transform instruction sequences into AST nodes.
 *
 * <h2>Key Types</h2>
 * <ul>
 *   <li>{@link InstructionHandler} - Handler interface for bytecode instructions
 *   <li>{@link InstructionHandlerRegistry} - Registry for instruction handlers
 *   <li>{@link AnalysisContext} - Context and state for bytecode analysis
 *   <li>{@link MethodInvocationHandler} - Handles method call instructions
 *   <li>{@link LoadInstructionHandler} - Handles variable load instructions
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <ul>
 *   <li>{@code ast} - For AST node types
 *   <li>{@code common} - For shared utilities
 *   <li>{@code util} - For parsing utilities
 * </ul>
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.analysis.instruction;

import org.jspecify.annotations.NullMarked;
