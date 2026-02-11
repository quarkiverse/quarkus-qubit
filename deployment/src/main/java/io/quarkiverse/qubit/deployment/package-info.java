/**
 * QUBIT deployment module - Quarkus build-time lambda query processor.
 *
 * <h2>Purpose</h2>
 * This is the main deployment package for QUBIT, a Quarkus extension
 * that transforms Java lambda predicates into JPA Criteria API queries
 * at build time.
 *
 * <h2>Key Types</h2>
 * <ul>
 * <li>{@link QubitProcessor} - Main Quarkus build step processor
 * <li>{@link QubitRepositoryEnhancer} - Enhances repository classes
 * <li>{@link QubitEntityEnhancer} - Enhances entity classes
 * <li>{@link QubitBytecodeGenerator} - Low-level bytecode generation
 * </ul>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 * <li>{@code ast} - AST node type definitions
 * <li>{@code analysis} - Lambda bytecode analysis
 * <li>{@code generation} - JPA Criteria code generation
 * <li>{@code common} - Shared utilities
 * <li>{@code util} - Low-level parsing utilities
 * </ul>
 *
 * <h2>Package Architecture</h2>
 *
 * <pre>
 *                     deployment/ (root)
 *                           │
 *           ┌───────────────┼───────────────┐
 *           │               │               │
 *           ▼               ▼               ▼
 *        ast/          analysis/       generation/
 *           │               │               │
 *           │    ┌──────────┼──────────┐    │
 *           │    │          │          │    │
 *           │    ▼          ▼          ▼    │
 *           │ instruction/ branch/  expression/
 *           │    │          │          │    │
 *           └────┴──────────┴──────────┴────┘
 *                           │
 *                     ┌─────┴─────┐
 *                     ▼           ▼
 *                  common/      util/
 * </pre>
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment;

import org.jspecify.annotations.NullMarked;
