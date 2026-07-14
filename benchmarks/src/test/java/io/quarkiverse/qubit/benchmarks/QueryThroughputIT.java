package io.quarkiverse.qubit.benchmarks;

import static io.quarkiverse.qubit.benchmarks.BenchmarkConfig.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class QueryThroughputIT {

    private static final List<BenchmarkResult> results = new ArrayList<>();

    @AfterAll
    static void report() {
        BenchmarkReport.writeResults(results, "throughput-results");
        System.out.println("\n=== Throughput Results ===");
        for (var r : results) {
            System.out.printf("  %-20s %,.0f %s%n", r.testName(), r.value(), r.unit());
        }
    }

    @Test
    void simpleWhere() {
        results.add(measure("simpleWhere", () -> QueryRunner.simpleWhere(25)));
    }

    @Test
    void projection() {
        results.add(measure("projection", QueryRunner::projection));
    }

    @Test
    void joinQuery() {
        results.add(measure("joinQuery", QueryRunner::joinQuery));
    }

    @Test
    void groupByQuery() {
        results.add(measure("groupByQuery", QueryRunner::groupByQuery));
    }

    @Test
    void aggregation() {
        results.add(measure("aggregation", QueryRunner::aggregation));
    }

    @Test
    void countQuery() {
        results.add(measure("countQuery", () -> QueryRunner.countQuery(true)));
    }

    private static BenchmarkResult measure(String name, Runnable query) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            query.run();
        }

        double totalOpsPerSec = 0;
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                query.run();
            }
            long elapsed = System.nanoTime() - start;
            totalOpsPerSec += MEASUREMENT_ITERATIONS / (elapsed / 1_000_000_000.0);
        }

        return new BenchmarkResult(name, "throughput", totalOpsPerSec / RUNS, "ops/sec");
    }
}
