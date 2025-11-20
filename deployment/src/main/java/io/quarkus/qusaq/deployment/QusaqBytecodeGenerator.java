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

        // Call the appropriate method on the stream: .where(spec) or .select(spec) etc.
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "io/quarkus/qusaq/runtime/QusaqStream",
                config.methodName(),
                DESC_QUERY_SPEC_TO_STREAM,
                true);

        // Return the result
        mv.visitInsn(config.returnOpcode());

        mv.visitMaxs(config.maxStack() + 1, config.maxLocals()); // +1 for ALOAD
        mv.visitEnd();

        return mv;
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
    }
}
