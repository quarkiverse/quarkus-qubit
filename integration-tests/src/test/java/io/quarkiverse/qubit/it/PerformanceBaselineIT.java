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
class PerformanceBaselineIT {

    @Inject
    QueryExecutorRegistry registry;

    @Test
    void captureRuntimeMemoryBaseline() throws Exception {
        // Get executor count
        int executorCount = QueryExecutorRegistry.getRegisteredExecutorCount();
        assertThat(executorCount)
            .as("Should have registered query executors from integration tests")
            .isGreaterThan(0);

        // Estimate heap usage
        long heapBytes = MemoryEstimator.estimateDeepSize(registry);
        assertThat(heapBytes)
            .as("Registry should occupy some heap space")
            .isGreaterThan(0);

        // Write runtime metrics to JSON (consumed by performance analysis scripts)
        Path outputPath = Path.of("target/qubit-runtime-metrics.json");
        PerformanceReport.writeRuntimeMetrics(executorCount, heapBytes, outputPath);
    }
}
