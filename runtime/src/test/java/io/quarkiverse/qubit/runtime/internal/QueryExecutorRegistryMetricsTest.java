package io.quarkiverse.qubit.runtime.internal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

class QueryExecutorRegistryMetricsTest {

    @Test
    void hasGetRegisteredExecutorCountMethod() throws Exception {
        Method method = QueryExecutorRegistry.class.getMethod("getRegisteredExecutorCount");
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}
