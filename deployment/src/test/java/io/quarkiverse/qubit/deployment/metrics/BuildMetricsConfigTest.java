package io.quarkiverse.qubit.deployment.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuildMetricsConfigTest {

    @Test
    void enabledDefaultsToFalse() {
        BuildMetricsConfig config = createConfig(false, "target/qubit-build-metrics.json");
        assertThat(config.enabled()).isFalse();
    }

    @Test
    void outputPathDefaultValue() {
        BuildMetricsConfig config = createConfig(false, "target/qubit-build-metrics.json");
        assertThat(config.outputPath()).isEqualTo("target/qubit-build-metrics.json");
    }

    @Test
    void customOutputPath() {
        BuildMetricsConfig config = createConfig(true, "custom/path/metrics.json");
        assertThat(config.outputPath()).isEqualTo("custom/path/metrics.json");
    }

    private BuildMetricsConfig createConfig(boolean enabled, String outputPath) {
        return new BuildMetricsConfig() {
            @Override public boolean enabled() { return enabled; }
            @Override public String outputPath() { return outputPath; }
        };
    }
}
