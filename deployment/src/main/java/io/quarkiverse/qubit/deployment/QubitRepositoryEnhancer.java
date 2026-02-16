package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.deployment.common.BytecodeAnalysisConstants.DESC_CLASS_CONSTRUCTOR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.CONSTRUCTOR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.DESC_QUERY_SPEC_TO_JOIN_STREAM;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.JOIN_STREAM_IMPL_INTERNAL_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.JOIN_TYPE_DESCRIPTOR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.JOIN_TYPE_INTERNAL_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_JOIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_LEFT_JOIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_REPOSITORY_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_REPOSITORY_INTERNAL_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_STREAM_IMPL_INTERNAL_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_STREAM_INTERNAL_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_SPEC_DESCRIPTOR;
import static org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE;

import java.util.List;
import java.util.function.BiFunction;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ParameterizedType;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.quarkus.logging.Log;

/**
 * Generates @GenerateBridge fluent API entry point implementations for QubitRepository beans.
 */
public class QubitRepositoryEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    private final IndexView indexView;

    public QubitRepositoryEnhancer(IndexView indexView) {
        this.indexView = indexView;
    }

    /**
     * Creates class visitor that generates bridge methods for QubitRepository implementation.
     */
    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        Log.debugf("Checking class: %s for QubitRepository interface implementation", className);
        return new QubitRepositoryClassVisitor(
                Opcodes.ASM9,
                outputClassVisitor,
                className,
                indexView);
    }

    private static class QubitRepositoryClassVisitor extends ClassVisitor {

        private final IndexView indexView;
        private final String className;
        private Type entityType;
        private boolean implementsQubitRepository;

        public QubitRepositoryClassVisitor(int api, ClassVisitor classVisitor,
                String className, IndexView indexView) {
            super(api, classVisitor);
            this.indexView = indexView;
            this.className = className;
            this.implementsQubitRepository = false;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {

            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (iface.startsWith(QUBIT_REPOSITORY_INTERNAL_NAME)) {
                        implementsQubitRepository = true;
                        extractEntityType();
                        if (entityType != null) {
                            Log.debugf("Repository %s implements QubitRepository<%s> - will generate bridge methods",
                                    className, entityType.getClassName());
                        }
                        break;
                    }
                }
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {

            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            if (!implementsQubitRepository || entityType == null) {
                return mv;
            }

            if (isGenerateBridgeMethod(name)) {
                Log.debugf("Generating bridge implementation for %s.%s%s", className, name, descriptor);
                return new BridgeMethodReplacer(api, mv, name, entityType);
            }

            return mv;
        }

        private void extractEntityType() {
            try {
                DotName classNameDot = DotName.createSimple(className);
                ClassInfo classInfo = indexView.getClassByName(classNameDot);
                if (classInfo == null) {
                    Log.warnf("Could not find ClassInfo for %s", className);
                    return;
                }

                DotName qubitRepositoryName = DotName.createSimple(QUBIT_REPOSITORY_CLASS_NAME);

                for (org.jboss.jandex.Type interfaceType : classInfo.interfaceTypes()) {
                    if (interfaceType.name().equals(qubitRepositoryName) && interfaceType.kind() == PARAMETERIZED_TYPE) {
                        ParameterizedType parameterizedType = interfaceType.asParameterizedType();
                        List<org.jboss.jandex.Type> typeArguments = parameterizedType.arguments();
                        if (!typeArguments.isEmpty()) {
                            org.jboss.jandex.Type jandexType = typeArguments.getFirst();
                            String entityClassName = jandexType.name().toString();
                            this.entityType = Type.getObjectType(entityClassName.replace('.', '/'));
                            Log.debugf("Extracted entity type: %s", entityClassName);
                            return;
                        }
                    }
                }

                Log.warnf("Could not extract entity type for repository %s", className);
            } catch (Exception e) {
                Log.warnf(e, "Failed to extract entity type for %s", className);
            }
        }

        private boolean isGenerateBridgeMethod(String methodName) {
            return FluentMethodType.fromMethodName(methodName).isPresent() ||
                    METHOD_JOIN.equals(methodName) ||
                    METHOD_LEFT_JOIN.equals(methodName);
        }

        /**
         * Generate bridge methods at the end of class visiting for empty repository classes.
         * This ensures all @GenerateBridge methods are implemented even if the repository
         * class body is empty.
         */
        @Override
        public void visitEnd() {
            if (implementsQubitRepository && entityType != null) {
                Log.debugf("Generating bridge methods for empty repository: %s", className);

                for (FluentMethodType methodType : FluentMethodType.ENTRY_POINTS) {
                    generateBridgeMethod(methodType);
                }

                // Generate join methods
                generateJoinMethod(METHOD_JOIN);
                generateJoinMethod(METHOD_LEFT_JOIN);
            }
            super.visitEnd();
        }

        /**
         * Generates a bridge method implementation for the given fluent API method type.
         *
         * @param methodType the fluent method type to generate
         */
        private void generateBridgeMethod(FluentMethodType methodType) {
            String entityInternalName = entityType.getInternalName();
            String methodName = methodType.getMethodName();

            QubitBytecodeGenerator.FluentMethodConfig config = methodType.createConfig(entityType, entityInternalName);

            Log.tracef("Generating method %s with descriptor %s", methodName, config.methodDescriptor());

            // Generate method: public QubitStream methodName(QuerySpec spec)
            MethodVisitor mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    methodName,
                    config.methodDescriptor(),
                    config.genericSignature(),
                    null);

            mv.visitCode();

            // Create new QubitStreamImpl instance
            mv.visitTypeInsn(Opcodes.NEW, QUBIT_STREAM_IMPL_INTERNAL_NAME);
            mv.visitInsn(Opcodes.DUP);

            // Load entity class as constructor argument
            mv.visitLdcInsn(entityType);

            // Call QubitStreamImpl constructor
            mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    QUBIT_STREAM_IMPL_INTERNAL_NAME,
                    CONSTRUCTOR,
                    DESC_CLASS_CONSTRUCTOR,
                    false);

            // Load QuerySpec parameter (index 1 for instance method)
            mv.visitVarInsn(Opcodes.ALOAD, 1);

            // Call the appropriate method on the stream (uses config descriptor for correct return type)
            mv.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    QUBIT_STREAM_INTERNAL_NAME,
                    methodName,
                    config.methodDescriptor(),
                    true);

            mv.visitInsn(Opcodes.ARETURN);

            mv.visitMaxs(4, 2);
            mv.visitEnd();
            Log.infof("    Successfully generated method: %s", methodName);
        }

        /**
         * Generates a join/leftJoin method implementation for repositories.
         * These methods return JoinStream instead of QubitStream.
         *
         * Generated code equivalent:
         *
         * <pre>{@code
         * public <R> JoinStream<E, R> join(QuerySpec<E, Collection<R>> relationship) {
         *     return new JoinStreamImpl<>(entityClass, relationship, JoinType.INNER);
         * }
         *
         * public <R> JoinStream<E, R> leftJoin(QuerySpec<E, Collection<R>> relationship) {
         *     return new JoinStreamImpl<>(entityClass, relationship, JoinType.LEFT);
         * }
         * }</pre>
         */
        private void generateJoinMethod(String methodName) {
            boolean isLeftJoin = METHOD_LEFT_JOIN.equals(methodName);

            // Method signature: (QuerySpec)JoinStream
            // Note: Generic types are erased at bytecode level
            String methodDescriptor = DESC_QUERY_SPEC_TO_JOIN_STREAM;
            String genericSignature = "<R:Ljava/lang/Object;>(Lio/quarkiverse/qubit/QuerySpec<L" +
                    entityType.getInternalName() + ";Ljava/util/Collection<TR;>;>;)Lio/quarkiverse/qubit/JoinStream<L" +
                    entityType.getInternalName() + ";TR;>;";

            Log.tracef("Generating join method %s with descriptor %s", methodName, methodDescriptor);

            MethodVisitor mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    methodName,
                    methodDescriptor,
                    genericSignature,
                    null);

            mv.visitCode();

            // Create new JoinStreamImpl instance
            mv.visitTypeInsn(Opcodes.NEW, JOIN_STREAM_IMPL_INTERNAL_NAME);
            mv.visitInsn(Opcodes.DUP);

            // Load entity class as first constructor argument (sourceEntityClass)
            mv.visitLdcInsn(entityType);

            // Load Object.class as second constructor argument (joinedEntityClass - placeholder)
            // The actual joined entity type is erased at runtime; Object.class is used as placeholder
            mv.visitLdcInsn(Type.getType(Object.class));

            // Load relationship QuerySpec parameter (index 1 for instance method)
            mv.visitVarInsn(Opcodes.ALOAD, 1);

            // Load join type as fourth constructor argument
            mv.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    JOIN_TYPE_INTERNAL_NAME,
                    isLeftJoin ? "LEFT" : "INNER",
                    JOIN_TYPE_DESCRIPTOR);

            // Call JoinStreamImpl constructor: <init>(Class, Class, QuerySpec, JoinType)
            mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    JOIN_STREAM_IMPL_INTERNAL_NAME,
                    CONSTRUCTOR,
                    "(Ljava/lang/Class;Ljava/lang/Class;" + QUERY_SPEC_DESCRIPTOR + JOIN_TYPE_DESCRIPTOR + ")V",
                    false);

            mv.visitInsn(Opcodes.ARETURN);

            mv.visitMaxs(6, 2);
            mv.visitEnd();
            Log.infof("    Successfully generated join method: %s", methodName);
        }
    }

    private static class BridgeMethodReplacer extends MethodVisitor {
        private final String methodName;
        private final Type entityType;

        public BridgeMethodReplacer(int api, MethodVisitor methodVisitor,
                String methodName, Type entityType) {
            super(api, methodVisitor);
            this.methodName = methodName;
            this.entityType = entityType;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            generateBridgeImplementation();
        }

        private void generateBridgeImplementation() {
            mv.visitCode();

            String entityInternalName = entityType.getInternalName();

            QubitBytecodeGenerator.FluentMethodConfig config = FluentMethodType.fromMethodName(methodName)
                    .map(type -> type.createConfig(entityType, entityInternalName))
                    .orElse(null);

            if (config == null) {
                Log.warnf("Unknown fluent entry point method: %s", methodName);
                return;
            }

            // Create new QubitStreamImpl instance
            mv.visitTypeInsn(Opcodes.NEW, QUBIT_STREAM_IMPL_INTERNAL_NAME);
            mv.visitInsn(Opcodes.DUP);

            // Load entity class as constructor argument
            mv.visitLdcInsn(entityType);

            // Call QubitStreamImpl constructor
            mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    QUBIT_STREAM_IMPL_INTERNAL_NAME,
                    CONSTRUCTOR,
                    DESC_CLASS_CONSTRUCTOR,
                    false);

            // Load QuerySpec parameter (index 1 for instance method)
            mv.visitVarInsn(Opcodes.ALOAD, 1);

            // Call the appropriate method on the stream (uses config descriptor for correct return type)
            mv.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    QUBIT_STREAM_INTERNAL_NAME,
                    methodName,
                    config.methodDescriptor(),
                    true);

            mv.visitInsn(Opcodes.ARETURN);

            mv.visitMaxs(4, 2);
        }

        /**
         * Suppresses default visitEnd behavior since method is fully generated in visitCode.
         */
        @Override
        public void visitEnd() {
            // Code not needed.
        }
    }
}
