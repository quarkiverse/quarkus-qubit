package io.quarkiverse.qubit.deployment.testutil.fixtures;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/**
 * Fluent builders for ASM ClassNode and MethodNode test fixtures.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * import static ...fixtures.AsmFixtures.*;
 *
 * MethodNode method = testMethod().build();
 * MethodNode staticMethod = testMethod().asStatic().withName("lambda$0").build();
 * ClassNode clazz = testClass().withMethod(method).build();
 * }</pre>
 */
public final class AsmFixtures {

    private AsmFixtures() {
    }

    /** Creates a default test method builder. */
    public static MethodNodeBuilder testMethod() {
        return new MethodNodeBuilder();
    }

    /** Fluent builder for MethodNode. */
    public static class MethodNodeBuilder {
        private String name = "testLambda";
        private String desc = "(Ljava/lang/Object;)Z";
        private int access = ACC_PUBLIC;
        private InsnList instructions;

        public MethodNodeBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public MethodNodeBuilder withDesc(String desc) {
            this.desc = desc;
            return this;
        }

        public MethodNodeBuilder asStatic() {
            this.access |= ACC_STATIC;
            return this;
        }

        public MethodNodeBuilder withAccess(int access) {
            this.access = access;
            return this;
        }

        public MethodNodeBuilder withInstructions(InsnList instructions) {
            this.instructions = instructions;
            return this;
        }

        public MethodNode build() {
            MethodNode method = new MethodNode();
            method.name = name;
            method.desc = desc;
            method.access = access;
            method.instructions = instructions != null ? instructions : new InsnList();
            return method;
        }
    }

}
