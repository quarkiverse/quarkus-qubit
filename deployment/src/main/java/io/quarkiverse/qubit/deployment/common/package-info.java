/**
 * Cross-cutting utilities shared across analysis and generation packages.
 *
 * <h2>Purpose</h2>
 * This package contains utility classes used by multiple packages,
 * providing common functionality for bytecode analysis and validation.
 *
 * <h2>Key Types</h2>
 * <ul>
 *   <li>{@link PatternDetector} - Detects bytecode patterns
 *   <li>{@link BytecodeValidator} - Validates bytecode structures
 *   <li>{@link BytecodeAnalysisException} - Exception for analysis errors
 *   <li>{@link BytecodeAnalysisConstants} - Constants used in analysis
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <ul>
 *   <li>{@code ast} - For AST node types
 * </ul>
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.common;

import org.jspecify.annotations.NullMarked;
