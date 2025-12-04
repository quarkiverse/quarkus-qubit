package io.quarkiverse.qubit.deployment;

import io.quarkus.logging.Log;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import static io.quarkiverse.qubit.runtime.QubitConstants.QUBIT_ENTITY_INTERNAL_NAME;

/**
 * Injects static query methods into QubitEntity subclasses.
 */
public class QubitEntityOperationGenerationVisitor extends ClassVisitor {

    private final String entityClassName;
    private final String entityInternalName;
    private Type entityType;
    private boolean extendsQubitEntity;

    public QubitEntityOperationGenerationVisitor(
            int api,
            ClassVisitor classVisitor,
            String entityClassName) {
        super(api, classVisitor);
        this.entityClassName = entityClassName;
        this.entityInternalName = entityClassName.replace('.', '/');
        this.extendsQubitEntity = false;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {

        if (QUBIT_ENTITY_INTERNAL_NAME.equals(superName)) {
            extendsQubitEntity = true;
            entityType = Type.getObjectType(entityInternalName);
            Log.debugf("Entity %s extends QubitEntity - will replace abstract methods", entityClassName);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        if (extendsQubitEntity) {
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

            // Iteration 7: Generate groupBy entry point
            generateGroupByMethod();

            // Note: count() and findAll() are inherited from PanacheEntityBase
        }

        super.visitEnd();
    }

    private void generateWhereMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forWhere(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSelectMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forSelect(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSortedByMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forSortedBy(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSortedDescendingByMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forSortedDescendingBy(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    // Phase 5: Aggregation method generation

    private void generateMinMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forMin(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateMaxMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forMax(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateAvgMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forAvg(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSumIntegerMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forSumInteger(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSumLongMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forSumLong(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    private void generateSumDoubleMethod() {
        var config = QubitBytecodeGenerator.FluentMethodConfig.forSumDouble(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateFluentEntryPoint(cv, config);
    }

    // Iteration 6: Join method generation

    private void generateJoinMethod() {
        var config = QubitBytecodeGenerator.JoinMethodConfig.forJoin(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateJoinEntryPoint(cv, config);
    }

    private void generateLeftJoinMethod() {
        var config = QubitBytecodeGenerator.JoinMethodConfig.forLeftJoin(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateJoinEntryPoint(cv, config);
    }

    // Iteration 7: GroupBy method generation

    private void generateGroupByMethod() {
        var config = QubitBytecodeGenerator.GroupMethodConfig.forGroupBy(
                entityType, entityInternalName);
        QubitBytecodeGenerator.generateGroupEntryPoint(cv, config);
    }
}
