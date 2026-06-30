package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BuilderResultTest {

    @Test
    void success_throwsForNullValue() {
        assertThatThrownBy(() -> new BuilderResult.Success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cannot be null");
    }
}
