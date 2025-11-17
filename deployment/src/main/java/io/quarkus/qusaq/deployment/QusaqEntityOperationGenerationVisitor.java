package io.quarkus.qusaq.deployment;

import org.jboss.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_ENTITY_INTERNAL_NAME;

/**
 * Injects static query methods into QusaqEntity subclasses.
 */
public class QusaqEntityOperationGenerationVisitor extends ClassVisitor {

    private static final Logger log = Logger.getLogger(QusaqEntityOperationGenerationVisitor.class);

    private final String entityClassName;
    private final String entityInternalName;
    private Type entityType;
    private boolean extendsQusaqEntity;

    public QusaqEntityOperationGenerationVisitor(
            int api,
            ClassVisitor classVisitor,
            String entityClassName) {
        super(api, classVisitor);
        this.entityClassName = entityClassName;
        this.entityInternalName = entityClassName.replace('.', '/');
        this.extendsQusaqEntity = false;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {

        if (superName != null && superName.equals(QUSAQ_ENTITY_INTERNAL_NAME)) {
            extendsQusaqEntity = true;
            entityType = Type.getObjectType(entityInternalName);
            log.debugf("Entity %s extends QusaqEntity - will replace abstract methods", entityClassName);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        if (extendsQusaqEntity) {
            generateFindWhereMethod();
            generateCountWhereMethod();
            generateExistsMethod();
        }

        super.visitEnd();
    }

    private void generateFindWhereMethod() {
        var config = QusaqBytecodeGenerator.DelegationMethodConfig.forFindWhere(
                entityType, entityInternalName, true);
        QusaqBytecodeGenerator.generateDelegationMethod(cv, config);
    }

    private void generateCountWhereMethod() {
        var config = QusaqBytecodeGenerator.DelegationMethodConfig.forCountWhere(
                entityType, entityInternalName, true);
        QusaqBytecodeGenerator.generateDelegationMethod(cv, config);
    }

    private void generateExistsMethod() {
        var config = QusaqBytecodeGenerator.DelegationMethodConfig.forExists(
                entityType, entityInternalName, true);
        QusaqBytecodeGenerator.generateDelegationMethod(cv, config);
    }
}
