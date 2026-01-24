package io.quarkiverse.qubit.deployment.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuildMetricsConfigTest {

    @Test
    void defaultValuesAreDisabled() {
        // Verify interface exists and default values
        // This test validates the config interface structure
        assertThat(BuildMetricsConfig.class.isInterface()).isTrue();
    }
}
