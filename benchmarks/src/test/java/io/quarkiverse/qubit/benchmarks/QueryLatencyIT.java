package io.quarkiverse.qubit.benchmarks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class QueryLatencyIT {

    private static final int WARMUP = 100;
    private static final int MEASUREMENTS = 500;
    private static final List<BenchmarkResult> results = new ArrayList<>();

    @AfterAll
    static void report() {
        BenchmarkReport.writeResults(results, "latency-results");
        System.out.println("\n=== Latency Results ===");
        for (var r : results) {
            System.out.printf("  %-30s %,.0f %s%n", r.testName() + "/" + r.metric(), r.value(), r.unit());
        }
    }

    @Test
    void simpleWhere() {
        collectLatencies("simpleWhere", () -> QueryRunner.simpleWhere(25));
    }

    @Test
    void projection() {
        collectLatencies("projection", QueryRunner::projection);
    }

    @Test
    void joinQuery() {
        collectLatencies("joinQuery", QueryRunner::joinQuery);
    }

    @Test
    void groupByQuery() {
        collectLatencies("groupByQuery", QueryRunner::groupByQuery);
    }

    @Test
    void aggregation() {
        collectLatencies("aggregation", QueryRunner::aggregation);
    }

    @Test
    void countQuery() {
        collectLatencies("countQuery", () -> QueryRunner.countQuery(true));
    }

    private void collectLatencies(String name, Runnable query) {
        for (int i = 0; i < WARMUP; i++) {
            query.run();
        }

        long[] latencies = new long[MEASUREMENTS];
        for (int i = 0; i < MEASUREMENTS; i++) {
            long start = System.nanoTime();
            query.run();
            latencies[i] = System.nanoTime() - start;
        }

        Arrays.sort(latencies);
        double avg = Arrays.stream(latencies).average().orElse(0);
        long p50 = latencies[(int) (MEASUREMENTS * 0.50)];
        long p90 = latencies[(int) (MEASUREMENTS * 0.90)];
        long p99 = latencies[(int) (MEASUREMENTS * 0.99)];

        results.add(new BenchmarkResult(name, "avg", avg, "ns"));
        results.add(new BenchmarkResult(name, "p50", p50, "ns"));
        results.add(new BenchmarkResult(name, "p90", p90, "ns"));
        results.add(new BenchmarkResult(name, "p99", p99, "ns"));
    }
}
