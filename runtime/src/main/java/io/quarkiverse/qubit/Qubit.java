package io.quarkiverse.qubit;

/**
 * Marker class for Qubit query operations that have no direct Java equivalent.
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
 * // Arbitrary LIKE pattern with % and _ wildcards
 * Person.where(p -> Qubit.like(p.email, "%@%.com")).toList();
 * }</pre>
 *
 * @see QubitMath for mathematical marker methods
 */
public final class Qubit {

    private Qubit() {
    }

    /**
     * Matches a string field against a SQL LIKE pattern.
     *
     * <p>
     * Use {@code %} for any sequence of characters and {@code _} for a single character.
     * Maps to {@code CriteriaBuilder.like(Expression, String)} at build time.
     *
     * <p>
     * For common prefix/suffix/substring matching, prefer the native Java methods:
     * {@code startsWith()}, {@code endsWith()}, {@code contains()}.
     *
     * @param value the string field to match
     * @param pattern the LIKE pattern with {@code %} and {@code _} wildcards
     * @return always true (never executed at runtime)
     */
    public static boolean like(String value, String pattern) {
        return true; // Never executed — intercepted at build time
    }

    /**
     * Matches a string field against a SQL NOT LIKE pattern.
     *
     * <p>
     * Use {@code %} for any sequence of characters and {@code _} for a single character.
     * Maps to {@code CriteriaBuilder.not(CriteriaBuilder.like(Expression, String))} at build time.
     *
     * @param value the string field to match
     * @param pattern the LIKE pattern with {@code %} and {@code _} wildcards
     * @return always true (never executed at runtime)
     */
    public static boolean notLike(String value, String pattern) {
        return true; // Never executed — intercepted at build time
    }

    /**
     * Returns the leftmost {@code length} characters of the string field.
     * Maps to {@code CriteriaBuilder.left(Expression, int)} at build time.
     *
     * @param value the string field
     * @param length the number of characters from the left
     * @return the value unchanged (never executed at runtime)
     */
    public static String left(String value, int length) {
        return value;
    }

    /**
     * Returns the rightmost {@code length} characters of the string field.
     * Maps to {@code CriteriaBuilder.right(Expression, int)} at build time.
     *
     * @param value the string field
     * @param length the number of characters from the right
     * @return the value unchanged (never executed at runtime)
     */
    public static String right(String value, int length) {
        return value;
    }

    /**
     * Extracts the calendar quarter (1-4) from a date field.
     * Maps to {@code CriteriaBuilder.extract(LocalDateField.QUARTER, Expression)} at build time.
     *
     * @param date the LocalDate field to extract the quarter from
     * @return always 0 (never executed at runtime)
     */
    public static int quarter(java.time.LocalDate date) {
        return 0; // Never executed — intercepted at build time
    }

    /**
     * Extracts the calendar quarter (1-4) from a datetime field.
     * Maps to {@code CriteriaBuilder.extract(LocalDateTimeField.QUARTER, Expression)} at build time.
     *
     * @param dateTime the LocalDateTime field to extract the quarter from
     * @return always 0 (never executed at runtime)
     */
    public static int quarter(java.time.LocalDateTime dateTime) {
        return 0; // Never executed — intercepted at build time
    }

    /**
     * Extracts the ISO-8601 week number from a date field.
     * Maps to {@code CriteriaBuilder.extract(LocalDateField.WEEK, Expression)} at build time.
     *
     * @param date the LocalDate field to extract the week from
     * @return always 0 (never executed at runtime)
     */
    public static int week(java.time.LocalDate date) {
        return 0; // Never executed — intercepted at build time
    }

    /**
     * Extracts the ISO-8601 week number from a datetime field.
     * Maps to {@code CriteriaBuilder.extract(LocalDateTimeField.WEEK, Expression)} at build time.
     *
     * @param dateTime the LocalDateTime field to extract the week from
     * @return always 0 (never executed at runtime)
     */
    public static int week(java.time.LocalDateTime dateTime) {
        return 0; // Never executed — intercepted at build time
    }
}
