package io.quarkus.qusaq.deployment;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static io.quarkus.qusaq.runtime.QusaqConstants.COUNT_QUERY_METHOD_NAMES;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUERY_METHOD_NAMES;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUERY_SPEC_DESCRIPTOR;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_ENTITY_CLASS_NAME;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUSAQ_REPOSITORY_CLASS_NAME;

/**
 * Scans bytecode for invokedynamic instructions creating QuerySpec lambdas.
 * Discovery only - does not analyze lambda contents.
 */
public class InvokeDynamicScanner {

    private static final Logger log = Logger.getLogger(InvokeDynamicScanner.class);
    private final IndexView index;

    public InvokeDynamicScanner(IndexView index) {
        this.index = index;
    }

    /**
     * Discovered lambda call site.
     */
    public record LambdaCallSite(
            String ownerClassName,
            String methodName,
            String lambdaMethodName,
            String lambdaMethodDescriptor,
            String targetMethodName,
            int lineNumber) {

        /**
         * Returns true if this is a count query.
         */
        public boolean isCountQuery() {
            return COUNT_QUERY_METHOD_NAMES.contains(targetMethodName);
        }

        /**
         * Returns unique identifier for this call site.
         */
        public String getCallSiteId() {
            return ownerClassName + ":" + methodName + ":" + lineNumber;
        }

        @Override
        public String toString() {
            return String.format("LambdaCallSite{%s.%s line %d, lambda=%s, target=%s}",
                    ownerClassName, methodName, lineNumber, lambdaMethodName, targetMethodName);
        }
    }

    /**
     * Scans class bytecode for QuerySpec lambda call sites.
     */
    public List<LambdaCallSite> scanClass(byte[] classBytes, String className) {
        List<LambdaCallSite> callSites = new ArrayList<>();

        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            for (MethodNode method : classNode.methods) {
                scanMethod(classNode, method, callSites);
            }
        } catch (Exception e) {
            log.warnf(e, "Failed to scan class %s for lambda call sites", className);
        }

        return callSites;
    }

    private void scanMethod(ClassNode classNode, MethodNode method, List<LambdaCallSite> callSites) {
        InsnList instructions = method.instructions;
        int currentLine = -1;
        String pendingLambdaMethod = null;
        String pendingLambdaDescriptor = null;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            if (insn instanceof LineNumberNode lineNumberNode) {
                currentLine = lineNumberNode.line;
            }

            if (insn instanceof InvokeDynamicInsnNode invokeDynamic && isQuerySpecLambda(invokeDynamic)) {
                Handle lambdaHandle = extractLambdaHandle(invokeDynamic);
                if (lambdaHandle != null) {
                    pendingLambdaMethod = lambdaHandle.getName();
                    pendingLambdaDescriptor = lambdaHandle.getDesc();
                }
            }

            if (insn instanceof MethodInsnNode methodCall && isQusaqEntityCall(methodCall) && pendingLambdaMethod != null) {
                String targetMethod = methodCall.name;

                LambdaCallSite callSite = new LambdaCallSite(
                        classNode.name.replace('/', '.'),
                        method.name,
                        pendingLambdaMethod,
                        pendingLambdaDescriptor,
                        targetMethod,
                        currentLine
                );

                callSites.add(callSite);
                log.debugf("Found lambda call site: %s", callSite);

                pendingLambdaMethod = null;
                pendingLambdaDescriptor = null;
            }
        }
    }

    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR);
    }

    private Handle extractLambdaHandle(InvokeDynamicInsnNode invokeDynamic) {
        Object[] bsmArgs = invokeDynamic.bsmArgs;

        if (bsmArgs != null && bsmArgs.length >= 2 && bsmArgs[1] instanceof Handle handle) {
            return handle;
        }

        return null;
    }

    private boolean isQusaqEntityCall(MethodInsnNode methodCall) {
        String owner = methodCall.owner.replace('/', '.');
        String name = methodCall.name;

        if (!QUERY_METHOD_NAMES.contains(name)) {
            return false;
        }

        if (QUSAQ_ENTITY_CLASS_NAME.equals(owner)) {
            return true;
        }

        if (implementsQusaqRepository(owner)) {
            return true;
        }

        return implementsQusaqEntity(owner);
    }

    private boolean implementsQusaqRepository(String className) {
        DotName qusaqRepositoryName = DotName.createSimple(QUSAQ_REPOSITORY_CLASS_NAME);
        DotName classNameDot = DotName.createSimple(className);

        ClassInfo classInfo = index.getClassByName(classNameDot);
        if (classInfo == null) {
            return false;
        }

        for (org.jboss.jandex.Type interfaceType : classInfo.interfaceTypes()) {
            if (interfaceType.name().equals(qusaqRepositoryName)) {
                return true;
            }
        }

        return false;
    }

    private boolean implementsQusaqEntity(String className) {
        DotName qusaqEntityName = DotName.createSimple(QUSAQ_ENTITY_CLASS_NAME);
        DotName classNameDot = DotName.createSimple(className);

        ClassInfo classInfo = index.getClassByName(classNameDot);
        if (classInfo == null) {
            return false;
        }

        ClassInfo currentClass = classInfo;
        while (currentClass != null) {
            if (currentClass.superName() != null && currentClass.superName().equals(qusaqEntityName)) {
                return true;
            }

            if (currentClass.superName() != null) {
                currentClass = index.getClassByName(currentClass.superName());
            } else {
                break;
            }
        }

        return false;
    }

}
