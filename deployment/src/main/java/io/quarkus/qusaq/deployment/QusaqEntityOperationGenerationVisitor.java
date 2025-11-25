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

        if (QUSAQ_ENTITY_INTERNAL_NAME.equals(superName)) {
            extendsQusaqEntity = true;
            entityType = Type.getObjectType(entityInternalName);
            log.debugf("Entity %s extends QusaqEntity - will replace abstract methods", entityClassName);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        if (extendsQusaqEntity) {
            // Generate new fluent API entry points (Iteration 3)
            generateWhereMethod();
            generateSelectMethod();
            generateSortedByMethod();
            generateSortedDescendingByMethod();

            // Phase 5: Generate aggregation entry points
            generateMinMethod();
            generateMaxMethod();
            generateAvgMethod();
            generateSumIntegerMethod();
            generateSumLongMethod();
            generateSumDoubleMethod();

            // Iteration 6: Generate join entry points
            generateJoinMethod();
            generateLeftJoinMethod();

            // Note: count() and findAll() are inherited from PanacheEntityBase
        }

        super.visitEnd();
    }

    private void generateWhereMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forWhere(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSelectMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forSelect(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSortedByMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forSortedBy(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSortedDescendingByMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forSortedDescendingBy(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    // Phase 5: Aggregation method generation

    private void generateMinMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forMin(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateMaxMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forMax(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateAvgMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forAvg(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSumIntegerMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forSumInteger(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSumLongMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forSumLong(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSumDoubleMethod() {
        var config = QusaqBytecodeGenerator.FluentMethodConfig.forSumDouble(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    // Iteration 6: Join method generation

    private void generateJoinMethod() {
        var config = QusaqBytecodeGenerator.JoinMethodConfig.forJoin(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateJoinEntryPoint(cv, config);
    }

    private void generateLeftJoinMethod() {
        var config = QusaqBytecodeGenerator.JoinMethodConfig.forLeftJoin(
                entityType, entityInternalName);
        QusaqBytecodeGenerator.generateJoinEntryPoint(cv, config);
    }
}
