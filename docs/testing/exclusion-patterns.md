# Exclusion Patterns Catalog

**Date:** 2026-01-24
**Status:** Active

## Overview

This document catalogs all code excluded from unit test coverage metrics (JaCoCo and Pitest)
and explains the rationale for each exclusion.

## Exclusion Philosophy

Code is excluded from unit test metrics when:
1. **Integration test covered** - The code is exercised by integration tests
2. **Framework-generated** - Java-generated code (records, enums) with no behavior to test
3. **Bytecode generation** - Gizmo-generated code that's inherently covered when the generated code works
4. **Constants only** - Classes containing only string constants or configuration values

## Pitest Excluded Classes

### Gizmo Bytecode Generation
These classes generate bytecode using Gizmo and are covered when the generated code works correctly:

| Class | Reason |
|-------|--------|
| `QueryExecutorClassGenerator` | Generates query executor implementations |
| `QubitBytecodeGenerator` | Core bytecode generation |
| `QubitEntityEnhancer` | Entity class enhancement |
| `QubitEntityOperationGenerationVisitor` | Entity operation generation |
| `CriteriaExpressionGenerator` | Generates JPA Criteria expressions from AST |
| `GizmoHelper` | Low-level Gizmo bytecode utilities |
| `BiEntityExpressionBuilder` | Generates JPA Criteria for bi-entity (join) queries |
| `SubqueryExpressionBuilder` | Generates JPA Criteria for subqueries |
| `GroupExpressionBuilder` | Generates JPA Criteria for group-by queries |
| `StringExpressionBuilder` | String method expression handling |
| `TemporalExpressionBuilder` | Date/time expression handling |
| `BigDecimalExpressionBuilder` | BigDecimal expression handling |
| `UnsupportedExpressionException` | Exception for unsupported expressions |
| `generation.join.*` | Join query bytecode generation |
| `generation.methodcall.*` | Method call handlers for bytecode generation |

### Quarkus Build Processors (IT-covered)
These classes are build-time only and covered by integration tests:

| Class | IT Coverage |
|-------|-------------|
| `QubitProcessor` | All IT tests exercise this |
| `QubitProcessor$*` | Inner classes |
| `QubitNativeImageProcessor` | Native image builds |
| `QubitRepositoryEnhancer` | Repository enhancement |
| `QubitRepositoryEnhancer$*` | Inner classes |
| `QubitDevUIProcessor` | DevUI integration tests |
| `QubitDevUIProcessor$*` | Inner classes |
| `CallSiteProcessor` | Called by QubitProcessor |
| `InvokeDynamicScanner` | Core bytecode scanner, called by QubitProcessor |

### Bytecode Analyzers (IT-covered)
These analyzers have low unit test value but are covered by integration tests:

| Class | Reason |
|-------|--------|
| `InvokeDynamicScanner$*` | Inner classes of bytecode scanner |
| `LambdaBytecodeAnalyzer` | Lambda bytecode analysis, covered by IT |
| `LambdaBytecodeAnalyzer$*` | Inner classes |
| `StreamPipelineAnalyzer` | Stream pipeline analysis, covered by IT |
| `StreamPipelineAnalyzer$*` | Inner classes |
| `RelationshipMetadataExtractor` | JPA relationship extraction, covered by IT |
| `LambdaDeduplicator` | Lambda deduplication, covered by IT |

### DevUI Display Utilities
Development-only display utilities with no production impact:

| Class | Reason |
|-------|--------|
| `JpqlGenerator` | Generates JPQL strings for DevUI display |
| `JavaSourceGenerator` | Generates source code for DevUI display |

### Build Items (Data Classes)
Data transfer objects with no behavior to test:

| Class | Reason |
|-------|--------|
| `LambdaReflectionBuildItem` | Quarkus build item |

### Handler Classes (Tested via Registry)
Strategy implementations tested indirectly through the `QueryTypeHandlerRegistry`:

| Pattern | Reason |
|---------|--------|
| `handler.*` | All handlers tested via registry dispatch |

### Java-Generated Code
Code that Java generates automatically with no behavior to test:

| Class | Type |
|-------|------|
| `InvokeDynamicScanner$JoinType` | Enum |
| `InvokeDynamicScanner$QueryContext` | Record |
| `InvokeDynamicScanner$LambdaPair` | Record |
| `InvokeDynamicScanner$SortLambda` | Record |
| `StringExpressionBuilder$StringOperationType` | Enum |
| `AnalysisOutcome$UnsupportedPattern$PatternType` | Enum |

### Constants Classes
Classes containing only constants:

| Class | Contents |
|-------|----------|
| `ExceptionMessages` | String constants for exceptions |
| `QubitConstants` | Runtime constants (fluent method names, etc.) |

### Runtime Module Exclusions (IT-Covered)
These runtime classes are extensively exercised by integration tests:

| Class | Reason |
|-------|--------|
| `QubitEntity` | Marker interface, no behavior to test |
| `QubitRepository` | Marker interface, no behavior to test |
| `Subqueries` | Factory class, IT-covered via subquery tests |
| `SubqueryBuilder` | Builder class, IT-covered via subquery tests |
| `QubitStreamImpl` | Core stream impl, IT-covered via all fluent API tests |
| `JoinStreamImpl` | Join stream impl, IT-covered via join tests |
| `GroupStreamImpl` | Group stream impl, IT-covered via group tests |
| `QueryExecutorRecorder` | Quarkus recorder, build-time only |
| `QueryExecutorRegistry` | Runtime registry, IT-covered via all query tests |
| `QubitConstants` | String constants only, no behavior to test |

## Pitest Excluded Tests

### Slow/Unsuitable Tests

| Test Class | Reason |
|------------|--------|
| `QubitDevUIBuildTimeDataTest` | Starts Quarkus dev server (~120s) |

### Pitest Isolation Issues
Tests that pass with `mvn test` but fail in Pitest's forked environment:

| Test Class | Issue |
|------------|-------|
| `ComplexExpressionsBytecodeTest` | Classloader isolation |
| `EqualityOperationsCriteriaTest` | Classloader isolation |

## JaCoCo Exclusions

Located in parent `pom.xml` under `jacoco-maven-plugin`:

```xml
<excludes>
    <!-- Same patterns as Pitest -->
    <exclude>io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator*</exclude>
    <exclude>io.quarkiverse.qubit.deployment.generation.QubitBytecodeGenerator*</exclude>
    <!-- ... etc -->
</excludes>
```

## Verification: IT Coverage

To verify excluded code is covered by integration tests:

```bash
# Run integration tests with JaCoCo
mvn verify -pl integration-tests -Djacoco.check.skip=true

# Check coverage report
open integration-tests/target/site/jacoco/index.html
```

Classes excluded from unit tests should show coverage in integration test reports.

## Adding New Exclusions

Before adding a new exclusion:

1. **Verify IT coverage exists** - The class should be exercised by integration tests
2. **Document the rationale** - Add to this file with clear reasoning
3. **Update both tools** - Add to Pitest `excludedClasses` AND JaCoCo `excludes`
4. **Cross-reference** - Note which IT tests cover the excluded code

## Configuration Locations

| Tool | Location | Section |
|------|----------|---------|
| Pitest | `deployment/pom.xml` | `excludedClasses`, `excludedTestClasses` |
| JaCoCo | `pom.xml` (parent) | `jacoco-maven-plugin` rules |
