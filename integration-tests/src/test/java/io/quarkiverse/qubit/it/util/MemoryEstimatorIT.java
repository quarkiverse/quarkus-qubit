package io.quarkiverse.qubit.it.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

class MemoryEstimatorIT {

    @Test
    void estimatesPrimitiveSize() {
        long size = MemoryEstimator.estimateDeepSize(42);
        // Integer object: header (12-16 bytes) + int value (4 bytes)
        assertThat(size).isGreaterThan(0);
    }

    @Test
    void estimatesStringSize() {
        String str = "Hello, World!";
        long size = MemoryEstimator.estimateDeepSize(str);
        // String has char array backing
        assertThat(size).isGreaterThan(str.length());
    }

    @Test
    void estimatesMapSize() {
        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);

        long size = MemoryEstimator.estimateDeepSize(map);
        assertThat(size).isGreaterThan(0);
    }

    @Test
    void handlesNullGracefully() {
        long size = MemoryEstimator.estimateDeepSize(null);
        assertThat(size).isZero();
    }

    @Test
    void handlesCyclesGracefully() {
        Object[] cycle = new Object[1];
        cycle[0] = cycle; // Self-reference

        long size = MemoryEstimator.estimateDeepSize(cycle);
        assertThat(size).isGreaterThan(0); // Should not stack overflow
    }
}
