package io.quarkiverse.qubit.benchmarks;

/**
 * A single benchmark measurement.
 *
 * @param testName name of the benchmark test (e.g. "simpleWhere")
 * @param metric what was measured (e.g. "throughput", "p99")
 * @param value the measured value
 * @param unit unit of the value (e.g. "ops/sec", "ns")
 */
record BenchmarkResult(String testName, String metric, double value, String unit) {
}
