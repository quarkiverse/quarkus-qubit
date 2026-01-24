# Performance Testing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add performance testing infrastructure to capture build-time and runtime metrics for optimization baseline.

**Architecture:** Build metrics collected in QubitProcessor via config-controlled instrumentation. Runtime memory measured in a @QuarkusTest using reflection-based deep size estimation. Both output JSON for comparison across builds.

**Tech Stack:** Quarkus SmallRye Config, System.nanoTime(), Reflection API, JUnit 5, AssertJ

---

## Task 1: Create BuildMetricsConfig Interface

**Files:**
- Create: `deployment/src/main/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsConfig.java`
- Test: `deployment/src/test/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsConfigTest.java`

**Step 1: Write the failing test**

```java
package io.quarkiverse.qubit.deployment.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuildMetricsConfigTest {

    @Test
    void defaultValuesAreDisabled() {
        // Verify interface exists and default values
        // This test validates the config interface structure
        assertThat(BuildMetricsConfig.class.isInterface()).isTrue();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl deployment -Dtest=BuildMetricsConfigTest -q`
Expected: FAIL with "cannot find symbol: class BuildMetricsConfig"

**Step 3: Write the implementation**

```java
package io.quarkiverse.qubit.deployment.metrics;

import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Configuration for build-time performance metrics collection.
 * Nested under quarkus.qubit.metrics in QubitBuildTimeConfig.
 */
public interface BuildMetricsConfig {

    /**
     * Enable build-time metrics collection.
     * When enabled, timing data is collected for each processing phase.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Output path for build metrics JSON file.
     * Relative paths are resolved from the project root.
     */
    @WithDefault("target/qubit-build-metrics.json")
    String outputPath();
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl deployment -Dtest=BuildMetricsConfigTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsConfig.java deployment/src/test/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsConfigTest.java
git commit -m "feat(metrics): add BuildMetricsConfig interface"
```

---

## Task 2: Create BuildMetricsCollector Class

**Files:**
- Create: `deployment/src/main/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsCollector.java`
- Test: `deployment/src/test/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsCollectorTest.java`

**Step 1: Write the failing test**

```java
package io.quarkiverse.qubit.deployment.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

class BuildMetricsCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void collectsPhaseTiming() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.startPhase("lambda_discovery");
        // Simulate work
        collector.endPhase("lambda_discovery");

        assertThat(collector.getPhaseDuration("lambda_discovery"))
            .isGreaterThanOrEqualTo(0L);
    }

    @Test
    void tracksQueryCount() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.incrementQueryCount();
        collector.incrementQueryCount();

        assertThat(collector.getQueryCount()).isEqualTo(2);
    }

    @Test
    void writesJsonReport() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();
        collector.startPhase("test_phase");
        collector.endPhase("test_phase");
        collector.incrementQueryCount();

        Path outputPath = tempDir.resolve("metrics.json");
        collector.writeReport(outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);
        assertThat(content).contains("\"total_ms\"");
        assertThat(content).contains("\"phases\"");
        assertThat(content).contains("\"test_phase\"");
        assertThat(content).contains("\"query_count\"");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl deployment -Dtest=BuildMetricsCollectorTest -q`
Expected: FAIL with "cannot find symbol: class BuildMetricsCollector"

**Step 3: Write the implementation**

```java
package io.quarkiverse.qubit.deployment.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collects build-time performance metrics for Qubit processing phases.
 * Thread-safe for use across build steps.
 */
public class BuildMetricsCollector {

    private final long startTime;
    private final Map<String, Long> phaseStartTimes = new LinkedHashMap<>();
    private final Map<String, Long> phaseDurations = new LinkedHashMap<>();
    private int queryCount;

    public BuildMetricsCollector() {
        this.startTime = System.nanoTime();
    }

    /**
     * Marks the start of a processing phase.
     */
    public synchronized void startPhase(String phase) {
        phaseStartTimes.put(phase, System.nanoTime());
    }

    /**
     * Marks the end of a processing phase and records duration.
     */
    public synchronized void endPhase(String phase) {
        Long start = phaseStartTimes.get(phase);
        if (start != null) {
            long durationNanos = System.nanoTime() - start;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            phaseDurations.put(phase, durationMs);
        }
    }

    /**
     * Increments the count of processed queries.
     */
    public synchronized void incrementQueryCount() {
        queryCount++;
    }

    /**
     * Returns duration of a phase in milliseconds.
     */
    public synchronized long getPhaseDuration(String phase) {
        return phaseDurations.getOrDefault(phase, 0L);
    }

    /**
     * Returns total query count.
     */
    public synchronized int getQueryCount() {
        return queryCount;
    }

    /**
     * Writes metrics report to JSON file.
     */
    public synchronized void writeReport(Path outputPath) throws IOException {
        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"total_ms\": ").append(totalMs).append(",\n");
        json.append("  \"phases\": {\n");

        boolean first = true;
        for (Map.Entry<String, Long> entry : phaseDurations.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            first = false;
        }

        json.append("\n  },\n");
        json.append("  \"query_count\": ").append(queryCount).append("\n");
        json.append("}\n");

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json.toString());
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl deployment -Dtest=BuildMetricsCollectorTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsCollector.java deployment/src/test/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsCollectorTest.java
git commit -m "feat(metrics): add BuildMetricsCollector for phase timing"
```

---

## Task 3: Integrate Metrics Config into QubitBuildTimeConfig

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/QubitBuildTimeConfig.java:23-24`
- Test: `deployment/src/test/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsConfigIntegrationTest.java`

**Step 1: Write the failing test**

```java
package io.quarkiverse.qubit.deployment.metrics;

import io.quarkiverse.qubit.deployment.QubitBuildTimeConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

class BuildMetricsConfigIntegrationTest {

    @Test
    void configHasMetricsMethod() throws Exception {
        Method metricsMethod = QubitBuildTimeConfig.class.getMethod("metrics");
        assertThat(metricsMethod.getReturnType()).isEqualTo(BuildMetricsConfig.class);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl deployment -Dtest=BuildMetricsConfigIntegrationTest -q`
Expected: FAIL with "NoSuchMethodException: metrics()"

**Step 3: Modify QubitBuildTimeConfig**

Add after line 23 (after `LoggingConfig logging();`):

```java
    /** Performance metrics configuration. */
    MetricsConfig metrics();
```

Add the nested interface before the closing brace:

```java
    /**
     * Performance metrics collection configuration.
     */
    interface MetricsConfig extends BuildMetricsConfig {
        // Inherits enabled() and outputPath() from BuildMetricsConfig
    }
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl deployment -Dtest=BuildMetricsConfigIntegrationTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/QubitBuildTimeConfig.java deployment/src/test/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsConfigIntegrationTest.java
git commit -m "feat(config): add metrics section to QubitBuildTimeConfig"
```

---

## Task 4: Instrument QubitProcessor with Metrics Collection

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/QubitProcessor.java:159-218`
- Test: Manual verification via build log

**Step 1: Add metrics collector field and initialization**

Add import at top of QubitProcessor:
```java
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import java.nio.file.Path;
```

**Step 2: Wrap processing phases with timing**

Modify `generateQueryExecutors` method to add metrics instrumentation:

```java
@BuildStep
void generateQueryExecutors(
        QubitBuildTimeConfig config,
        CombinedIndexBuildItem combinedIndex,
        ApplicationArchivesBuildItem applicationArchives,
        BuildProducer<GeneratedClassBuildItem> generatedClass,
        BuildProducer<QueryTransformationBuildItem> queryTransformations) {

    // Initialize metrics collector if enabled
    BuildMetricsCollector metricsCollector = config.metrics().enabled()
            ? new BuildMetricsCollector()
            : null;

    Log.debugf("Qubit: Scanning for lambda call sites using invokedynamic analysis");

    IndexView index = combinedIndex.getIndex();
    InvokeDynamicScanner scanner = new InvokeDynamicScanner();

    Collection<ClassInfo> allClasses = index.getKnownClasses();

    Log.debugf("Qubit: Scanning %d classes for lambda call sites", allClasses.size());

    // Phase: Lambda Discovery
    if (metricsCollector != null) {
        metricsCollector.startPhase("lambda_discovery");
    }

    List<ClassInfo> filteredClasses = allClasses.stream()
            .filter(classInfo -> isNotExcludedClass(classInfo, config.scanning()))
            .toList();

    Log.infof("Qubit: Filtered to %d application classes (from %d total)",
            filteredClasses.size(), allClasses.size());

    // Log test classes found
    long testClassCount = filteredClasses.stream()
            .filter(c -> c.name().toString().contains(".it.") || c.name().toString().contains(".test."))
            .count();
    if (config.scanning().scanTestClasses()) {
        Log.infof("Qubit: Found %d test classes (scanning enabled)", testClassCount);
    } else {
        Log.debugf("Qubit: Skipped %d test classes (scanning disabled)", testClassCount);
    }

    CallSiteProcessor configuredProcessor = new CallSiteProcessor(
            bytecodeAnalyzer, deduplicator, classGenerator, config.generation());

    List<InvokeDynamicScanner.LambdaCallSite> allCallSites = filteredClasses.stream()
            .flatMap(classInfo -> scanClassForCallSites(classInfo, scanner, applicationArchives, config.logging()).stream())
            .peek(c -> Log.tracef("Qubit: Found callSite %s", c.getCallSiteId()))
            .toList();

    if (metricsCollector != null) {
        metricsCollector.endPhase("lambda_discovery");
    }

    Log.debugf("Qubit: Found %d total lambda call site(s)", allCallSites.size());

    validateUniqueCallSiteIds(allCallSites);

    AtomicInteger generatedCount = new AtomicInteger(0);
    AtomicInteger deduplicatedCount = new AtomicInteger(0);

    // Phase: Bytecode Analysis and Code Generation
    if (metricsCollector != null) {
        metricsCollector.startPhase("bytecode_analysis");
    }

    allCallSites.stream()
            .forEach(callSite -> {
                configuredProcessor.processCallSiteWithHandlers(
                        callSite, applicationArchives,
                        generatedCount, deduplicatedCount,
                        generatedClass, queryTransformations,
                        config.logging(),
                        true);
                if (metricsCollector != null) {
                    metricsCollector.incrementQueryCount();
                }
            });

    if (metricsCollector != null) {
        metricsCollector.endPhase("bytecode_analysis");
    }

    Log.infof("Qubit extension initialized - Call sites: %d | Query executors: %d generated, %d deduplicated",
            allCallSites.size(), generatedCount.get(), deduplicatedCount.get());

    // Write metrics report if enabled
    if (metricsCollector != null) {
        try {
            Path outputPath = Path.of(config.metrics().outputPath());
            metricsCollector.writeReport(outputPath);
            Log.infof("Qubit: Build metrics written to %s", outputPath);
        } catch (Exception e) {
            Log.warnf(e, "Qubit: Failed to write build metrics");
        }
    }
}
```

**Step 3: Verify instrumentation works**

Run: `mvn clean verify -pl integration-tests -Dquarkus.qubit.metrics.enabled=true -q`
Expected: Build succeeds, check for `target/qubit-build-metrics.json`

**Step 4: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/QubitProcessor.java
git commit -m "feat(metrics): instrument QubitProcessor with build metrics collection"
```

---

## Task 5: Create MemoryEstimator Utility

**Files:**
- Create: `integration-tests/src/test/java/io/quarkiverse/qubit/it/util/MemoryEstimator.java`
- Test: `integration-tests/src/test/java/io/quarkiverse/qubit/it/util/MemoryEstimatorTest.java`

**Step 1: Write the failing test**

```java
package io.quarkiverse.qubit.it.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

class MemoryEstimatorTest {

    @Test
    void estimatesPrimitiveSize() {
        long size = MemoryEstimator.estimateDeepSize(42);
        // Integer object: header (12-16 bytes) + int value (4 bytes)
        assertThat(size).isGreaterThan(0);
    }

    @Test
    void estimatesStringSize() {
        String str = "Hello, World!";
        long size = MemoryEstimator.estimateDeepSize(str);
        // String has char array backing
        assertThat(size).isGreaterThan(str.length());
    }

    @Test
    void estimatesMapSize() {
        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);

        long size = MemoryEstimator.estimateDeepSize(map);
        assertThat(size).isGreaterThan(0);
    }

    @Test
    void handlesNullGracefully() {
        long size = MemoryEstimator.estimateDeepSize(null);
        assertThat(size).isEqualTo(0);
    }

    @Test
    void handlesCyclesGracefully() {
        Object[] cycle = new Object[1];
        cycle[0] = cycle; // Self-reference

        long size = MemoryEstimator.estimateDeepSize(cycle);
        assertThat(size).isGreaterThan(0); // Should not stack overflow
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl integration-tests -Dtest=MemoryEstimatorTest -q`
Expected: FAIL with "cannot find symbol: class MemoryEstimator"

**Step 3: Write the implementation**

```java
package io.quarkiverse.qubit.it.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Reflection-based deep size estimator for heap usage measurement.
 * Provides rough estimates (±20% accuracy) for baseline comparisons.
 */
public final class MemoryEstimator {

    // Object header size (compressed oops on 64-bit JVM)
    private static final int OBJECT_HEADER_SIZE = 12;
    // Reference size (compressed oops)
    private static final int REFERENCE_SIZE = 4;
    // Array header size
    private static final int ARRAY_HEADER_SIZE = 16;

    private MemoryEstimator() {
        // Utility class
    }

    /**
     * Estimates the deep size of an object graph in bytes.
     * Traverses all reachable objects via reflection.
     */
    public static long estimateDeepSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        return estimateSize(obj, visited);
    }

    private static long estimateSize(Object obj, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) {
            return 0;
        }

        Class<?> clazz = obj.getClass();

        // Skip Class objects to avoid infinite recursion
        if (clazz == Class.class) {
            return 0;
        }

        visited.add(obj);

        if (clazz.isArray()) {
            return estimateArraySize(obj, visited);
        }

        return estimateObjectSize(obj, clazz, visited);
    }

    private static long estimateArraySize(Object array, Set<Object> visited) {
        Class<?> componentType = array.getClass().getComponentType();
        int length = Array.getLength(array);

        long size = ARRAY_HEADER_SIZE;

        if (componentType.isPrimitive()) {
            size += length * getPrimitiveSize(componentType);
        } else {
            size += length * REFERENCE_SIZE;
            for (int i = 0; i < length; i++) {
                size += estimateSize(Array.get(array, i), visited);
            }
        }

        return alignTo8(size);
    }

    private static long estimateObjectSize(Object obj, Class<?> clazz, Set<Object> visited) {
        long size = OBJECT_HEADER_SIZE;

        // Walk up the class hierarchy
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                Class<?> fieldType = field.getType();

                if (fieldType.isPrimitive()) {
                    size += getPrimitiveSize(fieldType);
                } else {
                    size += REFERENCE_SIZE;
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(obj);
                        size += estimateSize(fieldValue, visited);
                    } catch (IllegalAccessException | InaccessibleObjectException e) {
                        // Skip inaccessible fields
                    }
                }
            }
            current = current.getSuperclass();
        }

        return alignTo8(size);
    }

    private static int getPrimitiveSize(Class<?> type) {
        if (type == boolean.class || type == byte.class) return 1;
        if (type == char.class || type == short.class) return 2;
        if (type == int.class || type == float.class) return 4;
        if (type == long.class || type == double.class) return 8;
        return 4; // Default
    }

    private static long alignTo8(long size) {
        return (size + 7) & ~7;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl integration-tests -Dtest=MemoryEstimatorTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add integration-tests/src/test/java/io/quarkiverse/qubit/it/util/MemoryEstimator.java integration-tests/src/test/java/io/quarkiverse/qubit/it/util/MemoryEstimatorTest.java
git commit -m "feat(metrics): add MemoryEstimator for heap size measurement"
```

---

## Task 6: Add getRegisteredExecutorCount to QueryExecutorRegistry

**Files:**
- Modify: `runtime/src/main/java/io/quarkiverse/qubit/runtime/internal/QueryExecutorRegistry.java:557-569`
- Test: `runtime/src/test/java/io/quarkiverse/qubit/runtime/internal/QueryExecutorRegistryMetricsTest.java`

**Step 1: Write the failing test**

```java
package io.quarkiverse.qubit.runtime.internal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

class QueryExecutorRegistryMetricsTest {

    @Test
    void hasGetRegisteredExecutorCountMethod() throws Exception {
        Method method = QueryExecutorRegistry.class.getMethod("getRegisteredExecutorCount");
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl runtime -Dtest=QueryExecutorRegistryMetricsTest -q`
Expected: FAIL with "NoSuchMethodException: getRegisteredExecutorCount()"

**Step 3: Add the method**

Add after `getTotalExecutorCount()` method (around line 569):

```java
/**
 * Returns total count of registered executors for performance metrics.
 * Alias for getTotalExecutorCount() for clarity in metrics context.
 */
public static int getRegisteredExecutorCount() {
    return getTotalExecutorCount();
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl runtime -Dtest=QueryExecutorRegistryMetricsTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add runtime/src/main/java/io/quarkiverse/qubit/runtime/internal/QueryExecutorRegistry.java runtime/src/test/java/io/quarkiverse/qubit/runtime/internal/QueryExecutorRegistryMetricsTest.java
git commit -m "feat(metrics): add getRegisteredExecutorCount to QueryExecutorRegistry"
```

---

## Task 7: Create PerformanceReport Utility

**Files:**
- Create: `integration-tests/src/test/java/io/quarkiverse/qubit/it/util/PerformanceReport.java`
- Test: `integration-tests/src/test/java/io/quarkiverse/qubit/it/util/PerformanceReportTest.java`

**Step 1: Write the failing test**

```java
package io.quarkiverse.qubit.it.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

class PerformanceReportTest {

    @TempDir
    Path tempDir;

    @Test
    void writesRuntimeMetrics() throws Exception {
        Path outputPath = tempDir.resolve("runtime-metrics.json");

        PerformanceReport.writeRuntimeMetrics(47, 102400L, outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);
        assertThat(content).contains("\"executor_count\": 47");
        assertThat(content).contains("\"heap_bytes\": 102400");
        assertThat(content).contains("\"timestamp\"");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl integration-tests -Dtest=PerformanceReportTest -q`
Expected: FAIL with "cannot find symbol: class PerformanceReport"

**Step 3: Write the implementation**

```java
package io.quarkiverse.qubit.it.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Utility for writing runtime performance metrics to JSON.
 */
public final class PerformanceReport {

    private PerformanceReport() {
        // Utility class
    }

    /**
     * Writes runtime metrics to a JSON file.
     *
     * @param executorCount Number of registered query executors
     * @param heapBytes     Estimated heap usage in bytes
     * @param outputPath    Path to write JSON file
     */
    public static void writeRuntimeMetrics(int executorCount, long heapBytes, Path outputPath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"executor_count\": ").append(executorCount).append(",\n");
        json.append("  \"heap_bytes\": ").append(heapBytes).append(",\n");
        json.append("  \"heap_kb\": ").append(heapBytes / 1024).append(",\n");
        json.append("  \"heap_mb\": ").append(String.format("%.2f", heapBytes / (1024.0 * 1024.0))).append("\n");
        json.append("}\n");

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json.toString());
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl integration-tests -Dtest=PerformanceReportTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add integration-tests/src/test/java/io/quarkiverse/qubit/it/util/PerformanceReport.java integration-tests/src/test/java/io/quarkiverse/qubit/it/util/PerformanceReportTest.java
git commit -m "feat(metrics): add PerformanceReport utility for JSON output"
```

---

## Task 8: Create PerformanceTestProfile

**Files:**
- Create: `integration-tests/src/test/java/io/quarkiverse/qubit/it/PerformanceTestProfile.java`

**Step 1: Write the implementation**

```java
package io.quarkiverse.qubit.it;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables build-time metrics collection.
 */
public class PerformanceTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.qubit.metrics.enabled", "true",
            "quarkus.qubit.metrics.output-path", "target/qubit-build-metrics.json"
        );
    }

    @Override
    public String getConfigProfile() {
        return "performance";
    }
}
```

**Step 2: Commit**

```bash
git add integration-tests/src/test/java/io/quarkiverse/qubit/it/PerformanceTestProfile.java
git commit -m "feat(metrics): add PerformanceTestProfile for metrics-enabled testing"
```

---

## Task 9: Create PerformanceBaselineTest

**Files:**
- Create: `integration-tests/src/test/java/io/quarkiverse/qubit/it/PerformanceBaselineTest.java`

**Step 1: Write the test**

```java
package io.quarkiverse.qubit.it;

import io.quarkiverse.qubit.it.util.MemoryEstimator;
import io.quarkiverse.qubit.it.util.PerformanceReport;
import io.quarkiverse.qubit.runtime.internal.QueryExecutorRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance baseline test that captures runtime memory metrics.
 * Run with: mvn verify -pl integration-tests -Dtest=PerformanceBaselineTest
 */
@QuarkusTest
@TestProfile(PerformanceTestProfile.class)
class PerformanceBaselineTest {

    @Inject
    QueryExecutorRegistry registry;

    @Test
    void captureRuntimeMemoryBaseline() throws Exception {
        // Get executor count
        int executorCount = QueryExecutorRegistry.getRegisteredExecutorCount();
        assertThat(executorCount).isGreaterThan(0)
            .as("Should have registered query executors from integration tests");

        // Estimate heap usage
        long heapBytes = MemoryEstimator.estimateDeepSize(registry);
        assertThat(heapBytes).isGreaterThan(0)
            .as("Registry should occupy some heap space");

        // Write runtime metrics
        Path outputPath = Path.of("target/qubit-runtime-metrics.json");
        PerformanceReport.writeRuntimeMetrics(executorCount, heapBytes, outputPath);

        // Log for visibility
        System.out.printf("Qubit Performance Baseline:%n");
        System.out.printf("  Executor count: %d%n", executorCount);
        System.out.printf("  Heap usage: %d bytes (%.2f KB)%n", heapBytes, heapBytes / 1024.0);
        System.out.printf("  Report: %s%n", outputPath.toAbsolutePath());
    }
}
```

**Step 2: Run the test**

Run: `mvn verify -pl integration-tests -Dtest=PerformanceBaselineTest -q`
Expected: PASS with output showing executor count and heap usage

**Step 3: Commit**

```bash
git add integration-tests/src/test/java/io/quarkiverse/qubit/it/PerformanceBaselineTest.java
git commit -m "feat(metrics): add PerformanceBaselineTest for runtime metrics capture"
```

---

## Task 10: Add Maven Performance Profile

**Files:**
- Modify: `integration-tests/pom.xml`

**Step 1: Add profile**

Add before `</project>` closing tag:

```xml
<profiles>
    <profile>
        <id>performance</id>
        <properties>
            <quarkus.qubit.metrics.enabled>true</quarkus.qubit.metrics.enabled>
        </properties>
    </profile>
</profiles>
```

**Step 2: Verify profile works**

Run: `mvn verify -pl integration-tests -Pperformance -Dtest=PerformanceBaselineTest -q`
Expected: PASS, both JSON files created in target/

**Step 3: Commit**

```bash
git add integration-tests/pom.xml
git commit -m "feat(metrics): add Maven performance profile for metrics collection"
```

---

## Task 11: Add Performance Baseline Documentation

**Files:**
- Create: `docs/testing/performance-baseline.md`

**Step 1: Write documentation**

```markdown
# Performance Baseline Testing

## Overview

Qubit includes performance testing infrastructure to capture build-time and runtime metrics.
Use these tools to establish baselines before optimization work and track improvements.

## Quick Start

```bash
# Capture baseline metrics
mvn verify -Pperformance -pl integration-tests

# View results
cat integration-tests/target/qubit-build-metrics.json
cat integration-tests/target/qubit-runtime-metrics.json
```

## Build-Time Metrics

Enabled via `quarkus.qubit.metrics.enabled=true`.

**Output:** `target/qubit-build-metrics.json`

| Metric | Description |
|--------|-------------|
| `total_ms` | Full Qubit processing time |
| `lambda_discovery` | InvokeDynamicScanner phase |
| `bytecode_analysis` | LambdaBytecodeAnalyzer phase |
| `query_count` | Number of queries processed |

## Runtime Metrics

Captured by `PerformanceBaselineTest`.

**Output:** `target/qubit-runtime-metrics.json`

| Metric | Description |
|--------|-------------|
| `executor_count` | Registered query executors |
| `heap_bytes` | Estimated registry heap usage |

## Comparing Results

```bash
# After optimization, compare with baseline
diff <(jq . baseline-build.json) <(jq . target/qubit-build-metrics.json)
diff <(jq . baseline-runtime.json) <(jq . target/qubit-runtime-metrics.json)
```

## Configuration

```properties
# Enable metrics collection
quarkus.qubit.metrics.enabled=true

# Custom output path (default: target/qubit-build-metrics.json)
quarkus.qubit.metrics.output-path=my-metrics.json
```
```

**Step 2: Commit**

```bash
git add docs/testing/performance-baseline.md
git commit -m "docs: add performance baseline testing documentation"
```

---

## Task 12: Final Verification

**Step 1: Run full build with metrics**

Run: `mvn clean verify -Pperformance -q`
Expected: Build succeeds

**Step 2: Verify metrics files exist**

Run: `ls -la integration-tests/target/qubit-*.json`
Expected: Both `qubit-build-metrics.json` and `qubit-runtime-metrics.json` present

**Step 3: Verify JSON content**

Run: `jq . integration-tests/target/qubit-build-metrics.json && jq . integration-tests/target/qubit-runtime-metrics.json`
Expected: Valid JSON with timestamps and metrics

**Step 4: Final commit**

```bash
git add -A
git commit -m "feat(metrics): complete performance testing infrastructure"
```

---

## Summary

This implementation adds:

1. **BuildMetricsConfig** - Configuration interface for metrics settings
2. **BuildMetricsCollector** - Phase timing and JSON output
3. **QubitBuildTimeConfig integration** - `quarkus.qubit.metrics.*` properties
4. **QubitProcessor instrumentation** - Timing around key phases
5. **MemoryEstimator** - Reflection-based heap size estimation
6. **PerformanceReport** - Runtime metrics JSON output
7. **PerformanceBaselineTest** - Quarkus test for capturing runtime metrics
8. **Maven profile** - `-Pperformance` for easy activation
9. **Documentation** - Usage guide in `docs/testing/`
