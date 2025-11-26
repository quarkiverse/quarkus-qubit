package io.quarkus.qusaq.deployment;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates fluent API entry point methods for QusaqEntity and QusaqRepository.
 */
public final class QusaqBytecodeGenerator {

    private static final String DESC_QUERY_SPEC_TO_STREAM = "(Lio/quarkus/qusaq/runtime/QuerySpec;)Lio/quarkus/qusaq/runtime/QusaqStream;";
    private static final String DESC_QUERY_SPEC_TO_JOIN_STREAM = "(Lio/quarkus/qusaq/runtime/QuerySpec;)Lio/quarkus/qusaq/runtime/JoinStream;";
    private static final String DESC_QUERY_SPEC_TO_GROUP_STREAM = "(Lio/quarkus/qusaq/runtime/QuerySpec;)Lio/quarkus/qusaq/runtime/GroupStream;";
    private static final String QUSAQ_STREAM_IMPL_INTERNAL_NAME = "io/quarkus/qusaq/runtime/QusaqStreamImpl";

    private QusaqBytecodeGenerator() {
    }

    /**
     * Generates fluent API entry point method that returns QusaqStreamImpl.
     * <p>
     * Generated code pattern for entry points:
     * <pre>{@code
     * public static QusaqStream<Person> where(QuerySpec<Person, Boolean> spec) {
     *     return new QusaqStreamImpl<>(Person.class).where(spec);
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

        // Create new QusaqStreamImpl instance
        mv.visitTypeInsn(Opcodes.NEW, QUSAQ_STREAM_IMPL_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);

        // Load entity class as constructor argument
        mv.visitLdcInsn(config.entityType());

        // Call QusaqStreamImpl constructor: new QusaqStreamImpl(Person.class)
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                QUSAQ_STREAM_IMPL_INTERNAL_NAME,
                "<init>",
                "(Ljava/lang/Class;)V",
                false);

        // Load QuerySpec parameter (index 0 for static method)
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // Call the appropriate method on the stream: .where(spec) or .select(spec) or .min(spec) etc.
        // Use the method descriptor from config (aggregation methods have different return types)
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "io/quarkus/qusaq/runtime/QusaqStream",
                config.methodName(),
                config.methodDescriptor(),  // Use descriptor from config (not hardcoded)
                true);

        // Return the result
        mv.visitInsn(config.returnOpcode());

        mv.visitMaxs(config.maxStack() + 1, config.maxLocals()); // +1 for ALOAD
        mv.visitEnd();

        return mv;
    }

    /**
     * Generates join entry point method that returns JoinStream.
     * <p>
     * Generated code pattern for join entry points:
     * <pre>{@code
     * public static <R> JoinStream<Person, R> join(QuerySpec<Person, Collection<R>> spec) {
     *     return new QusaqStreamImpl<>(Person.class).join(spec);
     * }
     * }</pre>
     * <p>
     * Iteration 6: Join Queries
     */
    public static MethodVisitor generateJoinEntryPoint(ClassVisitor cv, JoinMethodConfig config) {
        MethodVisitor mv = cv.visitMethod(
                config.access(),
                config.methodName(),
                config.methodDescriptor(),
                config.genericSignature(),
                null);

        mv.visitCode();

        // Create new QusaqStreamImpl instance
        mv.visitTypeInsn(Opcodes.NEW, QUSAQ_STREAM_IMPL_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);

        // Load entity class as constructor argument
        mv.visitLdcInsn(config.entityType());

        // Call QusaqStreamImpl constructor: new QusaqStreamImpl(Person.class)
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                QUSAQ_STREAM_IMPL_INTERNAL_NAME,
                "<init>",
                "(Ljava/lang/Class;)V",
                false);

        // Load QuerySpec parameter (index 0 for static method)
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // Call the join method on the stream: .join(spec) or .leftJoin(spec)
        // Returns JoinStream<T, R>
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "io/quarkus/qusaq/runtime/QusaqStream",
                config.methodName(),
                config.methodDescriptor(),
                true);

        // Return the JoinStream result
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(4, 1); // stack: NEW, DUP, LDC, ALOAD; locals: QuerySpec parameter
        mv.visitEnd();

        return mv;
    }

    /**
     * Configuration for generating join entry point methods.
     * Iteration 6: Join Queries
     */
    public record JoinMethodConfig(
            int access,
            String methodName,
            String methodDescriptor,
            String genericSignature,
            Type entityType
    ) {
        /**
         * Creates config for join() method.
         * Signature: <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship)
         */
        public static JoinMethodConfig forJoin(Type entityType, String entityInternalName) {
            // Generic signature: <R:Ljava/lang/Object;>(Lio/quarkus/qusaq/runtime/QuerySpec<LPerson;Ljava/util/Collection<TR;>;>;)Lio/quarkus/qusaq/runtime/JoinStream<LPerson;TR;>;
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";Ljava/util/Collection<TR;>;>;)Lio/quarkus/qusaq/runtime/JoinStream<L" +
                    entityInternalName + ";TR;>;";

            return new JoinMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "join",
                    DESC_QUERY_SPEC_TO_JOIN_STREAM,
                    genericSignature,
                    entityType
            );
        }

        /**
         * Creates config for leftJoin() method.
         * Signature: <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship)
         */
        public static JoinMethodConfig forLeftJoin(Type entityType, String entityInternalName) {
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";Ljava/util/Collection<TR;>;>;)Lio/quarkus/qusaq/runtime/JoinStream<L" +
                    entityInternalName + ";TR;>;";

            return new JoinMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "leftJoin",
                    DESC_QUERY_SPEC_TO_JOIN_STREAM,
                    genericSignature,
                    entityType
            );
        }
    }

    /**
     * Generates groupBy entry point method that returns GroupStream.
     * <p>
     * Generated code pattern for groupBy entry point:
     * <pre>{@code
     * public static <K> GroupStream<Person, K> groupBy(QuerySpec<Person, K> keyExtractor) {
     *     return new QusaqStreamImpl<>(Person.class).groupBy(keyExtractor);
     * }
     * }</pre>
     * <p>
     * Iteration 7: Grouping / GROUP BY
     */
    public static MethodVisitor generateGroupEntryPoint(ClassVisitor cv, GroupMethodConfig config) {
        MethodVisitor mv = cv.visitMethod(
                config.access(),
                config.methodName(),
                config.methodDescriptor(),
                config.genericSignature(),
                null);

        mv.visitCode();

        // Create new QusaqStreamImpl instance
        mv.visitTypeInsn(Opcodes.NEW, QUSAQ_STREAM_IMPL_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);

        // Load entity class as constructor argument
        mv.visitLdcInsn(config.entityType());

        // Call QusaqStreamImpl constructor: new QusaqStreamImpl(Person.class)
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                QUSAQ_STREAM_IMPL_INTERNAL_NAME,
                "<init>",
                "(Ljava/lang/Class;)V",
                false);

        // Load QuerySpec parameter (index 0 for static method)
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // Call the groupBy method on the stream: .groupBy(spec)
        // Returns GroupStream<T, K>
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "io/quarkus/qusaq/runtime/QusaqStream",
                config.methodName(),
                config.methodDescriptor(),
                true);

        // Return the GroupStream result
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(4, 1); // stack: NEW, DUP, LDC, ALOAD; locals: QuerySpec parameter
        mv.visitEnd();

        return mv;
    }

    /**
     * Configuration for generating groupBy entry point method.
     * Iteration 7: Grouping / GROUP BY
     */
    public record GroupMethodConfig(
            int access,
            String methodName,
            String methodDescriptor,
            String genericSignature,
            Type entityType
    ) {
        /**
         * Creates config for groupBy() method.
         * Signature: <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor)
         */
        public static GroupMethodConfig forGroupBy(Type entityType, String entityInternalName) {
            // Generic signature: <K:Ljava/lang/Object;>(Lio/quarkus/qusaq/runtime/QuerySpec<LPerson;TK;>;)Lio/quarkus/qusaq/runtime/GroupStream<LPerson;TK;>;
            String genericSignature = "<K:Ljava/lang/Object;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkus/qusaq/runtime/GroupStream<L" +
                    entityInternalName + ";TK;>;";

            return new GroupMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "groupBy",
                    DESC_QUERY_SPEC_TO_GROUP_STREAM,
                    genericSignature,
                    entityType
            );
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
            int maxLocals
    ) {
        /**
         * Creates config for where() method.
         */
        public static FluentMethodConfig forWhere(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkus/qusaq/runtime/QuerySpec<L" + entityInternalName +
                    ";Ljava/lang/Boolean;>;)Lio/quarkus/qusaq/runtime/QusaqStream<L" + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "where",
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,  // max stack: NEW, DUP, LDC
                    1   // max locals: QuerySpec parameter
            );
        }

        /**
         * Creates config for select() method.
         */
        public static FluentMethodConfig forSelect(Type entityType, String entityInternalName) {
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";TR;>;)Lio/quarkus/qusaq/runtime/QusaqStream<TR;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "select",
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for sortedBy() method.
         */
        public static FluentMethodConfig forSortedBy(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkus/qusaq/runtime/QusaqStream<L" + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "sortedBy",
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for sortedDescendingBy() method.
         */
        public static FluentMethodConfig forSortedDescendingBy(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkus/qusaq/runtime/QusaqStream<L" + entityInternalName + ";>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "sortedDescendingBy",
                    DESC_QUERY_SPEC_TO_STREAM,
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1
            );
        }

        // Phase 5: Aggregation methods - these now return QusaqStream (intermediate operations)

        /**
         * Creates config for min() method.
         * Returns: <K extends Comparable<K>> QusaqStream<K>
         */
        public static FluentMethodConfig forMin(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkus/qusaq/runtime/QusaqStream<TK;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "min",
                    DESC_QUERY_SPEC_TO_STREAM,  // Returns QusaqStream
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for max() method.
         * Returns: <K extends Comparable<K>> QusaqStream<K>
         */
        public static FluentMethodConfig forMax(Type entityType, String entityInternalName) {
            String genericSignature = "<K::Ljava/lang/Comparable<TK;>;>(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";TK;>;)Lio/quarkus/qusaq/runtime/QusaqStream<TK;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "max",
                    DESC_QUERY_SPEC_TO_STREAM,  // Returns QusaqStream
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for avg() method.
         * Returns: QusaqStream<Double>
         */
        public static FluentMethodConfig forAvg(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";+Ljava/lang/Number;>;)Lio/quarkus/qusaq/runtime/QusaqStream<Ljava/lang/Double;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "avg",
                    DESC_QUERY_SPEC_TO_STREAM,  // Returns QusaqStream
                    genericSignature,
                    Opcodes.ARETURN,
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for sumInteger() method.
         * Returns: QusaqStream<Long>
         */
        public static FluentMethodConfig forSumInteger(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";Ljava/lang/Integer;>;)Lio/quarkus/qusaq/runtime/QusaqStream<Ljava/lang/Long;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "sumInteger",
                    DESC_QUERY_SPEC_TO_STREAM,  // Returns QusaqStream
                    genericSignature,
                    Opcodes.ARETURN,  // object reference return
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for sumLong() method.
         * Returns: QusaqStream<Long>
         */
        public static FluentMethodConfig forSumLong(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";Ljava/lang/Long;>;)Lio/quarkus/qusaq/runtime/QusaqStream<Ljava/lang/Long;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "sumLong",
                    DESC_QUERY_SPEC_TO_STREAM,  // Returns QusaqStream
                    genericSignature,
                    Opcodes.ARETURN,  // object reference return
                    entityType,
                    3,
                    1
            );
        }

        /**
         * Creates config for sumDouble() method.
         * Returns: QusaqStream<Double>
         */
        public static FluentMethodConfig forSumDouble(Type entityType, String entityInternalName) {
            String genericSignature = "(Lio/quarkus/qusaq/runtime/QuerySpec<L" +
                    entityInternalName + ";Ljava/lang/Double;>;)Lio/quarkus/qusaq/runtime/QusaqStream<Ljava/lang/Double;>;";

            return new FluentMethodConfig(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "sumDouble",
                    DESC_QUERY_SPEC_TO_STREAM,  // Returns QusaqStream
                    genericSignature,
                    Opcodes.ARETURN,  // object reference return
                    entityType,
                    3,
                    1
            );
        }
    }
}
