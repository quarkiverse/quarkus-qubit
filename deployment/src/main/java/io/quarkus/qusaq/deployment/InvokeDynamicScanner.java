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
 * <p>
 * Iteration 6: Enhanced to support join queries with BiQuerySpec lambdas.
 * Detects join()/leftJoin() entry points and JoinStream terminal operations.
 */
public class InvokeDynamicScanner {

    /**
     * Join type for join queries.
     * Iteration 6: Tracks whether a join is INNER or LEFT OUTER.
     */
    public enum JoinType {
        /** Standard inner join - excludes source entities without matching joined entities. */
        INNER,
        /** Left outer join - includes all source entities even without matching joined entities. */
        LEFT
    }

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
     * Phase 5: Enhanced to support aggregations (min/max/avg/sum*).
     * Iteration 6: Enhanced to support join queries with BiQuerySpec lambdas.
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
            List<SortLambda> sortLambdas,          // Phase 3: ALL sort lambdas with direction
            String aggregationLambdaMethodName,    // Phase 5: Aggregation mapper lambda (e.g., p -> p.salary for min/max/avg/sum)
            String aggregationLambdaMethodDescriptor, // Phase 5: Aggregation mapper descriptor
            JoinType joinType,                     // Iteration 6: Join type (INNER/LEFT) or null if not a join query
            String joinRelationshipLambdaMethodName,    // Iteration 6: Join relationship lambda (e.g., p -> p.phones)
            String joinRelationshipLambdaDescriptor,    // Iteration 6: Join relationship lambda descriptor
            List<LambdaPair> biEntityPredicateLambdas) { // Iteration 6: BiQuerySpec WHERE lambdas for join queries

        /**
         * Returns true if this is a count query.
         * Both count() and exists() are count queries since exists() delegates to count().
         */
        public boolean isCountQuery() {
            return METHOD_COUNT.equals(targetMethodName) || METHOD_EXISTS.equals(targetMethodName);
        }

        /**
         * Returns true if this is an aggregation query (min, max, avg, sum*).
         * Phase 5: Aggregation terminals require mapper lambda analysis.
         */
        public boolean isAggregationQuery() {
            return METHOD_MIN.equals(targetMethodName) ||
                   METHOD_MAX.equals(targetMethodName) ||
                   METHOD_AVG.equals(targetMethodName) ||
                   METHOD_SUM_INTEGER.equals(targetMethodName) ||
                   METHOD_SUM_LONG.equals(targetMethodName) ||
                   METHOD_SUM_DOUBLE.equals(targetMethodName);
        }

        /**
         * Returns true if this is a join query.
         * Iteration 6: Join queries have a non-null join type.
         */
        public boolean isJoinQuery() {
            return joinType != null;
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

            // Phase 5: Aggregation methods are not projections - they are aggregations
            if (METHOD_MIN.equals(fluentMethodName) || METHOD_MAX.equals(fluentMethodName) ||
                METHOD_AVG.equals(fluentMethodName) || METHOD_SUM_INTEGER.equals(fluentMethodName) ||
                METHOD_SUM_LONG.equals(fluentMethodName) || METHOD_SUM_DOUBLE.equals(fluentMethodName)) {
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
            if (isJoinQuery()) {
                int biPredicateCount = biEntityPredicateLambdas != null ? biEntityPredicateLambdas.size() : 0;
                return String.format("LambdaCallSite{%s.%s line %d, %s JOIN, relationship=%s, biPredicates=%d, target=%s}",
                        ownerClassName, methodName, lineNumber,
                        joinType, joinRelationshipLambdaMethodName, biPredicateCount, targetMethodName);
            }
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
     * Iteration 6: Added isBiEntity to track BiQuerySpec vs QuerySpec lambdas.
     */
    private record PendingLambda(String methodName, String descriptor, String fluentMethod, boolean isBiEntity) {}

    /**
     * Tracked aggregation method in the pipeline.
     * Phase 5: Used to detect aggregation methods that are now intermediate operations.
     */
    private record PendingAggregation(String aggregationMethod) {}

    /**
     * Grouped lambda information for building call sites.
     * Phase 5: Added aggregation lambda fields.
     * Iteration 6: Added join-related fields.
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
        List<SortLambda> sortLambdas,
        String aggregationLambdaMethod,      // Phase 5: Aggregation mapper lambda
        String aggregationLambdaDescriptor,  // Phase 5: Aggregation mapper descriptor
        String joinRelationshipLambdaMethod,   // Iteration 6: Join relationship lambda
        String joinRelationshipLambdaDescriptor, // Iteration 6: Join relationship descriptor
        List<LambdaPair> biEntityWhereLambdas  // Iteration 6: BiQuerySpec WHERE lambdas
    ) {}

    private void scanMethod(ClassNode classNode, MethodNode method, List<LambdaCallSite> callSites) {
        InsnList instructions = method.instructions;
        List<PendingLambda> pendingLambdas = new ArrayList<>();
        PendingAggregation pendingAggregation = null;
        JoinType pendingJoinType = null;  // Iteration 6: Track join type
        int currentLine = -1;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            currentLine = updateLineNumber(insn, currentLine);
            detectAndAddLambda(insn, instructions, i, pendingLambdas);

            // Phase 5: Detect aggregation method calls (now intermediate operations)
            if (insn instanceof MethodInsnNode methodCall && isAggregationMethod(methodCall)) {
                pendingAggregation = new PendingAggregation(methodCall.name);
            }

            // Iteration 6: Detect join method calls
            if (insn instanceof MethodInsnNode methodCall && isJoinMethod(methodCall)) {
                pendingJoinType = METHOD_LEFT_JOIN.equals(methodCall.name) ? JoinType.LEFT : JoinType.INNER;
            }

            if (isTerminalOperation(insn, pendingLambdas, pendingJoinType)) {
                LambdaCallSite callSite = createCallSite(classNode, method, (MethodInsnNode) insn,
                                                          pendingLambdas, pendingAggregation, pendingJoinType, currentLine, i);
                callSites.add(callSite);
                log.debugf("Found fluent API terminal operation: %s", callSite);
                pendingLambdas.clear();
                pendingAggregation = null;
                pendingJoinType = null;  // Iteration 6: Reset join type
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
                // Iteration 6: Check if this is a BiQuerySpec lambda
                boolean isBiEntity = invokeDynamic.desc.contains(BI_QUERY_SPEC_DESCRIPTOR);
                pendingLambdas.add(new PendingLambda(handle.getName(), handle.getDesc(), fluentMethod, isBiEntity));
            }
        }
    }

    /**
     * Checks if instruction is a terminal operation.
     * Iteration 6: Also checks for JoinStream terminal calls when in join context.
     */
    private boolean isTerminalOperation(AbstractInsnNode insn, List<PendingLambda> pendingLambdas, JoinType joinType) {
        if (!(insn instanceof MethodInsnNode methodCall) || pendingLambdas.isEmpty()) {
            return false;
        }

        // Iteration 6: Check for JoinStream terminals when in join context
        if (joinType != null) {
            return isJoinStreamTerminalCall(methodCall);
        }

        return isQusaqStreamTerminalCall(methodCall);
    }

    private LambdaCallSite createCallSite(ClassNode classNode, MethodNode method, MethodInsnNode methodCall,
                                          List<PendingLambda> pendingLambdas, PendingAggregation pendingAggregation,
                                          JoinType pendingJoinType,
                                          int lineNumber, int insnIndex) {
        // Phase 5: Pass aggregation method to extractLambdaInfo
        String aggregationMethod = pendingAggregation != null ? pendingAggregation.aggregationMethod : null;
        LambdaInfo info = extractLambdaInfo(pendingLambdas, methodCall.name, aggregationMethod);

        // Phase 5: For aggregation queries, use aggregation method as target instead of getSingleResult
        String targetMethod = aggregationMethod != null ? aggregationMethod : methodCall.name;

        return new LambdaCallSite(
            classNode.name.replace('/', '.'),
            method.name,
            info.primaryLambdaMethod,
            info.primaryLambdaDescriptor,
            info.primaryFluentMethod,
            targetMethod,
            lineNumber,
            insnIndex,
            info.selectLambdaMethod,
            info.selectLambdaDescriptor,
            info.whereLambdas,
            info.sortLambdas,
            info.aggregationLambdaMethod,
            info.aggregationLambdaDescriptor,
            pendingJoinType,  // Iteration 6: Join type
            info.joinRelationshipLambdaMethod,  // Iteration 6: Join relationship lambda
            info.joinRelationshipLambdaDescriptor,  // Iteration 6: Join relationship descriptor
            info.biEntityWhereLambdas  // Iteration 6: BiQuerySpec WHERE lambdas
        );
    }

    private LambdaInfo extractLambdaInfo(List<PendingLambda> pendingLambdas, String terminalMethodName, String aggregationMethodName) {
        List<LambdaPair> whereLambdas = new ArrayList<>();
        List<SortLambda> sortLambdas = new ArrayList<>();
        List<LambdaPair> biEntityWhereLambdas = new ArrayList<>();  // Iteration 6: BiQuerySpec WHERE lambdas
        String firstWhereMethod = null;
        String firstWhereDescriptor = null;
        String selectMethod = null;
        String selectDescriptor = null;
        String aggregationMethod = null;
        String aggregationDescriptor = null;
        String joinRelationshipMethod = null;  // Iteration 6: Join relationship lambda
        String joinRelationshipDescriptor = null;

        // Phase 5: Use provided aggregation method name (detected from intermediate operation)
        boolean isAggregation = aggregationMethodName != null;

        // Phase 5: If aggregation, the last lambda is the mapper (e.g., p -> p.salary)
        // All others are treated as WHERE predicates
        for (int i = 0; i < pendingLambdas.size(); i++) {
            PendingLambda lambda = pendingLambdas.get(i);
            boolean isLastLambda = (i == pendingLambdas.size() - 1);

            // Phase 5: For aggregations, last lambda is the mapper
            if (isAggregation && isLastLambda) {
                aggregationMethod = lambda.methodName;
                aggregationDescriptor = lambda.descriptor;
            } else if (METHOD_JOIN.equals(lambda.fluentMethod) || METHOD_LEFT_JOIN.equals(lambda.fluentMethod)) {
                // Iteration 6: Join relationship lambda (e.g., p -> p.phones)
                joinRelationshipMethod = lambda.methodName;
                joinRelationshipDescriptor = lambda.descriptor;
            } else if (METHOD_WHERE.equals(lambda.fluentMethod)) {
                // Iteration 6: Check if this is a BiQuerySpec lambda (has two entity parameters)
                if (lambda.isBiEntity) {
                    biEntityWhereLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
                } else {
                    whereLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
                    if (firstWhereMethod == null) {
                        firstWhereMethod = lambda.methodName;
                        firstWhereDescriptor = lambda.descriptor;
                    }
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
            sortLambdas.isEmpty() ? null : sortLambdas,
            aggregationMethod,
            aggregationDescriptor,
            joinRelationshipMethod,
            joinRelationshipDescriptor,
            biEntityWhereLambdas.isEmpty() ? null : biEntityWhereLambdas
        );
    }

    /**
     * Looks forward from invokedynamic to find the fluent method call (where/select/join).
     * Iteration 6: Also recognizes join methods.
     */
    private String findFluentMethodForward(InsnList instructions, int startIndex) {
        // Look ahead a few instructions to find where/select/join call
        for (int j = startIndex + 1; j < Math.min(startIndex + 5, instructions.size()); j++) {
            AbstractInsnNode nextInsn = instructions.get(j);
            if (nextInsn instanceof MethodInsnNode methodCall) {
                String methodName = methodCall.name;
                if (FLUENT_ENTRY_POINT_METHODS.contains(methodName) ||
                    FLUENT_INTERMEDIATE_METHODS.contains(methodName) ||
                    JOIN_METHODS.contains(methodName)) {
                    return methodName;
                }
            }
        }
        return null;
    }

    /**
     * Checks if invokedynamic creates a QuerySpec or BiQuerySpec lambda.
     * Iteration 6: Also detects BiQuerySpec for join query lambdas.
     */
    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR) || desc.contains(BI_QUERY_SPEC_DESCRIPTOR);
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

    /**
     * Checks if method call is a terminal operation on JoinStream.
     * Iteration 6: JoinStream has the same terminal methods as QusaqStream.
     */
    private boolean isJoinStreamTerminalCall(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a terminal method name
        if (!FLUENT_TERMINAL_METHODS.contains(name)) {
            return false;
        }

        // Check if owner is JoinStream interface
        return JOIN_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is a join entry point (join or leftJoin).
     * Iteration 6: Detects join/leftJoin calls on QusaqEntity, QusaqRepository, or QusaqStream.
     */
    private boolean isJoinMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a join method name
        if (!METHOD_JOIN.equals(name) && !METHOD_LEFT_JOIN.equals(name)) {
            return false;
        }

        // Accept if called on QusaqEntity (static method)
        if (QUSAQ_ENTITY_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if called on QusaqRepository (instance method)
        if (QUSAQ_REPOSITORY_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if return type is JoinStream (covers virtual calls)
        String desc = methodCall.desc;
        return desc.contains("Lio/quarkus/qusaq/runtime/JoinStream;");
    }

    /**
     * Checks if method call is an aggregation method.
     * Phase 5: Aggregation methods are now intermediate operations.
     * Detects both:
     * - Instance calls on QusaqStream: stream.min(...)
     * - Static calls on entities: Person.min(...)
     */
    private boolean isAggregationMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's an aggregation method name
        boolean isAggregationName = METHOD_MIN.equals(name) ||
                                     METHOD_MAX.equals(name) ||
                                     METHOD_AVG.equals(name) ||
                                     METHOD_SUM_INTEGER.equals(name) ||
                                     METHOD_SUM_LONG.equals(name) ||
                                     METHOD_SUM_DOUBLE.equals(name);

        if (!isAggregationName) {
            return false;
        }

        // Accept if owner is QusaqStream interface (instance method call)
        if (QUSAQ_STREAM_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Also accept if it's a static method call (invokestatic) with QusaqStream return type
        // This handles direct static calls like Person.min(...)
        if (methodCall.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC) {
            // Check if return type is QusaqStream
            String desc = methodCall.desc;
            return desc.contains("Lio/quarkus/qusaq/runtime/QusaqStream;");
        }

        // Phase 5: Also accept instance method calls on QusaqRepository that return QusaqStream
        // This handles repository.min(...) calls
        if (methodCall.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL ||
            methodCall.getOpcode() == org.objectweb.asm.Opcodes.INVOKEINTERFACE) {
            String desc = methodCall.desc;
            return desc.contains("Lio/quarkus/qusaq/runtime/QusaqStream;");
        }

        return false;
    }

}
