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
