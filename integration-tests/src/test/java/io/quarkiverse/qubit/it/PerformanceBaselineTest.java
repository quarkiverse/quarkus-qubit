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
