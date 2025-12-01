package io.quarkiverse.qubit.deployment.common;

/**
 * Holds entity class information including both the Class object and optional class name.
 *
 * <p>The className is only set when the class cannot be loaded at build-time.
 * In this case, the class will be Object.class and className will contain the
 * fully qualified name for runtime resolution.
 *
 * <p>Extracted from SubqueryAnalyzer (ARCH-008 continuation) to provide
 * a reusable record for entity class information.
 *
 * @param clazz the entity class (may be Object.class for placeholders)
 * @param className optional entity class name (for placeholders when class not loadable)
 */
public record EntityClassInfo(Class<?> clazz, String className) {

    /**
     * Creates an EntityClassInfo with a loaded class.
     *
     * @param clazz the loaded entity class
     * @return entity class info with no className (class was successfully loaded)
     */
    public static EntityClassInfo of(Class<?> clazz) {
        return new EntityClassInfo(clazz, null);
    }

    /**
     * Creates an EntityClassInfo for a class that couldn't be loaded at build-time.
     *
     * @param className the fully qualified class name for runtime resolution
     * @return entity class info with Object.class placeholder and className set
     */
    public static EntityClassInfo placeholder(String className) {
        return new EntityClassInfo(Object.class, className);
    }

    /**
     * Returns true if this represents a placeholder (class not loadable at build-time).
     *
     * @return true if className is set (class needs runtime resolution)
     */
    public boolean isPlaceholder() {
        return className != null;
    }

    /**
     * Returns the effective class name for logging/debugging.
     *
     * @return the class name, either from the loaded class or the placeholder className
     */
    public String getEffectiveClassName() {
        return className != null ? className : clazz.getName();
    }
}
