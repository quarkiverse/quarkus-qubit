package io.quarkiverse.qubit.deployment.metrics;

import io.smallrye.config.WithDefault;

/**
 * Configuration for build-time performance metrics collection.
 * Nested under quarkus.qubit.metrics in QubitBuildTimeConfig.
 */
public interface BuildMetricsConfig {

    /**
     * Enable build-time metrics collection.
     * When enabled, timing data is collected for each processing phase.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Output path for build metrics JSON file.
     * Relative paths are resolved from the project root.
     */
    @WithDefault("target/qubit-build-metrics.json")
    String outputPath();
}
