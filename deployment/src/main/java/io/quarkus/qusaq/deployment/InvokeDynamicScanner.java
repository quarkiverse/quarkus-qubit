package io.quarkus.qusaq.deployment;

import io.quarkus.qusaq.runtime.SortDirection;
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

import static io.quarkus.qusaq.runtime.QusaqConstants.*;

/**
 * Scans bytecode for invokedynamic instructions creating QuerySpec lambdas.
 * Detects fluent API terminal operations.
 */
public class InvokeDynamicScanner {

    private static final Logger log = Logger.getLogger(InvokeDynamicScanner.class);

    /**
     * Pair of lambda method name and descriptor.
     */
    public record LambdaPair(String methodName, String descriptor) {}

    /**
     * Sort lambda with direction (ascending/descending).
     * Phase 3: Used to track sortedBy() and sortedDescendingBy() operations.
     */
    public record SortLambda(String methodName, String descriptor, SortDirection direction) {}

    /**
     * Discovered lambda call site for fluent API terminal operations.
     * Phase 2.2: Enhanced to track both predicate and projection lambdas for combined queries.
     * Phase 2.5: Enhanced to support multiple where() predicates.
     * Phase 3: Enhanced to support sorting (sortedBy/sortedDescendingBy).
     */
    public record LambdaCallSite(
            String ownerClassName,
            String methodName,
            String lambdaMethodName,           // Primary lambda (for backward compatibility)
            String lambdaMethodDescriptor,     // Primary lambda descriptor
            String fluentMethodName,           // Primary fluent method (for backward compatibility)
            String targetMethodName,
            int lineNumber,
            int terminalInsnIndex,
            String projectionLambdaMethodName,     // SELECT lambda (null if no select clause)
            String projectionLambdaMethodDescriptor, // SELECT lambda descriptor
            List<LambdaPair> predicateLambdas,     // Phase 2.5: ALL WHERE lambdas for multiple where() support
            List<SortLambda> sortLambdas) {        // Phase 3: ALL sort lambdas with direction

        /**
         * Returns true if this is a count query.
         * Both count() and exists() are count queries since exists() delegates to count().
         */
        public boolean isCountQuery() {
            return METHOD_COUNT.equals(targetMethodName) || METHOD_EXISTS.equals(targetMethodName);
        }

        /**
         * Returns true if this is a projection query (select).
         * Phase 2.2: Checks if projection lambda is present.
         * Phase 3: Excludes sorting methods from being treated as projections.
         */
        public boolean isProjectionQuery() {
            // Phase 2.2: Check if projection lambda is present
            if (projectionLambdaMethodName != null) {
                return true;
            }

            // Phase 2.1 backward compatibility: Check fluent method name
            if (METHOD_SELECT.equals(fluentMethodName)) {
                return true;
            }

            if (METHOD_WHERE.equals(fluentMethodName)) {
                return false;
            }

            // Phase 3: Sorting methods are not projections
            if (METHOD_SORTED_BY.equals(fluentMethodName) || METHOD_SORTED_DESCENDING_BY.equals(fluentMethodName)) {
                return false;
            }

            // Fallback: Use descriptor heuristic
            boolean returnsBoolean = lambdaMethodDescriptor.endsWith(")Z") ||
                                    lambdaMethodDescriptor.endsWith(")Ljava/lang/Boolean;");

            if (returnsBoolean) {
                return false;
            }

            log.warnf("Treating as projection (non-boolean): descriptor=%s, fluent=%s", lambdaMethodDescriptor, fluentMethodName);
            return true;
        }

        /**
         * Returns true if this call site has both a where() predicate and a select() projection.
         * This indicates a Phase 2.2 combined query.
         */
        public boolean isCombinedQuery() {
            return (predicateLambdas != null && !predicateLambdas.isEmpty()) && projectionLambdaMethodName != null;
        }

        /**
         * Returns unique identifier for this call site.
         */
        public String getCallSiteId() {
            return ownerClassName + ":" + methodName + ":" + lineNumber;
        }

        @Override
        public String toString() {
            if (isCombinedQuery()) {
                int predicateCount = predicateLambdas != null ? predicateLambdas.size() : 0;
                return String.format("LambdaCallSite{%s.%s line %d, where=%d predicates, select=%s, target=%s}",
                        ownerClassName, methodName, lineNumber,
                        predicateCount, projectionLambdaMethodName, targetMethodName);
            }
            return String.format("LambdaCallSite{%s.%s line %d, lambda=%s, fluent=%s, target=%s}",
                    ownerClassName, methodName, lineNumber, lambdaMethodName, fluentMethodName, targetMethodName);
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

    /**
     * Tracked lambda in the pipeline.
     */
    private record PendingLambda(String methodName, String descriptor, String fluentMethod) {}

    /**
     * Grouped lambda information for building call sites.
     */
    private record LambdaInfo(
        String primaryLambdaMethod,
        String primaryLambdaDescriptor,
        String primaryFluentMethod,
        String firstWhereLambdaMethod,
        String firstWhereLambdaDescriptor,
        String selectLambdaMethod,
        String selectLambdaDescriptor,
        List<LambdaPair> whereLambdas,
        List<SortLambda> sortLambdas
    ) {}

    private void scanMethod(ClassNode classNode, MethodNode method, List<LambdaCallSite> callSites) {
        InsnList instructions = method.instructions;
        List<PendingLambda> pendingLambdas = new ArrayList<>();
        int currentLine = -1;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            currentLine = updateLineNumber(insn, currentLine);
            detectAndAddLambda(insn, instructions, i, pendingLambdas);

            if (isTerminalOperation(insn, pendingLambdas)) {
                LambdaCallSite callSite = createCallSite(classNode, method, (MethodInsnNode) insn,
                                                          pendingLambdas, currentLine, i);
                callSites.add(callSite);
                log.debugf("Found fluent API terminal operation: %s", callSite);
                pendingLambdas.clear();
            }
        }
    }

    private int updateLineNumber(AbstractInsnNode insn, int currentLine) {
        return (insn instanceof LineNumberNode lineNode) ? lineNode.line : currentLine;
    }

    private void detectAndAddLambda(AbstractInsnNode insn, InsnList instructions, int index,
                                     List<PendingLambda> pendingLambdas) {
        if (insn instanceof InvokeDynamicInsnNode invokeDynamic && isQuerySpecLambda(invokeDynamic)) {
            Handle handle = extractLambdaHandle(invokeDynamic);
            if (handle != null) {
                String fluentMethod = findFluentMethodForward(instructions, index);
                pendingLambdas.add(new PendingLambda(handle.getName(), handle.getDesc(), fluentMethod));
            }
        }
    }

    private boolean isTerminalOperation(AbstractInsnNode insn, List<PendingLambda> pendingLambdas) {
        return insn instanceof MethodInsnNode methodCall
            && !pendingLambdas.isEmpty()
            && isQusaqStreamTerminalCall(methodCall);
    }

    private LambdaCallSite createCallSite(ClassNode classNode, MethodNode method, MethodInsnNode methodCall,
                                          List<PendingLambda> pendingLambdas, int lineNumber, int insnIndex) {
        LambdaInfo info = extractLambdaInfo(pendingLambdas);
        return new LambdaCallSite(
            classNode.name.replace('/', '.'),
            method.name,
            info.primaryLambdaMethod,
            info.primaryLambdaDescriptor,
            info.primaryFluentMethod,
            methodCall.name,
            lineNumber,
            insnIndex,
            info.selectLambdaMethod,
            info.selectLambdaDescriptor,
            info.whereLambdas,
            info.sortLambdas
        );
    }

    private LambdaInfo extractLambdaInfo(List<PendingLambda> pendingLambdas) {
        List<LambdaPair> whereLambdas = new ArrayList<>();
        List<SortLambda> sortLambdas = new ArrayList<>();
        String firstWhereMethod = null;
        String firstWhereDescriptor = null;
        String selectMethod = null;
        String selectDescriptor = null;

        for (PendingLambda lambda : pendingLambdas) {
            if (METHOD_WHERE.equals(lambda.fluentMethod)) {
                whereLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
                if (firstWhereMethod == null) {
                    firstWhereMethod = lambda.methodName;
                    firstWhereDescriptor = lambda.descriptor;
                }
            } else if (METHOD_SELECT.equals(lambda.fluentMethod)) {
                selectMethod = lambda.methodName;
                selectDescriptor = lambda.descriptor;
            } else if (METHOD_SORTED_BY.equals(lambda.fluentMethod)) {
                // Ascending sort
                sortLambdas.add(new SortLambda(lambda.methodName, lambda.descriptor, SortDirection.ASCENDING));
            } else if (METHOD_SORTED_DESCENDING_BY.equals(lambda.fluentMethod)) {
                // Descending sort
                sortLambdas.add(new SortLambda(lambda.methodName, lambda.descriptor, SortDirection.DESCENDING));
            }
        }

        PendingLambda first = pendingLambdas.isEmpty() ? null : pendingLambdas.get(0);
        return new LambdaInfo(
            first != null ? first.methodName : null,
            first != null ? first.descriptor : null,
            first != null && first.fluentMethod != null ? first.fluentMethod : METHOD_WHERE,
            firstWhereMethod,
            firstWhereDescriptor,
            selectMethod,
            selectDescriptor,
            whereLambdas.isEmpty() ? null : whereLambdas,
            sortLambdas.isEmpty() ? null : sortLambdas
        );
    }

    /**
     * Looks forward from invokedynamic to find the fluent method call (where/select).
     */
    private String findFluentMethodForward(InsnList instructions, int startIndex) {
        // Look ahead a few instructions to find where/select call
        for (int j = startIndex + 1; j < Math.min(startIndex + 5, instructions.size()); j++) {
            AbstractInsnNode nextInsn = instructions.get(j);
            if (nextInsn instanceof MethodInsnNode methodCall) {
                String methodName = methodCall.name;
                if (FLUENT_ENTRY_POINT_METHODS.contains(methodName) || FLUENT_INTERMEDIATE_METHODS.contains(methodName)) {
                    return methodName;
                }
            }
        }
        return null;
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

    /**
     * Checks if method call is a terminal operation on QusaqStream.
     */
    private boolean isQusaqStreamTerminalCall(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a terminal method name
        if (!FLUENT_TERMINAL_METHODS.contains(name)) {
            return false;
        }

        // Check if owner is QusaqStream interface
        return QUSAQ_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

}
