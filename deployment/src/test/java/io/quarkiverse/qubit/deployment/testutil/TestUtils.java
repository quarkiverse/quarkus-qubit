package io.quarkiverse.qubit.deployment.testutil;

/** Simple utility methods for testing constant folding. */
public final class TestUtils {

    private TestUtils() {
    }

    /** Converts string to uppercase. */
    public static String toUpper(String s) {
        return s.toUpperCase();
    }

    /** Adds a prefix to a string. */
    public static String withPrefix(String prefix, String s) {
        return prefix + s;
    }

    /** Doubles an integer value. */
    public static int doubleValue(int x) {
        return x * 2;
    }
}
