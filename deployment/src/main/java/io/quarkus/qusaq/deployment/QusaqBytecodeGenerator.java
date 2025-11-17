package io.quarkus.qusaq.deployment;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_COUNT_WHERE;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_EXISTS;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_FIND_WHERE;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_OPERATIONS_INTERNAL_NAME;

/**
 * Generates delegation methods that call QusaqOperations.
 */
public final class QusaqBytecodeGenerator {

    private static final String DESC_QUERY_SPEC_TO_LIST = "(Lio/quarkus/qusaq/runtime/QuerySpec;)Ljava/util/List;";
    private static final String DESC_QUERY_SPEC_TO_LONG = "(Lio/quarkus/qusaq/runtime/QuerySpec;)J";
    private static final String DESC_QUERY_SPEC_TO_BOOLEAN = "(Lio/quarkus/qusaq/runtime/QuerySpec;)Z";

    private static final String OPS_DESC_CLASS_QUERY_SPEC_TO_LIST = "(Ljava/lang/Class;Lio/quarkus/qusaq/runtime/QuerySpec;)Ljava/util/List;";
    private static final String OPS_DESC_CLASS_QUERY_SPEC_TO_LONG = "(Ljava/lang/Class;Lio/quarkus/qusaq/runtime/QuerySpec;)J";
    private static final String OPS_DESC_CLASS_QUERY_SPEC_TO_BOOLEAN = "(Ljava/lang/Class;Lio/quarkus/qusaq/runtime/QuerySpec;)Z";

    private static final String GENERIC_SIG_PREFIX = "(Lio/quarkus/qusaq/runtime/QuerySpec<L";

    private QusaqBytecodeGenerator() {
    }

    /**
     * Generates method that delegates to QusaqOperations.
     */
    public static MethodVisitor generateDelegationMethod(ClassVisitor cv, DelegationMethodConfig config) {
        MethodVisitor mv = cv.visitMethod(
                config.access(),
                config.methodName(),
                config.methodDescriptor(),
                config.genericSignature(),
                null);

        mv.visitCode();

        mv.visitLdcInsn(config.entityType());

        mv.visitVarInsn(Opcodes.ALOAD, config.querySpecParameterIndex());

        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                QUSAQ_OPERATIONS_INTERNAL_NAME,
                config.methodName(),
                config.operationsMethodDescriptor(),
                false);

        mv.visitInsn(config.returnOpcode());

        mv.visitMaxs(config.maxStack(), config.maxLocals());
        mv.visitEnd();

        return mv;
    }

    /**
     * Configuration for generating delegation method.
     */
    public record DelegationMethodConfig(
            int access,
            String methodName,
            String methodDescriptor,
            String genericSignature,
            String operationsMethodDescriptor,
            int returnOpcode,
            Type entityType,
            int querySpecParameterIndex,
            int maxStack,
            int maxLocals
    ) {
        /**
         * Creates config for findWhere method.
         */
        public static DelegationMethodConfig forFindWhere(Type entityType, String entityInternalName, boolean isStatic) {
            String genericSignature = GENERIC_SIG_PREFIX + entityInternalName +
                    ";Ljava/lang/Boolean;>;)Ljava/util/List<L" + entityInternalName + ";>;";

            return new DelegationMethodConfig(
                    isStatic ? (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) : Opcodes.ACC_PUBLIC,
                    METHOD_FIND_WHERE,
                    DESC_QUERY_SPEC_TO_LIST,
                    genericSignature,
                    OPS_DESC_CLASS_QUERY_SPEC_TO_LIST,
                    Opcodes.ARETURN,
                    entityType,
                    isStatic ? 0 : 1,
                    2,
                    isStatic ? 1 : 2
            );
        }

        /**
         * Creates config for countWhere method.
         */
        public static DelegationMethodConfig forCountWhere(Type entityType, String entityInternalName, boolean isStatic) {
            String genericSignature = GENERIC_SIG_PREFIX + entityInternalName + ";Ljava/lang/Boolean;>;)J";

            return new DelegationMethodConfig(
                    isStatic ? (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) : Opcodes.ACC_PUBLIC,
                    METHOD_COUNT_WHERE,
                    DESC_QUERY_SPEC_TO_LONG,
                    genericSignature,
                    OPS_DESC_CLASS_QUERY_SPEC_TO_LONG,
                    Opcodes.LRETURN,
                    entityType,
                    isStatic ? 0 : 1,
                    2,
                    isStatic ? 1 : 2
            );
        }

        /**
         * Creates config for exists method.
         */
        public static DelegationMethodConfig forExists(Type entityType, String entityInternalName, boolean isStatic) {
            String genericSignature = GENERIC_SIG_PREFIX + entityInternalName + ";Ljava/lang/Boolean;>;)Z";

            return new DelegationMethodConfig(
                    isStatic ? (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) : Opcodes.ACC_PUBLIC,
                    METHOD_EXISTS,
                    DESC_QUERY_SPEC_TO_BOOLEAN,
                    genericSignature,
                    OPS_DESC_CLASS_QUERY_SPEC_TO_BOOLEAN,
                    Opcodes.IRETURN,
                    entityType,
                    isStatic ? 0 : 1,
                    2,
                    isStatic ? 1 : 2
            );
        }
    }
}
