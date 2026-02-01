# Performance Comparison Report

**Date:** 2026-02-01
**Optimization:** Parallel Class Scanning + Early Deduplication Registration Fix

## Executive Summary

This report compares build-time performance metrics before and after implementing:
1. **Parallel class scanning** for lambda discovery phase
2. **Early deduplication registration fix** to enable bytecode signature caching

## Test Environment

- **Dataset:** 940 query call sites across 2,105 unique classes
- **Test Method:** PerformanceBaselineIT with 3 runs per configuration
- **JDK:** Java 25
- **Quarkus:** 3.31.1

---

## Raw Benchmark Results

### Baseline (Before Optimization)

| Run | Total (ms) | Lambda Discovery (ms) | Bytecode Analysis (ms) | Duplicates |
|-----|------------|----------------------|------------------------|------------|
| 1   | 662        | 446                  | 204                    | 326        |
| 2   | 514        | 370                  | 135                    | 330        |
| 3   | 582        | 406                  | 169                    | 331        |
| **Avg** | **586** | **407**              | **169**                | **329**    |

### Optimized (After Optimization)

| Run | Total (ms) | Lambda Discovery (ms) | Bytecode Analysis (ms) | Duplicates |
|-----|------------|----------------------|------------------------|------------|
| 1   | 274        | 171                  | 95                     | 326        |
| 2   | 334        | 199                  | 124                    | 331        |
| 3   | 353        | 216                  | 129                    | 330        |
| **Avg** | **320** | **195**              | **116**                | **329**    |

---

## Performance Improvement Summary

| Metric | Baseline | Optimized | Improvement | % Change |
|--------|----------|-----------|-------------|----------|
| **Total Build Time** | 586 ms | 320 ms | -266 ms | **-45.4%** |
| **Lambda Discovery** | 407 ms | 195 ms | -212 ms | **-52.1%** |
| **Bytecode Analysis** | 169 ms | 116 ms | -53 ms | **-31.4%** |

### Key Findings

1. **Lambda Discovery Phase:** The parallel scanning optimization reduced lambda discovery time by **52.1%** (407ms → 195ms), which was the primary bottleneck (69% of build time).

2. **Overall Build Time:** Total Qubit processing time reduced by **45.4%** (586ms → 320ms).

3. **Bytecode Analysis:** Improved by **31.4%** (169ms → 116ms) due to better cache utilization from parallel loading.

---

## Granular Metrics Comparison

### Baseline Metrics (Last Run)
```json
{
  "total_ms": 582,
  "phases": {
    "lambda_discovery": 406,
    "bytecode_analysis": 169
  },
  "granular": {
    "bytecode_load_time_nanos": 126966897,
    "asm_parsing_time_nanos": 41135969,
    "instruction_analysis_time_nanos": 909507393,
    "deduplication_check_time_nanos": 145309367,
    "unique_classes_loaded": 2105,
    "total_bytecode_loads": 2980,
    "duplicate_count": 331,
    "early_deduplication_hits": 0
  }
}
```

### Optimized Metrics (Last Run)
```json
{
  "total_ms": 353,
  "phases": {
    "lambda_discovery": 216,
    "bytecode_analysis": 129
  },
  "granular": {
    "bytecode_load_time_nanos": 756274616,
    "asm_parsing_time_nanos": 81089996,
    "instruction_analysis_time_nanos": 767709439,
    "deduplication_check_time_nanos": 87408798,
    "unique_classes_loaded": 2105,
    "total_bytecode_loads": 2980,
    "duplicate_count": 330,
    "early_deduplication_hits": 0
  }
}
```

### Granular Metrics Analysis

| Metric | Baseline | Optimized | Notes |
|--------|----------|-----------|-------|
| Bytecode Load Time | 127 ms | 756 ms | Higher due to parallel contention (wall-clock vs cumulative) |
| ASM Parsing Time | 41 ms | 81 ms | Cumulative time across threads |
| Instruction Analysis | 910 ms | 768 ms | -15.6% improvement |
| Deduplication Check | 145 ms | 87 ms | -40.0% improvement |

**Note:** The granular metrics show cumulative time across all threads, so parallel execution may show higher values for load/parse operations while wall-clock time is lower.

---

## Historical Context

### Previous Optimization Report (2026-01-25)

The previous optimization cycle (Tasks 1-7) established the baseline we're comparing against:

| Task | Optimization | Impact |
|------|--------------|--------|
| 1 | Granular metrics collection | Measurement infrastructure |
| 2 | Bytecode cache (ConcurrentHashMap) | Reduced disk I/O |
| 3 | ClassNode cache | 98.8% reduction in ASM parsing |
| 4 | Early deduplication check | Skip already-processed signatures |
| 5 | Parallel processing (bytecode analysis) | Multi-core utilization |
| 6 | Batch processing by class | Improved cache locality |
| 7 | ASM flag optimization | 25% reduction in parsing overhead |

### This Session's Optimizations

| Change | Description | Impact |
|--------|-------------|--------|
| Parallel class scanning | Changed lambda discovery to `parallelStream()` | **-52.1%** discovery time |
| Early dedup registration | Fixed missing `registerBytecodeSignature()` call | Enables future early hits |

---

## Code Changes

### QubitProcessor.java
```diff
- List<InvokeDynamicScanner.LambdaCallSite> allCallSites = filteredClasses.stream()
+ // Parallel class scanning: safe because InvokeDynamicScanner.scanClass() creates
+ // fresh local state per invocation and BytecodeLoader uses ConcurrentHashMap cache
+ List<InvokeDynamicScanner.LambdaCallSite> allCallSites = filteredClasses.parallelStream()
```

### CallSiteProcessor.java
- Compute bytecode signature once in `processCallSiteWithHandlers()`
- Pass signature through to `handleSuccessOutcome()`
- Register signature after successful executor generation via `registerBytecodeSignature()`

---

## Verification

All 1,480 integration tests pass after optimization:
```
[INFO] Tests run: 1480, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Conclusion

The parallel class scanning optimization provides a **45% reduction in total build time** by parallelizing the lambda discovery phase, which was previously sequential and represented 69% of the build time. The early deduplication registration fix ensures that bytecode signatures are now cached for potential future early deduplication hits in dev mode scenarios.

## Future Considerations

1. **Early Deduplication in Dev Mode:** The registration fix enables early deduplication for hot reload scenarios where the same call sites are reprocessed.

2. **Memory-Mapped Files:** Evaluated but not implemented - class files are too small (1-50KB) for memory-mapped benefits to exceed setup overhead.

3. **Structured Concurrency (JDK 25):** Could potentially replace ForkJoinPool for better error handling in parallel operations, but would require preview feature enablement.
