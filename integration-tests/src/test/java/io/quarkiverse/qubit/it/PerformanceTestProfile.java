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
                "quarkus.qubit.metrics.output-path", "target/qubit-build-metrics.json");
    }

    @Override
    public String getConfigProfile() {
        return "performance";
    }
}
