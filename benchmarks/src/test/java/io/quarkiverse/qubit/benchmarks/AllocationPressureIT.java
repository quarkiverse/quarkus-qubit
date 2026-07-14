package io.quarkiverse.qubit.benchmarks;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AllocationPressureIT {

    private static final int BATCH_SIZE = 500;
    private static final List<BenchmarkResult> results = new ArrayList<>();

    @AfterAll
    static void report() {
        BenchmarkReport.writeResults(results, "allocation-results");
        System.out.println("\n=== Allocation Pressure Results ===");
        for (var r : results) {
            System.out.printf("  %-30s %,.0f %s%n", r.testName() + "/" + r.metric(), r.value(), r.unit());
        }
    }

    @Test
    void simpleWhere() {
        measureAllocation("simpleWhere", () -> QueryRunner.simpleWhere(25));
    }

    @Test
    void projection() {
        measureAllocation("projection", QueryRunner::projection);
    }

    @Test
    void joinQuery() {
        measureAllocation("joinQuery", QueryRunner::joinQuery);
    }

    @Test
    void countQuery() {
        measureAllocation("countQuery", () -> QueryRunner.countQuery(true));
    }

    private void measureAllocation(String name, Runnable query) {
        for (int i = 0; i < 100; i++) {
            query.run();
        }

        Runtime rt = Runtime.getRuntime();
        System.gc();
        System.gc();
        long gcBefore = totalGcCount();
        long heapBefore = rt.totalMemory() - rt.freeMemory();

        for (int i = 0; i < BATCH_SIZE; i++) {
            query.run();
        }

        long heapAfter = rt.totalMemory() - rt.freeMemory();
        long gcAfter = totalGcCount();

        results.add(new BenchmarkResult(name, "heapDelta", heapAfter - heapBefore, "bytes"));
        results.add(new BenchmarkResult(name, "gcEvents", gcAfter - gcBefore, "count"));
        results.add(new BenchmarkResult(name, "bytesPerQuery", (double) (heapAfter - heapBefore) / BATCH_SIZE,
                "bytes/query"));
    }

    private static long totalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0) {
                total += count;
            }
        }
        return total;
    }
}
