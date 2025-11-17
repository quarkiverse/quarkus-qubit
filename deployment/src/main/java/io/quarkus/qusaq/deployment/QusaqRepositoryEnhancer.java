package io.quarkus.qusaq.deployment;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ParameterizedType;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE;

import java.util.List;
import java.util.function.BiFunction;

import static io.quarkus.qusaq.runtime.QusaqConstants.QUERY_METHOD_NAMES;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_OPERATIONS_INTERNAL_NAME;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_COUNT_WHERE;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_EXISTS;
import static io.quarkus.qusaq.runtime.QusaqConstants.METHOD_FIND_WHERE;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_REPOSITORY_CLASS_NAME;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_REPOSITORY_INTERNAL_NAME;

/**
 * Generates @GenerateBridge method implementations for QusaqRepository beans (Repository pattern).
 */
public class QusaqRepositoryEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    private static final Logger log = Logger.getLogger(QusaqRepositoryEnhancer.class);
    private final IndexView indexView;

    public QusaqRepositoryEnhancer(IndexView indexView) {
        this.indexView = indexView;
    }

    /**
     * Creates class visitor that generates bridge methods for QusaqRepository implementation.
     */
    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        log.debugf("Checking class: %s for QusaqRepository interface implementation", className);
        return new QusaqRepositoryClassVisitor(
                Opcodes.ASM9,
                outputClassVisitor,
                className,
                indexView);
    }

    private static class QusaqRepositoryClassVisitor extends ClassVisitor {

        private final IndexView indexView;
        private final String className;
        private Type entityType;
        private boolean implementsQusaqRepository;

        public QusaqRepositoryClassVisitor(int api, ClassVisitor classVisitor,
                                            String className, IndexView indexView) {
            super(api, classVisitor);
            this.indexView = indexView;
            this.className = className;
            this.implementsQusaqRepository = false;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {

            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (iface.startsWith(QUSAQ_REPOSITORY_INTERNAL_NAME)) {
                        implementsQusaqRepository = true;
                        extractEntityType();
                        if (entityType != null) {
                            log.debugf("Repository %s implements QusaqRepository<%s> - will generate bridge methods",
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

            if (!implementsQusaqRepository || entityType == null) {
                return mv;
            }

            if (isGenerateBridgeMethod(name)) {
                log.debugf("Generating bridge implementation for %s.%s%s", className, name, descriptor);
                return new BridgeMethodReplacer(api, mv, name, entityType);
            }

            return mv;
        }

        private void extractEntityType() {
            try {
                DotName classNameDot = DotName.createSimple(className);
                ClassInfo classInfo = indexView.getClassByName(classNameDot);
                if (classInfo == null) {
                    log.warnf("Could not find ClassInfo for %s", className);
                    return;
                }

                DotName qusaqRepositoryName = DotName.createSimple(QUSAQ_REPOSITORY_CLASS_NAME);

                for (org.jboss.jandex.Type interfaceType : classInfo.interfaceTypes()) {
                    if (interfaceType.name().equals(qusaqRepositoryName) && interfaceType.kind() == PARAMETERIZED_TYPE) {
                        ParameterizedType parameterizedType = interfaceType.asParameterizedType();
                        List<org.jboss.jandex.Type> typeArguments = parameterizedType.arguments();
                        if (!typeArguments.isEmpty()) {
                            org.jboss.jandex.Type jandexType = typeArguments.get(0);
                            String entityClassName = jandexType.name().toString();
                            this.entityType = Type.getObjectType(entityClassName.replace('.', '/'));
                            log.debugf("Extracted entity type: %s", entityClassName);
                            return;
                        }
                    }
                }

                log.warnf("Could not extract entity type for repository %s", className);
            } catch (Exception e) {
                log.warnf(e, "Failed to extract entity type for %s", className);
            }
        }

        private boolean isGenerateBridgeMethod(String methodName) {
            return QUERY_METHOD_NAMES.contains(methodName);
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

            QusaqBytecodeGenerator.DelegationMethodConfig config = switch (methodName) {
                case METHOD_FIND_WHERE -> QusaqBytecodeGenerator.DelegationMethodConfig.forFindWhere(
                        entityType, entityInternalName, false);
                case METHOD_COUNT_WHERE -> QusaqBytecodeGenerator.DelegationMethodConfig.forCountWhere(
                        entityType, entityInternalName, false);
                case METHOD_EXISTS -> QusaqBytecodeGenerator.DelegationMethodConfig.forExists(
                        entityType, entityInternalName, false);
                default -> {
                    log.warnf("Unknown bridge method: %s", methodName);
                    yield null;
                }
            };

            if (config == null) {
                return;
            }

            mv.visitLdcInsn(entityType);

            mv.visitVarInsn(Opcodes.ALOAD, config.querySpecParameterIndex());

            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    QUSAQ_OPERATIONS_INTERNAL_NAME,
                    config.methodName(),
                    config.operationsMethodDescriptor(),
                    false);

            mv.visitInsn(config.returnOpcode());

            mv.visitMaxs(config.maxStack(), config.maxLocals());
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
