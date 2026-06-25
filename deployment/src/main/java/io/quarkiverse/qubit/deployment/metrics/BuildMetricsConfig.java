package io.quarkiverse.qubit.deployment.metrics;

import io.smallrye.config.WithDefault;

/**
 * Configuration for build-time performance metrics collection.
 * Nested under quarkus.qubit.metrics in QubitBuildTimeConfig.
 */
public interface BuildMetricsConfig {

    /** Enable build-time metrics collection. */
    @WithDefault("false")
    boolean enabled();

    /** Output path for build metrics JSON file. */
    @WithDefault("target/qubit-build-metrics.json")
    String outputPath();

    /** Enable flame graph compatible output. */
    @WithDefault("false")
    boolean flameGraph();

    /** Output path for flame graph collapsed stacks file. */
    @WithDefault("target/qubit-flamegraph.collapsed")
    String flameGraphPath();
}
