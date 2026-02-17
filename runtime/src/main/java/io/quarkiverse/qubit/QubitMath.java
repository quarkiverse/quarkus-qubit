package io.quarkiverse.qubit;

/**
 * Marker class for Qubit math operations that have no direct Java equivalent.
 *
 * <p>
 * Methods in this class are never executed at runtime. During build-time
 * bytecode analysis, calls to these methods are intercepted and replaced
 * with JPA Criteria API expressions.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * Person.where(p -> QubitMath.round(p.salary, 2) > 50000).toList();
 * }</pre>
 */
public final class QubitMath {

    private QubitMath() {
    }

    /**
     * Rounds a numeric value to the specified number of decimal places.
     * Maps to {@code CriteriaBuilder.round(Expression, Integer)} at build time.
     *
     * @param value the numeric value to round
     * @param decimalPlaces the number of decimal places
     * @return the value unchanged (never executed at runtime)
     */
    public static double round(double value, int decimalPlaces) {
        return value;
    }

    /**
     * Rounds a numeric value to the specified number of decimal places (float variant).
     * Maps to {@code CriteriaBuilder.round(Expression, Integer)} at build time.
     *
     * @param value the numeric value to round
     * @param decimalPlaces the number of decimal places
     * @return the value unchanged (never executed at runtime)
     */
    public static float round(float value, int decimalPlaces) {
        return value;
    }
}
