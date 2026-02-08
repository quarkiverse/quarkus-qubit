package io.quarkiverse.qubit.deployment.metrics;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuildMetricsConfigTest {

    /** Creates config with actual @WithDefault values applied. */
    private BuildMetricsConfig createConfigWithDefaults() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withMapping(BuildMetricsConfig.class, "test")
                .build();
        return config.getConfigMapping(BuildMetricsConfig.class, "test");
    }

    @Test
    void enabledDefaultsToFalse() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.enabled()).isFalse();
    }

    @Test
    void outputPathDefaultValue() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.outputPath()).isEqualTo("target/qubit-build-metrics.json");
    }

    @Test
    void levelDefaultsToDetailed() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.level()).isEqualTo("detailed");
        assertThat(config.isDetailedLevel()).isTrue();
        assertThat(config.isBasicLevel()).isFalse();
        assertThat(config.isFullLevel()).isFalse();
    }

    @Test
    void flameGraphDefaultsToFalse() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.flameGraph()).isFalse();
    }

    @Test
    void flameGraphPathDefaultValue() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.flameGraphPath()).isEqualTo("target/qubit-flamegraph.collapsed");
    }

    @Test
    void maxHistogramSamplesDefaultValue() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.maxHistogramSamples()).isEqualTo(10000);
    }

    @Test
    void topClassCountDefaultValue() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.topClassCount()).isEqualTo(10);
    }

    @Test
    void levelBasicIncludesChecks() {
        BuildMetricsConfig config = createConfig("basic");
        assertThat(config.isBasicLevel()).isTrue();
        assertThat(config.isDetailedLevel()).isFalse();
        assertThat(config.isFullLevel()).isFalse();
        assertThat(config.includesDetailedMetrics()).isFalse();
        assertThat(config.includesFullMetrics()).isFalse();
    }

    @Test
    void levelDetailedIncludesChecks() {
        BuildMetricsConfig config = createConfig("detailed");
        assertThat(config.isDetailedLevel()).isTrue();
        assertThat(config.includesDetailedMetrics()).isTrue();
        assertThat(config.includesFullMetrics()).isFalse();
    }

    @Test
    void levelFullIncludesChecks() {
        BuildMetricsConfig config = createConfig("full");
        assertThat(config.isFullLevel()).isTrue();
        assertThat(config.isBasicLevel()).isFalse();
        assertThat(config.isDetailedLevel()).isFalse();
        assertThat(config.includesDetailedMetrics()).isTrue();
        assertThat(config.includesFullMetrics()).isTrue();
    }

    /** Creates config with specific level for testing level helper methods. */
    private BuildMetricsConfig createConfig(String level) {
        return new BuildMetricsConfig() {
            @Override public boolean enabled() { return false; }
            @Override public String outputPath() { return "path"; }
            @Override public String level() { return level; }
            @Override public boolean flameGraph() { return false; }
            @Override public String flameGraphPath() { return "fg"; }
            @Override public int maxHistogramSamples() { return 10000; }
            @Override public int topClassCount() { return 10; }
        };
    }
}
