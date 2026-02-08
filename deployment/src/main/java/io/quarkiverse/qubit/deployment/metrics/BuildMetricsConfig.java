package io.quarkiverse.qubit.deployment.metrics;

import io.smallrye.config.WithDefault;

/**
 * Configuration for build-time performance metrics collection.
 * Nested under quarkus.qubit.metrics in QubitBuildTimeConfig.
 *
 * <p>Levels: basic (phase timing), detailed (+cache/query breakdown), full (+histograms).
 * Outputs JSON and optional flame graph format.
 */
public interface BuildMetricsConfig {

    /** Enable build-time metrics collection. */
    @WithDefault("false")
    boolean enabled();

    /** Output path for build metrics JSON file. */
    @WithDefault("target/qubit-build-metrics.json")
    String outputPath();

    /** Metrics detail level: basic, detailed, or full. */
    @WithDefault("detailed")
    String level();

    /** Enable flame graph compatible output. */
    @WithDefault("false")
    boolean flameGraph();

    /** Output path for flame graph collapsed stacks file. */
    @WithDefault("target/qubit-flamegraph.collapsed")
    String flameGraphPath();

    /** Maximum histogram samples per distribution (for percentile accuracy). */
    @WithDefault("10000")
    int maxHistogramSamples();

    /**
     * Include top N classes by analysis time in the report.
     */
    @WithDefault("10")
    int topClassCount();

    // Convenience methods for level checking
    default boolean isBasicLevel() {
        return "basic".equalsIgnoreCase(level());
    }

    default boolean isDetailedLevel() {
        return "detailed".equalsIgnoreCase(level());
    }

    default boolean isFullLevel() {
        return "full".equalsIgnoreCase(level());
    }

    /** Returns true if level includes detailed metrics (detailed or full). */
    default boolean includesDetailedMetrics() {
        return isDetailedLevel() || isFullLevel();
    }

    /** Returns true if level includes full metrics (only full). */
    default boolean includesFullMetrics() {
        return isFullLevel();
    }
}
