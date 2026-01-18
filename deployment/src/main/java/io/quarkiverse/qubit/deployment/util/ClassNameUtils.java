package io.quarkiverse.qubit.deployment.util;

/** Utility for extracting simple class names from fully-qualified or JVM internal names. */
public final class ClassNameUtils {

    private ClassNameUtils() {
        // Utility class
    }

    /** Extracts simple name from FQCN (e.g., "com.example.Product" → "Product"). Returns "Entity" if null/empty. */
    public static String extractSimpleName(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return "Entity";
        }
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    /** Converts JVM internal name (using '/') to simple name (e.g., "com/example/Product" → "Product"). */
    public static String extractSimpleNameFromInternal(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return "Entity";
        }
        // Convert JVM internal name format to standard format, then extract
        return extractSimpleName(internalName.replace('/', '.'));
    }
}
