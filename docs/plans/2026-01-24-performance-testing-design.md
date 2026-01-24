# Performance Testing Design

**Date:** 2026-01-24
**Status:** Approved
**Goal:** Establish optimization baseline for build-time performance and runtime memory

## Overview

Add performance testing infrastructure to measure:
- **Build-time performance:** Full pipeline timing + key stage breakdown
- **Runtime memory:** Heap usage of QueryExecutorRegistry and cached executors

Use existing ~50 query patterns from integration tests as the test dataset.

## Architecture

### Components

1. **Build-Time Metrics Collection**
   - Instrument `QubitProcessor` to collect timing data for each phase
   - Use `System.nanoTime()` with structured JSON output
   - Controlled by build-time config property

2. **Runtime Memory Measurement**
   - `@QuarkusTest` that queries `QueryExecutorRegistry` after startup
   - Reflection-based deep size estimation (no external dependencies)
   - Outputs to JSON file

### Output Format

Both components write JSON files that can be compared across builds:
- `target/qubit-build-metrics.json`
- `target/qubit-runtime-metrics.json`

## Build-Time Instrumentation

### New Class: BuildMetricsCollector

```java
// deployment/src/main/java/io/quarkiverse/qubit/deployment/metrics/BuildMetricsCollector.java
public class BuildMetricsCollector {
    private long startTime;
    private final Map<String, Long> phaseDurations = new LinkedHashMap<>();
    private int queryCount;

    public void startPhase(String phase) { ... }
    public void endPhase(String phase) { ... }
    public void incrementQueryCount() { ... }
    public void writeReport(Path outputPath) { ... }
}
```

### Integration Points

Wrap existing logic with timing calls:
1. **Lambda Discovery** - Around `InvokeDynamicScanner.scanForLambdaCallSites()`
2. **Bytecode Analysis** - Around `LambdaBytecodeAnalyzer.analyze()` loop
3. **Deduplication** - Around `LambdaDeduplicator.deduplicate()`
4. **Code Generation** - Around `QueryExecutorClassGenerator.generate()` loop

### Configuration

```properties
quarkus.qubit.metrics.enabled=true
quarkus.qubit.metrics.output-path=target/qubit-build-metrics.json
```

Default: disabled (no overhead in normal builds).

### Sample Output

```json
{
  "timestamp": "2026-01-24T10:30:00Z",
  "total_ms": 245,
  "phases": {
    "lambda_discovery": 42,
    "bytecode_analysis": 156,
    "deduplication": 12,
    "code_generation": 35
  },
  "query_count": 47
}
```

## Runtime Memory Measurement

### Test Class

```java
// integration-tests/src/test/java/io/quarkiverse/qubit/it/PerformanceBaselineTest.java
@QuarkusTest
@TestProfile(PerformanceTestProfile.class)
public class PerformanceBaselineTest {

    @Inject
    QueryExecutorRegistry registry;

    @Test
    void captureRuntimeMemoryBaseline() {
        int executorCount = registry.getRegisteredExecutorCount();
        long heapBytes = MemoryEstimator.estimateDeepSize(registry);

        PerformanceReport.writeRuntimeMetrics(
            executorCount,
            heapBytes,
            Path.of("target/qubit-runtime-metrics.json")
        );
    }
}
```

### Memory Estimation

Reflection-based deep size estimator:
- Walk object graph via reflection
- Sum field sizes using known primitive sizes
- Handle cycles with identity set
- Rough estimate (±20% accuracy is acceptable for baseline)

## File Structure

```
deployment/
└── src/main/java/io/quarkiverse/qubit/deployment/
    └── metrics/
        ├── BuildMetricsCollector.java
        └── BuildMetricsConfig.java

integration-tests/
└── src/test/java/io/quarkiverse/qubit/it/
    ├── PerformanceBaselineTest.java
    ├── PerformanceTestProfile.java
    └── util/
        └── MemoryEstimator.java

docs/testing/
└── performance-baseline.md
```

## Maven Profile

```xml
<profile>
    <id>performance</id>
    <properties>
        <quarkus.qubit.metrics.enabled>true</quarkus.qubit.metrics.enabled>
    </properties>
</profile>
```

## Usage

```bash
# Capture baseline
mvn verify -Pperformance -pl integration-tests

# View results
cat integration-tests/target/qubit-build-metrics.json
cat integration-tests/target/qubit-runtime-metrics.json

# Compare after optimization
diff <(jq . baseline.json) <(jq . current.json)
```

## Metrics Captured

| Category | Metric | Description |
|----------|--------|-------------|
| Build | `total_ms` | Full Qubit processing time |
| Build | `lambda_discovery` | InvokeDynamicScanner phase |
| Build | `bytecode_analysis` | LambdaBytecodeAnalyzer phase |
| Build | `deduplication` | LambdaDeduplicator phase |
| Build | `code_generation` | QueryExecutorClassGenerator phase |
| Build | `query_count` | Number of queries processed |
| Runtime | `executor_count` | Executors registered |
| Runtime | `heap_bytes` | Estimated heap usage |

## Implementation Notes

- Metrics collection disabled by default (zero overhead)
- No external dependencies required
- JSON output for easy comparison and scripting
- Profile-based activation for CI integration readiness
