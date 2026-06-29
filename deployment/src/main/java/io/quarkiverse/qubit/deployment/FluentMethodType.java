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

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;

/** Fluent API method types with factory methods for FluentMethodConfig creation. */
public enum FluentMethodType {

    WHERE(METHOD_WHERE) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forWhere(entityType, entityInternalName);
        }
    },

    SELECT(METHOD_SELECT) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSelect(entityType, entityInternalName);
        }
    },

    SORTED_BY(METHOD_SORTED_BY) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSortedBy(entityType, entityInternalName);
        }
    },

    SORTED_DESCENDING_BY(METHOD_SORTED_DESCENDING_BY) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSortedDescendingBy(entityType, entityInternalName);
        }
    },

    MIN(METHOD_MIN) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forMin(entityType, entityInternalName);
        }
    },

    MAX(METHOD_MAX) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forMax(entityType, entityInternalName);
        }
    },

    AVG(METHOD_AVG) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forAvg(entityType, entityInternalName);
        }
    },

    SUM_INTEGER(METHOD_SUM_INTEGER) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumInteger(entityType, entityInternalName);
        }
    },

    SUM_LONG(METHOD_SUM_LONG) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumLong(entityType, entityInternalName);
        }
    },

    SUM_DOUBLE(METHOD_SUM_DOUBLE) {
        @Override
        public QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName) {
            return QubitBytecodeGenerator.FluentMethodConfig.forSumDouble(entityType, entityInternalName);
        }
    };

    private final String methodName;

    FluentMethodType(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    /** Creates FluentMethodConfig for this method type. */
    public abstract QubitBytecodeGenerator.FluentMethodConfig createConfig(Type entityType, String entityInternalName);

    public static Optional<FluentMethodType> fromMethodName(@Nullable String methodName) {
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
}
