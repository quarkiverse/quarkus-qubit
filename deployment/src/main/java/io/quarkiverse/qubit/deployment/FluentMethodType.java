package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_AVG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_MAX;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_MIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SELECT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SORTED_BY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SORTED_DESCENDING_BY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_DOUBLE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_INTEGER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_LONG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_WHERE;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.Type;

/** Fluent API method types with factory methods for FluentMethodConfig creation. */
public enum FluentMethodType {

    WHERE(METHOD_WHERE, MethodCategory.PREDICATE) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forWhere(entityType, entityInternalName);
        }
    },

    SELECT(METHOD_SELECT, MethodCategory.PROJECTION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSelect(entityType, entityInternalName);
        }
    },

    SORTED_BY(METHOD_SORTED_BY, MethodCategory.SORTING) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSortedBy(entityType, entityInternalName);
        }
    },

    SORTED_DESCENDING_BY(METHOD_SORTED_DESCENDING_BY, MethodCategory.SORTING) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSortedDescendingBy(entityType, entityInternalName);
        }
    },

    MIN(METHOD_MIN, MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forMin(entityType, entityInternalName);
        }
    },

    MAX(METHOD_MAX, MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forMax(entityType, entityInternalName);
        }
    },

    AVG(METHOD_AVG, MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forAvg(entityType, entityInternalName);
        }
    },

    SUM_INTEGER(METHOD_SUM_INTEGER, MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumInteger(entityType, entityInternalName);
        }
    },

    SUM_LONG(METHOD_SUM_LONG, MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumLong(entityType, entityInternalName);
        }
    },

    SUM_DOUBLE(METHOD_SUM_DOUBLE, MethodCategory.AGGREGATION) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumDouble(entityType, entityInternalName);
        }
    };

    private final String methodName;
    private final MethodCategory category;

    FluentMethodType(String methodName, MethodCategory category) {
        this.methodName = methodName;
        this.category = category;
    }

    public String getMethodName() {
        return methodName;
    }

    public MethodCategory getCategory() {
        return category;
    }

    /** Creates FluentMethodConfig for this method type. */
    public abstract QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName);

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

    public boolean isAggregation() {
        return category == MethodCategory.AGGREGATION;
    }

    protected static final Set<FluentMethodType> ENTRY_POINTS = EnumSet.allOf(FluentMethodType.class);

    protected static final Set<FluentMethodType> AGGREGATIONS = EnumSet.of(
            MIN, MAX, AVG, SUM_INTEGER, SUM_LONG, SUM_DOUBLE);

    protected static final Set<FluentMethodType> SORTING = EnumSet.of(SORTED_BY, SORTED_DESCENDING_BY);

    /** Semantic grouping: PREDICATE (filtering), PROJECTION (transform), SORTING, AGGREGATION. */
    public enum MethodCategory {
        PREDICATE, // Filtering methods (where)
        PROJECTION, // Transform result type (select)
        SORTING, // Order results (sortedBy, sortedDescendingBy)
        AGGREGATION // Compute aggregate values (min, max, avg, sum*)
    }
}
