package io.quarkus.qusaq.deployment;

import org.jboss.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.util.function.BiFunction;

/**
 * Injects static query methods into QusaqEntity subclasses (ActiveRecord pattern).
 */
public class QusaqEntityEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    private static final Logger log = Logger.getLogger(QusaqEntityEnhancer.class);

    /**
     * Creates class visitor that generates query methods for QusaqEntity subclass.
     */
    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        log.debugf("Checking entity: %s for QusaqEntity extension", className);
        return new QusaqEntityOperationGenerationVisitor(
                Opcodes.ASM9,
                outputClassVisitor,
                className);
    }
}
