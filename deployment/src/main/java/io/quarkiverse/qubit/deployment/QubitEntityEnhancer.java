package io.quarkiverse.qubit.deployment;

import java.util.function.BiFunction;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.logging.Log;

/**
 * Injects static query methods into QubitEntity subclasses (ActiveRecord pattern).
 */
public class QubitEntityEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    /**
     * Creates class visitor that generates query methods for QubitEntity subclass.
     */
    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        Log.debugf("Checking entity: %s for QubitEntity extension", className);
        return new QubitEntityOperationGenerationVisitor(
                Opcodes.ASM9,
                outputClassVisitor,
                className);
    }
}
