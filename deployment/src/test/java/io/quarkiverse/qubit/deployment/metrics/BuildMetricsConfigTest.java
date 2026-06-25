package io.quarkiverse.qubit.deployment.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class BuildMetricsConfigTest {

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
    void flameGraphDefaultsToFalse() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.flameGraph()).isFalse();
    }

    @Test
    void flameGraphPathDefaultValue() {
        BuildMetricsConfig config = createConfigWithDefaults();
        assertThat(config.flameGraphPath()).isEqualTo("target/qubit-flamegraph.collapsed");
    }
}
