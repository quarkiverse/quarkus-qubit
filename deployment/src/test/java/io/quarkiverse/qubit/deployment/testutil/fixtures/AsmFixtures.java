package io.quarkiverse.qubit.deployment.testutil.fixtures;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/** Fluent builders for ASM MethodNode test fixtures. */
public final class AsmFixtures {

    private AsmFixtures() {
    }

    /** Creates a default test method builder. */
    public static MethodNodeBuilder testMethod() {
        return new MethodNodeBuilder();
    }

    /** Fluent builder for MethodNode. */
    public static class MethodNodeBuilder {
        private final String name = "testLambda";
        private final String desc = "(Ljava/lang/Object;)Z";
        private final int access = ACC_PUBLIC;

        public MethodNode build() {
            MethodNode method = new MethodNode();
            method.name = name;
            method.desc = desc;
            method.access = access;
            method.instructions = new InsnList();
            return method;
        }
    }

}
