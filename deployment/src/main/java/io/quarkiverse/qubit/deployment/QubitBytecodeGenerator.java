package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.deployment.common.BytecodeAnalysisConstants.DESC_CLASS_CONSTRUCTOR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.CONSTRUCTOR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.DESC_QUERY_SPEC_TO_GROUP_STREAM;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.DESC_QUERY_SPEC_TO_JOIN_STREAM;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.DESC_QUERY_SPEC_TO_SCALAR_RESULT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.DESC_QUERY_SPEC_TO_STREAM;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_AVG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GROUP_BY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_JOIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_LEFT_JOIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_MAX;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_MIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SELECT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SORTED_BY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SORTED_DESCENDING_BY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_DOUBLE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_INTEGER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_LONG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_WHERE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_STREAM_IMPL_INTERNAL_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_STREAM_INTERNAL_NAME;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates fluent API entry point methods for QubitEntity and QubitRepository.
 */
@SuppressWarnings("java:S1192") // Generic signature fragments kept inline for readability
public final class QubitBytecodeGenerator {

    private QubitBytecodeGenerator() {
    }

    /**
     * Generates fluent API entry point method that returns QubitStreamImpl.
     * <p>
     * Generated code pattern for entry points:
     *
     * <pre>{@code
     * public static QubitStream<Person> where(QuerySpec<Person, Boolean> spec) {
     *     return new QubitStreamImpl<>(Person.class).where(spec);
     * }
     * }</pre>
     */
    public static MethodVisitor generateFluentEntryPoint(ClassVisitor cv, FluentMethodConfig config) {
        MethodVisitor mv = cv.visitMethod(
                config.access(),
                config.methodName(),
                config.methodDescriptor(),
                config.genericSignature(),
                null);

        mv.visitCode();

        // Create new QubitStreamImpl instance
        mv.visitTypeInsn(Opcodes.NEW, QUBIT_STREAM_IMPL_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);

        // Load entity class as constructor argument
        mv.visitLdcInsn(config.entityType());

        // Call QubitStreamImpl constructor: new QubitStreamImpl(Person.class)
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                QUBIT_STREAM_IMPL_INTERNAL_NAME,
                CONSTRUCTOR,
                DESC_CLASS_CONSTRUCTOR,
                false);

        // Load parameters (index 0..N for static method)
        for (int i = 0; i < config.maxLocals(); i++) {
            mv.visitVarInsn(Opcodes.ALOAD, i);
        }

        // Call the appropriate method on the stream: .where(spec) or .select(spec) or .min(spec) etc.
        // Use the method descriptor from config (aggregation methods have different return types)
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                QUBIT_STREAM_INTERNAL_NAME,
                config.methodName(),
                config.methodDescriptor(), // Use descriptor from config (not hardcoded)
                true);

        mv.visitInsn(config.returnOpcode());

        mv.visitMaxs(config.maxStack() + 1, config.maxLocals()); // +1 for ALOAD
        mv.visitEnd();

        return mv;
    }

    /**
     * Generates join entry point method that returns JoinStream.
     * <p>
     * Generated code pattern for join entry points:
     *
     * <pre>{@code
     * public static <R> JoinStream<Person, R> join(QuerySpec<Person, Collection<R>> spec) {
     *     return new QubitStreamImpl<>(Person.class).join(spec);
     * }
     * }</pre>
     * <p>
     * Join Queries
     */
    public static MethodVisitor generateJoinEntryPoint(ClassVisitor cv, JoinMethodConfig config) {
        MethodVisitor mv = cv.visitMethod(
                config.access(),
                config.methodName(),
                config.methodDescriptor(),
                config.genericSignature(),
                null);

        mv.visitCode();

        // Create new QubitStreamImpl instance
        mv.visitTypeInsn(Opcodes.NEW, QUBIT_STREAM_IMPL_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);

        // Load entity class as constructor argument
        mv.visitLdcInsn(config.entityType());

        // Call QubitStreamImpl constructor: new QubitStreamImpl(Person.class)
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                QUBIT_STREAM_IMPL_INTERNAL_NAME,
                CONSTRUCTOR,
                DESC_CLASS_CONSTRUCTOR,
                false);

        // Load QuerySpec parameter (index 0 for static method)
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // Call the join method on the stream: .join(spec) or .leftJoin(spec)
        // Returns JoinStream<T, R>
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                QUBIT_STREAM_INTERNAL_NAME,
                config.methodName(),
                config.methodDescriptor(),
                true);

        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(4, 1); // stack: NEW, DUP, LDC, ALOAD; locals: QuerySpec parameter
        mv.visitEnd();

        return mv;
    }

    /**
     * Configuration for generating join entry point methods.
     * Join Queries
     */
    public record JoinMethodConfig(
            int access,
            String methodName,
            String methodDescriptor,
            String genericSignature,
            Type entityType) {
        /**
         * Creates config for join() method.
         * Signature: <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship)
         */
        public static JoinMethodConfig forJoin(Type entityType, String entityInternalName) {
            // Generic signature: <R:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<LPerson;Ljava/util/Collection<TR;>;>;)Lio/quarkiverse/qubit/JoinStream<LPerson;TR;>;
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";Ljava/util/Collection<TR;>;>;)Lio/quarkiverse/qubit/JoinStream<L" +
                    entityInternalName + ";TR;>;";

            return new JoinMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_JOIN,
                    DESC_QUERY_SPEC_TO_JOIN_STREAM,
                    genericSignature,
                    entityType);
        }

        /**
         * Creates config for leftJoin() method.
         * Signature: <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship)
         */
        public static JoinMethodConfig forLeftJoin(Type entityType, String entityInternalName) {
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";Ljava/util/Collection<TR;>;>;)Lio/quarkiverse/qubit/JoinStream<L" +
                    entityInternalName + ";TR;>;";

            return new JoinMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_LEFT_JOIN,
                    DESC_QUERY_SPEC_TO_JOIN_STREAM,
                    genericSignature,
                    entityType);
        }
    }

    /**
     * Generates groupBy entry point method that returns GroupStream.
     * <p>
     * Generated code pattern for groupBy entry point:
     *
     * <pre>{@code
     * public static <K> GroupStream<Person, K> groupBy(QuerySpec<Person, K> keyExtractor) {
     *     return new QubitStreamImpl<>(Person.class).groupBy(keyExtractor);
     * }
     * }</pre>
     * <p>
     * Grouping / GROUP BY
     */
    public static MethodVisitor generateGroupEntryPoint(ClassVisitor cv, GroupMethodConfig config) {
        MethodVisitor mv = cv.visitMethod(
                config.access(),
                config.methodName(),
                config.methodDescriptor(),
                config.genericSignature(),
                null);

        mv.visitCode();

        // Create new QubitStreamImpl instance
        mv.visitTypeInsn(Opcodes.NEW, QUBIT_STREAM_IMPL_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);

        // Load entity class as constructor argument
        mv.visitLdcInsn(config.entityType());

        // Call QubitStreamImpl constructor: new QubitStreamImpl(Person.class)
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                QUBIT_STREAM_IMPL_INTERNAL_NAME,
                CONSTRUCTOR,
                DESC_CLASS_CONSTRUCTOR,
                false);

        // Load QuerySpec parameter (index 0 for static method)
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // Call the groupBy method on the stream: .groupBy(spec)
        // Returns GroupStream<T, K>
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                QUBIT_STREAM_INTERNAL_NAME,
                config.methodName(),
                config.methodDescriptor(),
                true);

        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(4, 1); // stack: NEW, DUP, LDC, ALOAD; locals: QuerySpec parameter
        mv.visitEnd();

        return mv;
    }

    /**
     * Configuration for generating groupBy entry point method.
     * Grouping / GROUP BY
     */
    public record GroupMethodConfig(
            int access,
            String methodName,
            String methodDescriptor,
            String genericSignature,
            Type entityType) {
        /**
         * Creates config for groupBy() method.
         * Signature: <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor)
         */
        public static GroupMethodConfig forGroupBy(Type entityType, String entityInternalName) {
            // Generic signature: <K:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<LPerson;TK;>;)Lio/quarkiverse/qubit/GroupStream<LPerson;TK;>;
            String genericSignature = "<K:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkiverse/qubit/GroupStream<L" +
                    entityInternalName + ";TK;>;";

            return new GroupMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_GROUP_BY,
                    DESC_QUERY_SPEC_TO_GROUP_STREAM,
                    genericSignature,
                    entityType);
        }
    }

    /**
     * Configuration for generating fluent API entry point methods.
     */
    public record FluentMethodConfig(
            int access,
            String methodName,
            String methodDescriptor,
            String genericSignature,
            int returnOpcode,
            Type entityType,
            int maxStack,
            int maxLocals) {
        /**
         * Creates config for where() method.
         */
        public static FluentMethodConfig forWhere(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkiverse/qubit/QuerySpec<L" + entityInternalName +
                    ";Ljava/lang/Boolean;>;)Lio/quarkiverse/qubit/QubitStream<L" + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_WHERE,
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3, // max stack: NEW, DUP, LDC
                    1 // max locals: QuerySpec parameter
            );
        }

        /**
         * Creates config for select() method.
         */
        public static FluentMethodConfig forSelect(Type entityType, String entityInternalName) {
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TR;>;)Lio/quarkiverse/qubit/QubitStream<TR;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_SELECT,
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1);
        }

        /**
         * Creates config for sortedBy() method.
         */
        public static FluentMethodConfig forSortedBy(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkiverse/qubit/QubitStream<L" + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_SORTED_BY,
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1);
        }

        /**
         * Creates config for sortedDescendingBy() method.
         */
        public static FluentMethodConfig forSortedDescendingBy(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkiverse/qubit/QubitStream<L" + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_SORTED_DESCENDING_BY,
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1);
        }

        private static final String DESC_QUERY_SPEC_NULLS_TO_STREAM = "(Lio/quarkiverse/qubit/QuerySpec;"
                + "Ljakarta/persistence/criteria/Nulls;)Lio/quarkiverse/qubit/QubitStream;";

        /** Creates config for sortedBy(QuerySpec, Nulls) method (JPA 3.2). */
        public static FluentMethodConfig forSortedByWithNulls(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;Ljakarta/persistence/criteria/Nulls;)Lio/quarkiverse/qubit/QubitStream<L"
                    + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_SORTED_BY,
                    DESC_QUERY_SPEC_NULLS_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    4,
                    2);
        }

        /** Creates config for sortedDescendingBy(QuerySpec, Nulls) method (JPA 3.2). */
        public static FluentMethodConfig forSortedDescendingByWithNulls(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;Ljakarta/persistence/criteria/Nulls;)Lio/quarkiverse/qubit/QubitStream<L"
                    + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    METHOD_SORTED_DESCENDING_BY,
                    DESC_QUERY_SPEC_NULLS_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    4,
                    2);
        }

        // Aggregation methods - these now return QubitStream (intermediate operations)

        /** Creates config for min() method. Returns: ScalarResult<K> */
        public static FluentMethodConfig forMin(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkiverse/qubit/ScalarResult<TK;>;";
            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_MIN,
                    DESC_QUERY_SPEC_TO_SCALAR_RESULT, genericSignature,
                    Opcodes.ARETURN, entityType, 3, 1);
        }

        /** Creates config for max() method. Returns: ScalarResult<K> */
        public static FluentMethodConfig forMax(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkiverse/qubit/ScalarResult<TK;>;";
            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_MAX,
                    DESC_QUERY_SPEC_TO_SCALAR_RESULT, genericSignature,
                    Opcodes.ARETURN, entityType, 3, 1);
        }

        /** Creates config for avg() method. Returns: ScalarResult<Double> */
        public static FluentMethodConfig forAvg(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";+Ljava/lang/Number;>;)Lio/quarkiverse/qubit/ScalarResult<Ljava/lang/Double;>;";
            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_AVG,
                    DESC_QUERY_SPEC_TO_SCALAR_RESULT, genericSignature,
                    Opcodes.ARETURN, entityType, 3, 1);
        }

        /** Creates config for sumInteger() method. Returns: ScalarResult<Long> */
        public static FluentMethodConfig forSumInteger(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";Ljava/lang/Integer;>;)Lio/quarkiverse/qubit/ScalarResult<Ljava/lang/Long;>;";
            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_SUM_INTEGER,
                    DESC_QUERY_SPEC_TO_SCALAR_RESULT, genericSignature,
                    Opcodes.ARETURN, entityType, 3, 1);
        }

        /** Creates config for sumLong() method. Returns: ScalarResult<Long> */
        public static FluentMethodConfig forSumLong(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";Ljava/lang/Long;>;)Lio/quarkiverse/qubit/ScalarResult<Ljava/lang/Long;>;";
            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_SUM_LONG,
                    DESC_QUERY_SPEC_TO_SCALAR_RESULT, genericSignature,
                    Opcodes.ARETURN, entityType, 3, 1);
        }

        /** Creates config for sumDouble() method. Returns: ScalarResult<Double> */
        public static FluentMethodConfig forSumDouble(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityInternalName + ";Ljava/lang/Double;>;)Lio/quarkiverse/qubit/ScalarResult<Ljava/lang/Double;>;";
            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_SUM_DOUBLE,
                    DESC_QUERY_SPEC_TO_SCALAR_RESULT, genericSignature,
                    Opcodes.ARETURN, entityType, 3, 1);
        }
    }
}
