package io.quarkiverse.qubit.deployment;

import org.objectweb.asm.Type;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Enumeration of fluent API method types for the Qubit extension.
 *
 * <p>This enum replaces the string constants and switch statements previously used
 * for dispatching fluent method configuration creation. Each enum value encapsulates:
 * <ul>
 *   <li>The method name (used for lookup from bytecode)</li>
 *   <li>The method category (predicate, projection, sorting, aggregation)</li>
 *   <li>Factory method for creating {@link QubitBytecodeGenerator.FluentMethodConfig}</li>
 * </ul>
 *
 * <p><b>Design Rationale:</b> The enum is placed in the deployment module rather than
 * runtime because the {@code createConfig()} behavior depends on
 * {@link QubitBytecodeGenerator.FluentMethodConfig} which is deployment-only.
 * String constants remain in {@code QubitConstants} for usage patterns that require
 * string comparison (e.g., bytecode analysis in {@code InvokeDynamicScanner}).
 *
 * @see QubitBytecodeGenerator.FluentMethodConfig
 * @see QubitRepositoryEnhancer
 */
public enum FluentMethodType {

    // =============================================================================================
    // PREDICATE METHODS
    // =============================================================================================

    /**
     * The {@code where()} method for filtering with a predicate.
     */
    WHERE("where", MethodCategory.PREDICATE) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forWhere(entityType, entityInternalName);
        }
    },

    // =============================================================================================
    // PROJECTION METHODS
    // =============================================================================================

    /**
     * The {@code select()} method for projecting to a different type.
     */
    SELECT("select", MethodCategory.PROJECTION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSelect(entityType, entityInternalName);
        }
    },

    // =============================================================================================
    // SORTING METHODS
    // =============================================================================================

    /**
     * The {@code sortedBy()} method for ascending order sorting.
     */
    SORTED_BY("sortedBy", MethodCategory.SORTING) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSortedBy(entityType, entityInternalName);
        }
    },

    /**
     * The {@code sortedDescendingBy()} method for descending order sorting.
     */
    SORTED_DESCENDING_BY("sortedDescendingBy", MethodCategory.SORTING) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSortedDescendingBy(entityType, entityInternalName);
        }
    },

    // =============================================================================================
    // AGGREGATION METHODS
    // =============================================================================================

    /**
     * The {@code min()} method for finding minimum value.
     */
    MIN("min", MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forMin(entityType, entityInternalName);
        }
    },

    /**
     * The {@code max()} method for finding maximum value.
     */
    MAX("max", MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forMax(entityType, entityInternalName);
        }
    },

    /**
     * The {@code avg()} method for computing average.
     */
    AVG("avg", MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forAvg(entityType, entityInternalName);
        }
    },

    /**
     * The {@code sumInteger()} method for summing Integer values.
     */
    SUM_INTEGER("sumInteger", MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumInteger(entityType, entityInternalName);
        }
    },

    /**
     * The {@code sumLong()} method for summing Long values.
     */
    SUM_LONG("sumLong", MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumLong(entityType, entityInternalName);
        }
    },

    /**
     * The {@code sumDouble()} method for summing Double values.
     */
    SUM_DOUBLE("sumDouble", MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumDouble(entityType, entityInternalName);
        }
    };

    // =============================================================================================
    // ENUM INFRASTRUCTURE
    // =============================================================================================

    private final String methodName;
    private final MethodCategory category;

    FluentMethodType(String methodName, MethodCategory category) {
        this.methodName = methodName;
        this.category = category;
    }

    /**
     * Returns the method name as it appears in Java code.
     *
     * @return the method name
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns the category of this method.
     *
     * @return the method category
     */
    public MethodCategory getCategory() {
        return category;
    }

    /**
     * Creates a {@link QubitBytecodeGenerator.FluentMethodConfig} for this method type.
     *
     * <p>This is the behavior-rich aspect of the enum - each value knows how to
     * create its own configuration, eliminating the need for switch statements.
     *
     * @param entityType the ASM Type of the entity class
     * @param entityInternalName the JVM internal name of the entity class
     * @return the fluent method configuration
     */
    public abstract QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName);

    /**
     * Looks up a FluentMethodType by its method name.
     *
     * @param methodName the method name to look up
     * @return an Optional containing the enum value, or empty if not found
     */
    public static Optional<FluentMethodType> fromMethodName(String methodName) {
        if (methodName == null) {
            return Optional.empty();
        }
        for (FluentMethodType type : values()) {
            if (type.methodName.equals(methodName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if this method type is an aggregation method.
     *
     * @return true if this is an aggregation method (min, max, avg, sum*)
     */
    public boolean isAggregation() {
        return category == MethodCategory.AGGREGATION;
    }

    // =============================================================================================
    // ENUM SETS FOR CATEGORY MEMBERSHIP
    // =============================================================================================

    /**
     * All fluent API entry point methods.
     *
     * <p>These are methods that can start a query pipeline (static methods on QubitEntity).
     * All fluent method types in this enum are entry points.
     */
    public static final EnumSet<FluentMethodType> ENTRY_POINTS =
            EnumSet.allOf(FluentMethodType.class);

    /**
     * All aggregation methods.
     */
    public static final EnumSet<FluentMethodType> AGGREGATIONS = EnumSet.of(
            MIN, MAX, AVG, SUM_INTEGER, SUM_LONG, SUM_DOUBLE
    );

    /**
     * All sorting methods.
     */
    public static final EnumSet<FluentMethodType> SORTING = EnumSet.of(
            SORTED_BY, SORTED_DESCENDING_BY
    );

    // =============================================================================================
    // METHOD CATEGORY ENUM
    // =============================================================================================

    /**
     * Categories of fluent API methods.
     *
     * <p>Categories provide semantic grouping for methods with similar behavior:
     * <ul>
     *   <li>{@link #PREDICATE} - Filtering methods that return a boolean (where)</li>
     *   <li>{@link #PROJECTION} - Methods that transform the result type (select)</li>
     *   <li>{@link #SORTING} - Methods that order results (sortedBy, sortedDescendingBy)</li>
     *   <li>{@link #AGGREGATION} - Methods that compute aggregate values (min, max, avg, sum*)</li>
     * </ul>
     */
    public enum MethodCategory {
        /**
         * Predicate methods that filter results.
         */
        PREDICATE,

        /**
         * Projection methods that transform result types.
         */
        PROJECTION,

        /**
         * Sorting methods that order results.
         */
        SORTING,

        /**
         * Aggregation methods that compute aggregate values.
         */
        AGGREGATION
    }
}
