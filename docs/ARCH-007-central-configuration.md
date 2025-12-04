# ARCH-007: Central Configuration Design Document

> **Status**: Proposed
> **Severity**: Medium
> **Issue Reference**: [code-quality-tracking.md](code-quality-tracking.md#arch-007-missing-central-configuration)

## Executive Summary

QUBIT currently has **zero configuration infrastructure**. Every behavioral aspect is hardcoded. This document defines a comprehensive configuration architecture that transforms QUBIT from a rigid tool into a flexible, production-ready Quarkus extension with **26+ distinct configuration options** across 5 categories.

---

## Table of Contents

1. [Current State Assessment](#1-current-state-assessment)
2. [Why Configuration is Needed](#2-why-configuration-is-needed)
3. [Proposed Configuration Architecture](#3-proposed-configuration-architecture)
4. [Configuration Properties Reference](#4-configuration-properties-reference)
5. [Implementation Changes](#5-implementation-changes)
6. [Example Usage](#6-example-usage)
7. [Implementation Roadmap](#7-implementation-roadmap)

---

## 1. Current State Assessment

### 1.1 What Exists Today

| Aspect | Current Implementation | Location |
|--------|----------------------|----------|
| **Constants** | `QubitConstants.java` (405 lines) | `runtime/` |
| **@ConfigMapping** | None | - |
| **@ConfigRoot** | None | - |
| **application.properties support** | None | - |
| **Build-time config** | None | - |
| **Runtime config** | None | - |

### 1.2 Hardcoded Behaviors Identified

#### Package Filtering (QubitProcessor.java:198-214)

```java
private static boolean isNotFrameworkClass(ClassInfo classInfo) {
    String className = classInfo.name().toString();
    if (className.startsWith("java.") || className.startsWith("jakarta.")) {
        return false;
    }
    if (className.startsWith("io.quarkiverse.qubit.")) {
        return true;
    }
    if (className.startsWith("io.quarkus.")) {
        return className.contains(".it.");
    }
    return true;
}
```

This filtering cannot be customized without code changes.

#### Logging Statements

From codebase analysis: **160 logging statements** across 26 files with no way to control verbosity per-component.

| File | Log Statement Count |
|------|---------------------|
| CallSiteProcessor.java | 32 |
| QubitProcessor.java | 18 |
| QubitRepositoryEnhancer.java | 14 |
| SubqueryAnalyzer.java | 11 |
| InvokeDynamicScanner.java | 10 |
| InvokeDynamicHandler.java | 9 |
| Other files (20) | 66 |

---

## 2. Why Configuration is Needed

### 2.1 User Flexibility Problems

| Problem | Impact | Current Workaround |
|---------|--------|-------------------|
| Cannot exclude packages from scanning | Slower builds, unnecessary processing | None |
| Cannot control log verbosity | Noisy logs or missing debug info | Manual Logger.setLevel() hacks |
| Cannot tune deduplication behavior | Potential hash collisions in edge cases | None |
| Cannot customize generated class naming | Namespace conflicts in multi-module projects | None |
| Cannot disable features (joins, groups, subqueries) | Larger bytecode footprint | None |

### 2.2 Testability Problems

- `InstructionHandlerRegistry` (ARCH-005) now supports DI, but no config controls handler selection
- `ExpressionBuilderRegistry` (ARCH-004) supports DI, but no config controls builder behavior
- No way to enable "analysis-only mode" for debugging without code generation

### 2.3 Production Readiness Requirements

Enterprise deployments need:
- **Metrics configuration** (expose executor counts, analysis timings)
- **Fail-fast vs. lenient modes** for unsupported lambda patterns
- **Logging structured output** for log aggregation systems

---

## 3. Proposed Configuration Architecture

### 3.1 Configuration Phases

Following the [Quarkus Extension Writing Guide](https://quarkus.io/guides/writing-extensions), QUBIT configuration is split into:

| Phase | Interface | Location | Purpose |
|-------|-----------|----------|---------|
| `BUILD_TIME` | `QubitBuildTimeConfig` | `deployment/` | Scanning, generation, feature flags |
| `RUN_TIME` | `QubitRuntimeConfig` | `runtime/` | Execution behavior, metrics |

### 3.2 Build-Time Configuration Interface

```java
// Location: deployment/src/main/java/io/quarkus/qubit/deployment/QubitBuildTimeConfig.java

package io.quarkiverse.qubit.deployment;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "quarkus.qubit")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface QubitBuildTimeConfig {

    /**
     * Scanning configuration for lambda analysis.
     */
    ScanningConfig scanning();

    /**
     * Code generation configuration.
     */
    GenerationConfig generation();

    /**
     * Feature flags for enabling/disabling specific capabilities.
     */
    FeaturesConfig features();

    /**
     * Logging configuration for build-time processing.
     */
    LoggingConfig logging();

    interface ScanningConfig {
        /**
         * Package prefixes to exclude from lambda scanning.
         * Default excludes: java., jakarta.
         */
        @WithDefault("java.,jakarta.")
        List<String> excludePackages();

        /**
         * Additional package prefixes to include (overrides exclude).
         * Useful for including specific io.quarkus packages.
         */
        Optional<List<String>> includePackages();

        /**
         * Whether to scan test classes (classes in paths containing .it. or .test.)
         */
        @WithDefault("true")
        boolean scanTestClasses();

        /**
         * Maximum classes to scan before warning (0 = unlimited).
         * Helps detect configuration issues in large projects.
         */
        @WithDefault("10000")
        int maxClassesToScan();
    }

    interface GenerationConfig {
        /**
         * Prefix for generated executor class names.
         * Default: "QubitExecutor$"
         */
        @WithDefault("QubitExecutor$")
        String classNamePrefix();

        /**
         * Package for generated executor classes.
         * Default: same package as the lambda's owning class
         */
        Optional<String> targetPackage();

        /**
         * Whether to include source location comments in generated bytecode.
         * Useful for debugging but increases class size.
         */
        @WithDefault("false")
        boolean includeSourceComments();

        /**
         * Maximum captured variables per lambda before warning.
         */
        @WithDefault("20")
        int maxCapturedVariables();
    }

    interface FeaturesConfig {
        /**
         * Enable join query support (join(), leftJoin()).
         */
        @WithDefault("true")
        boolean joinsEnabled();

        /**
         * Enable GROUP BY query support (groupBy()).
         */
        @WithDefault("true")
        boolean groupingEnabled();

        /**
         * Enable subquery support (Subqueries.exists(), etc.).
         */
        @WithDefault("true")
        boolean subqueriesEnabled();

        /**
         * Enable aggregation support (min, max, avg, sum*).
         */
        @WithDefault("true")
        boolean aggregationsEnabled();
    }

    interface LoggingConfig {
        /**
         * Log level for QUBIT build processing.
         * Options: OFF, ERROR, WARN, INFO, DEBUG, TRACE
         */
        @WithDefault("INFO")
        String level();

        /**
         * Log each scanned class (very verbose).
         */
        @WithDefault("false")
        boolean logScannedClasses();

        /**
         * Log each generated executor class.
         */
        @WithDefault("true")
        boolean logGeneratedClasses();

        /**
         * Log lambda deduplication events.
         */
        @WithDefault("false")
        boolean logDeduplication();

        /**
         * Log detailed bytecode analysis steps (TRACE-level detail).
         */
        @WithDefault("false")
        boolean logBytecodeAnalysis();
    }
}
```

### 3.3 Runtime Configuration Interface

```java
// Location: runtime/src/main/java/io/quarkus/qubit/runtime/QubitRuntimeConfig.java

package io.quarkiverse.qubit.runtime;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "quarkus.qubit")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface QubitRuntimeConfig {

    /**
     * Query execution configuration.
     */
    ExecutionConfig execution();

    /**
     * Metrics and observability configuration.
     */
    MetricsConfig metrics();

    interface ExecutionConfig {
        /**
         * Fail-fast mode: throw exception on missing executor.
         * When false, returns empty results with warning log.
         */
        @WithDefault("true")
        boolean failOnMissingExecutor();

        /**
         * Log slow queries (execution time > threshold).
         */
        Optional<Duration> slowQueryThreshold();

        /**
         * Enable query result caching (experimental).
         */
        @WithDefault("false")
        boolean cacheResults();

        /**
         * Maximum cache entries per executor.
         */
        @WithDefault("100")
        int maxCacheEntries();
    }

    interface MetricsConfig {
        /**
         * Expose executor count metrics.
         */
        @WithDefault("true")
        boolean exposeExecutorCounts();

        /**
         * Expose query execution timing metrics.
         */
        @WithDefault("false")
        boolean exposeExecutionTimings();
    }
}
```

---

## 4. Configuration Properties Reference

### 4.1 Complete Property Inventory

| Property | Type | Default | Phase | Description |
|----------|------|---------|-------|-------------|
| `quarkus.qubit.scanning.exclude-packages` | `List<String>` | `java.,jakarta.` | BUILD | Package prefixes to exclude |
| `quarkus.qubit.scanning.include-packages` | `List<String>` | (empty) | BUILD | Package prefixes to include (overrides exclude) |
| `quarkus.qubit.scanning.scan-test-classes` | `boolean` | `true` | BUILD | Scan test classes |
| `quarkus.qubit.scanning.max-classes-to-scan` | `int` | `10000` | BUILD | Warning threshold |
| `quarkus.qubit.generation.class-name-prefix` | `String` | `QubitExecutor$` | BUILD | Generated class prefix |
| `quarkus.qubit.generation.target-package` | `String` | (caller's) | BUILD | Target package for generated classes |
| `quarkus.qubit.generation.include-source-comments` | `boolean` | `false` | BUILD | Include debug comments |
| `quarkus.qubit.generation.max-captured-variables` | `int` | `20` | BUILD | Warning threshold |
| `quarkus.qubit.features.joins-enabled` | `boolean` | `true` | BUILD | Enable join support |
| `quarkus.qubit.features.grouping-enabled` | `boolean` | `true` | BUILD | Enable GROUP BY support |
| `quarkus.qubit.features.subqueries-enabled` | `boolean` | `true` | BUILD | Enable subquery support |
| `quarkus.qubit.features.aggregations-enabled` | `boolean` | `true` | BUILD | Enable aggregation support |
| `quarkus.qubit.logging.level` | `String` | `INFO` | BUILD | Log level |
| `quarkus.qubit.logging.log-scanned-classes` | `boolean` | `false` | BUILD | Log each scanned class |
| `quarkus.qubit.logging.log-generated-classes` | `boolean` | `true` | BUILD | Log generated classes |
| `quarkus.qubit.logging.log-deduplication` | `boolean` | `false` | BUILD | Log deduplication events |
| `quarkus.qubit.logging.log-bytecode-analysis` | `boolean` | `false` | BUILD | Log bytecode analysis |
| `quarkus.qubit.execution.fail-on-missing-executor` | `boolean` | `true` | RUN | Fail on missing executor |
| `quarkus.qubit.execution.slow-query-threshold` | `Duration` | (none) | RUN | Slow query logging threshold |
| `quarkus.qubit.execution.cache-results` | `boolean` | `false` | RUN | Enable result caching |
| `quarkus.qubit.execution.max-cache-entries` | `int` | `100` | RUN | Max cache size |
| `quarkus.qubit.metrics.expose-executor-counts` | `boolean` | `true` | RUN | Expose count metrics |
| `quarkus.qubit.metrics.expose-execution-timings` | `boolean` | `false` | RUN | Expose timing metrics |

### 4.2 Constants That Should NOT Be Configurable

These define API contracts and must remain constants:

| Constant Category | Examples | Reason |
|------------------|----------|--------|
| Fluent API method names | `METHOD_WHERE`, `METHOD_SELECT` | API contract |
| CriteriaBuilder methods | `CB_EQUAL`, `CB_AND` | JPA spec |
| JVM internal names | `JVM_JAVA_LANG_STRING` | JVM spec |
| Compiler prefixes | `CAPTURED_VAR_PREFIX_JAVAC` | Compiler implementation |

---

## 5. Implementation Changes

### 5.1 Files to Create

| File | Purpose |
|------|---------|
| `deployment/.../QubitBuildTimeConfig.java` | Build-time config interface |
| `runtime/.../QubitRuntimeConfig.java` | Runtime config interface |

### 5.2 Files to Modify

| File | Changes |
|------|---------|
| `deployment/QubitProcessor.java` | Inject config, use for scanning/logging decisions |
| `deployment/analysis/CallSiteProcessor.java` | Use config for generation decisions |
| `deployment/analysis/LambdaDeduplicator.java` | Configurable deduplication logging |
| `runtime/QueryExecutorRegistry.java` | Use runtime config for execution behavior |
| `deployment/pom.xml` | Ensure SmallRye Config is available |

### 5.3 QubitProcessor Changes

```java
@BuildStep
void generateQueryExecutors(
        QubitBuildTimeConfig config,  // Injected by Quarkus
        CombinedIndexBuildItem combinedIndex,
        ApplicationArchivesBuildItem applicationArchives,
        BuildProducer<GeneratedClassBuildItem> generatedClass,
        BuildProducer<QueryTransformationBuildItem> queryTransformations) {

    // Use config for filtering
    List<ClassInfo> filteredClasses = allClasses.stream()
            .filter(c -> isNotExcluded(c, config.scanning()))
            .toList();

    if (filteredClasses.size() > config.scanning().maxClassesToScan()) {
        log.warnf("Scanning %d classes exceeds threshold %d",
                  filteredClasses.size(), config.scanning().maxClassesToScan());
    }

    // Check feature flags before processing
    if (!config.features().joinsEnabled()) {
        // Skip join-related call sites
    }
}

private boolean isNotExcluded(ClassInfo classInfo, ScanningConfig config) {
    String className = classInfo.name().toString();

    // Check include packages first (override excludes)
    if (config.includePackages().isPresent()) {
        for (String includePrefix : config.includePackages().get()) {
            if (className.startsWith(includePrefix)) {
                return true;
            }
        }
    }

    // Check exclude packages
    for (String excludePrefix : config.excludePackages()) {
        if (className.startsWith(excludePrefix)) {
            return false;
        }
    }

    return true;
}
```

### 5.4 QueryExecutorRegistry Changes

```java
@ApplicationScoped
public class QueryExecutorRegistry {

    @Inject
    QubitRuntimeConfig config;

    public <T> List<T> executeListQuery(String callSiteId, Class<T> entityClass,
                                         Object[] capturedValues, ...) {
        QueryExecutor<List<?>> executor = LIST_EXECUTORS.get(callSiteId);

        if (executor == null) {
            if (config.execution().failOnMissingExecutor()) {
                throw new IllegalStateException(...);
            } else {
                log.warnf("No executor found for %s, returning empty list", callSiteId);
                return Collections.emptyList();
            }
        }

        // Track slow queries
        long start = System.nanoTime();
        List<T> result = executor.execute(...);
        long duration = System.nanoTime() - start;

        config.execution().slowQueryThreshold().ifPresent(threshold -> {
            if (Duration.ofNanos(duration).compareTo(threshold) > 0) {
                log.warnf("Slow query detected: %s took %dms",
                          callSiteId, Duration.ofNanos(duration).toMillis());
            }
        });

        return result;
    }
}
```

---

## 6. Example Usage

### 6.1 Default Configuration (No Changes Needed)

Works out of the box with sensible defaults matching current behavior.

### 6.2 Exclude Legacy Packages

```properties
quarkus.qubit.scanning.exclude-packages=java.,jakarta.,com.example.legacy,org.thirdparty
```

### 6.3 Debug Build-Time Analysis

```properties
quarkus.qubit.logging.level=DEBUG
quarkus.qubit.logging.log-bytecode-analysis=true
quarkus.qubit.logging.log-deduplication=true
```

### 6.4 Minimal Feature Set (Reduce Bytecode)

```properties
quarkus.qubit.features.joins-enabled=false
quarkus.qubit.features.grouping-enabled=false
quarkus.qubit.features.subqueries-enabled=false
```

### 6.5 Production with Slow Query Detection

```properties
quarkus.qubit.execution.slow-query-threshold=100ms
quarkus.qubit.execution.fail-on-missing-executor=false
quarkus.qubit.metrics.expose-execution-timings=true
```

### 6.6 Multi-Module Project (Avoid Collisions)

```properties
quarkus.qubit.generation.class-name-prefix=ModuleA_Query$
quarkus.qubit.generation.target-package=com.example.modulea.generated
```

---

## 7. Implementation Roadmap

### Phase 1: Core Infrastructure (2 days)

1. Create `QubitBuildTimeConfig` with scanning config only
2. Modify `QubitProcessor.isNotFrameworkClass()` to use config
3. Add logging level control
4. Add tests for config injection

### Phase 2: Generation & Features Config (1 day)

1. Add generation config (class naming, source comments)
2. Add feature flags
3. Modify `CallSiteProcessor` to respect feature flags

### Phase 3: Runtime Config (1 day)

1. Create `QubitRuntimeConfig`
2. Modify `QueryExecutorRegistry` for fail-mode and slow query detection
3. Add metrics exposure config

### Phase 4: Documentation & Polish (0.5 days)

1. Quarkus generates config documentation automatically from interfaces
2. Add configuration examples to README
3. Update code-quality-tracking.md to mark ARCH-007 resolved

---

## Appendix A: Related Architecture Decisions

| ID | Relationship |
|----|--------------|
| ARCH-004 | `ExpressionBuilderRegistry` - could be configured via DI |
| ARCH-005 | `InstructionHandlerRegistry` - could be configured via DI |
| ARCH-006 | `AnalysisContext` - immutable config set at construction |
| CS-001 | `QubitConstants` - remains for API constants, not config |

---

## Appendix B: References

- [Quarkus Extension Writing Guide](https://quarkus.io/guides/writing-extensions)
- [SmallRye Config @ConfigMapping](https://smallrye.io/smallrye-config/Main/config/mappings/)
- [Quarkus Configuration Reference](https://quarkus.io/guides/config-reference)
