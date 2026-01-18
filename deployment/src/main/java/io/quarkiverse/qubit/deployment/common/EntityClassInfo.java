package io.quarkiverse.qubit.deployment.common;

/**
 * Entity class info with optional className for build-time unloadable classes.
 * When className is set, clazz is Object.class as a placeholder for runtime resolution.
 */
public record EntityClassInfo(Class<?> clazz, String className) {

    /** Creates info for a successfully loaded class. */
    public static EntityClassInfo of(Class<?> clazz) {
        return new EntityClassInfo(clazz, null);
    }

    /** Creates placeholder for a class not loadable at build-time. */
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
