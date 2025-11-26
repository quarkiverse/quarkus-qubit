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
 * <p>
 * Iteration 7: Enhanced to support group queries with GroupQuerySpec lambdas.
 * Detects groupBy() entry points and GroupStream terminal operations.
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

    /**
     * Context type for tracking query context during scanning.
     * Iteration 7: Tracks whether we're in a group query context.
     */
    public enum QueryContext {
        /** Standard query (no special context). */
        STANDARD,
        /** Join query context (after join() or leftJoin()). */
        JOIN,
        /** Group query context (after groupBy()). */
        GROUP
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
     * Iteration 7: Enhanced to support group queries with GroupQuerySpec lambdas.
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
            List<LambdaPair> biEntityPredicateLambdas,  // Iteration 6: BiQuerySpec WHERE lambdas for join queries
            boolean isGroupQuery,                  // Iteration 7: True if this is a GROUP BY query
            String groupByLambdaMethodName,        // Iteration 7: groupBy() lambda (e.g., p -> p.department)
            String groupByLambdaDescriptor,        // Iteration 7: groupBy() lambda descriptor
            List<LambdaPair> havingLambdas,        // Iteration 7: having() lambdas (GroupQuerySpec)
            List<LambdaPair> groupSelectLambdas,   // Iteration 7: select() lambdas in group context (GroupQuerySpec)
            List<SortLambda> groupSortLambdas) {   // Iteration 7: sortedBy() lambdas in group context (GroupQuerySpec)

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
         * Returns true if this is a GROUP BY query.
         * Iteration 7: Group queries have isGroupQuery flag set.
         */
        public boolean isGroupByQuery() {
            return isGroupQuery;
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
            if (isGroupByQuery()) {
                int havingCount = havingLambdas != null ? havingLambdas.size() : 0;
                int selectCount = groupSelectLambdas != null ? groupSelectLambdas.size() : 0;
                return String.format("LambdaCallSite{%s.%s line %d, GROUP BY=%s, having=%d, groupSelect=%d, target=%s}",
                        ownerClassName, methodName, lineNumber,
                        groupByLambdaMethodName, havingCount, selectCount, targetMethodName);
            }
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
     * Lambda spec type for tracking lambda kind during scanning.
     * Iteration 7: Added GROUP for GroupQuerySpec lambdas.
     */
    private enum LambdaSpecType {
        /** Standard QuerySpec lambda. */
        QUERY_SPEC,
        /** BiQuerySpec lambda (for join queries). */
        BI_QUERY_SPEC,
        /** GroupQuerySpec lambda (for group queries). */
        GROUP_QUERY_SPEC
    }

    /**
     * Tracked lambda in the pipeline.
     * Iteration 6: Added isBiEntity to track BiQuerySpec vs QuerySpec lambdas.
     * Iteration 7: Enhanced with LambdaSpecType to support GroupQuerySpec.
     */
    private record PendingLambda(String methodName, String descriptor, String fluentMethod, LambdaSpecType specType) {
        boolean isBiEntity() {
            return specType == LambdaSpecType.BI_QUERY_SPEC;
        }

        boolean isGroupSpec() {
            return specType == LambdaSpecType.GROUP_QUERY_SPEC;
        }
    }

    /**
     * Tracked aggregation method in the pipeline.
     * Phase 5: Used to detect aggregation methods that are now intermediate operations.
     */
    private record PendingAggregation(String aggregationMethod) {}

    /**
     * Grouped lambda information for building call sites.
     * Phase 5: Added aggregation lambda fields.
     * Iteration 6: Added join-related fields.
     * Iteration 7: Added group-related fields.
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
        List<LambdaPair> biEntityWhereLambdas, // Iteration 6: BiQuerySpec WHERE lambdas
        boolean isGroup,                       // Iteration 7: True if this is a group query
        String groupByLambdaMethod,            // Iteration 7: groupBy() key extractor lambda
        String groupByLambdaDescriptor,        // Iteration 7: groupBy() lambda descriptor
        List<LambdaPair> havingLambdas,        // Iteration 7: having() lambdas
        List<LambdaPair> groupSelectLambdas,   // Iteration 7: select() on GroupStream
        List<SortLambda> groupSortLambdas      // Iteration 7: sortedBy() on GroupStream
    ) {}

    private void scanMethod(ClassNode classNode, MethodNode method, List<LambdaCallSite> callSites) {
        InsnList instructions = method.instructions;
        List<PendingLambda> pendingLambdas = new ArrayList<>();
        PendingAggregation pendingAggregation = null;
        JoinType pendingJoinType = null;  // Iteration 6: Track join type
        boolean pendingGroupQuery = false; // Iteration 7: Track group query context
        boolean pendingGroupSelectKey = false; // Iteration 7: Track selectKey() on GroupStream
        int currentLine = -1;
        int groupSelectLine = -1;  // Iteration 7: Track line of select() on GroupStream

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

            // Iteration 7: Detect groupBy method calls
            if (insn instanceof MethodInsnNode methodCall && isGroupMethod(methodCall)) {
                pendingGroupQuery = true;
            }

            // Iteration 7: Detect selectKey() method calls on GroupStream
            if (insn instanceof MethodInsnNode methodCall && isGroupSelectKeyMethod(methodCall)) {
                pendingGroupSelectKey = true;
                groupSelectLine = currentLine;  // Record line of selectKey()
            }

            // Iteration 7: Detect select() method calls on GroupStream (for line number tracking)
            if (insn instanceof MethodInsnNode methodCall && isGroupSelectMethod(methodCall)) {
                groupSelectLine = currentLine;  // Record line of select()
            }

            if (isTerminalOperation(insn, pendingLambdas, pendingJoinType, pendingGroupQuery, pendingGroupSelectKey)) {
                // Iteration 7: For group queries with select(), use the select() line, not terminal line
                int effectiveLine = (pendingGroupQuery && groupSelectLine > 0) ? groupSelectLine : currentLine;
                LambdaCallSite callSite = createCallSite(classNode, method, (MethodInsnNode) insn,
                                                          pendingLambdas, pendingAggregation, pendingJoinType,
                                                          pendingGroupQuery, effectiveLine, i);
                callSites.add(callSite);
                log.debugf("Found fluent API terminal operation: %s", callSite);
                // Temporary debug: log group call sites at info level
                if (pendingGroupQuery) {
                    log.infof("Detected GROUP call site: %s (groupSelect=%d, hasSelectKey=%b, usedLine=%d)",
                             callSite.getCallSiteId(),
                             callSite.groupSelectLambdas() != null ? callSite.groupSelectLambdas().size() : 0,
                             pendingGroupSelectKey, effectiveLine);
                }
                pendingLambdas.clear();
                pendingAggregation = null;
                pendingJoinType = null;  // Iteration 6: Reset join type
                pendingGroupQuery = false; // Iteration 7: Reset group query flag
                pendingGroupSelectKey = false; // Iteration 7: Reset selectKey flag
                groupSelectLine = -1;  // Iteration 7: Reset select line
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
                // Determine lambda spec type
                LambdaSpecType specType = determineLambdaSpecType(invokeDynamic);
                pendingLambdas.add(new PendingLambda(handle.getName(), handle.getDesc(), fluentMethod, specType));
                log.debugf("Detected lambda: method=%s, fluent=%s, specType=%s, desc=%s",
                          handle.getName(), fluentMethod, specType, invokeDynamic.desc);
            }
        }
    }

    /**
     * Determines the lambda spec type from the invokedynamic descriptor.
     * Iteration 7: Added GroupQuerySpec detection.
     */
    private LambdaSpecType determineLambdaSpecType(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        if (desc.contains(GROUP_QUERY_SPEC_DESCRIPTOR)) {
            return LambdaSpecType.GROUP_QUERY_SPEC;
        } else if (desc.contains(BI_QUERY_SPEC_DESCRIPTOR)) {
            return LambdaSpecType.BI_QUERY_SPEC;
        }
        return LambdaSpecType.QUERY_SPEC;
    }

    /**
     * Checks if instruction is a terminal operation.
     * Iteration 6: Also checks for JoinStream terminal calls when in join context.
     * Iteration 7: Also checks for GroupStream terminal calls when in group context.
     *              Also handles select()/selectKey() returning QusaqStream from GroupStream.
     */
    private boolean isTerminalOperation(AbstractInsnNode insn, List<PendingLambda> pendingLambdas,
                                         JoinType joinType, boolean isGroupQuery, boolean hasGroupSelectKey) {
        if (!(insn instanceof MethodInsnNode methodCall) || pendingLambdas.isEmpty()) {
            return false;
        }

        // Iteration 7: Check for GroupStream terminals when in group context
        if (isGroupQuery) {
            // Check GroupStream terminals (toList, count on GroupStream)
            if (isGroupStreamTerminalCall(methodCall)) {
                log.debugf("Group context: detected GroupStream terminal %s.%s", methodCall.owner, methodCall.name);
                return true;
            }
            // Also check QusaqStream terminals after select() or selectKey() in group context
            // When GroupStream.select() or selectKey() is called, it returns QusaqStream
            // So the terminal operation (toList, etc.) is on QusaqStream
            boolean hasSelectLambda = hasGroupSelectLambda(pendingLambdas);
            boolean isQusaqTerminal = isQusaqStreamTerminalCall(methodCall);
            log.debugf("Group context check: hasSelectLambda=%b, hasSelectKey=%b, isQusaqTerminal=%b, method=%s.%s",
                      hasSelectLambda, hasGroupSelectKey, isQusaqTerminal, methodCall.owner, methodCall.name);
            if ((hasSelectLambda || hasGroupSelectKey) && isQusaqTerminal) {
                log.debugf("Group context: detected QusaqStream terminal after select %s.%s", methodCall.owner, methodCall.name);
                return true;
            }
            return false;
        }

        // Iteration 6: Check for JoinStream terminals when in join context
        if (joinType != null) {
            return isJoinStreamTerminalCall(methodCall);
        }

        return isQusaqStreamTerminalCall(methodCall);
    }

    /**
     * Checks if pending lambdas include a select() call with GroupQuerySpec.
     * Iteration 7: Used to detect when GroupStream.select() returns QusaqStream.
     */
    private boolean hasGroupSelectLambda(List<PendingLambda> pendingLambdas) {
        for (PendingLambda lambda : pendingLambdas) {
            if (METHOD_SELECT.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if method call is selectKey() on GroupStream.
     * Iteration 7: selectKey() returns QusaqStream without a lambda argument.
     */
    private boolean isGroupSelectKeyMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT_KEY.equals(methodCall.name) &&
               GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is select() on GroupStream.
     * Iteration 7: select() with GroupQuerySpec returns QusaqStream.
     */
    private boolean isGroupSelectMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT.equals(methodCall.name) &&
               GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    private LambdaCallSite createCallSite(ClassNode classNode, MethodNode method, MethodInsnNode methodCall,
                                          List<PendingLambda> pendingLambdas, PendingAggregation pendingAggregation,
                                          JoinType pendingJoinType, boolean pendingGroupQuery,
                                          int lineNumber, int insnIndex) {
        // Phase 5: Pass aggregation method to extractLambdaInfo
        String aggregationMethod = pendingAggregation != null ? pendingAggregation.aggregationMethod : null;
        LambdaInfo info = extractLambdaInfo(pendingLambdas, methodCall.name, aggregationMethod, pendingGroupQuery);

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
            info.biEntityWhereLambdas,  // Iteration 6: BiQuerySpec WHERE lambdas
            info.isGroup,  // Iteration 7: Group query flag
            info.groupByLambdaMethod,  // Iteration 7: groupBy() lambda
            info.groupByLambdaDescriptor,  // Iteration 7: groupBy() descriptor
            info.havingLambdas,  // Iteration 7: having() lambdas
            info.groupSelectLambdas,  // Iteration 7: select() in group context
            info.groupSortLambdas  // Iteration 7: sortedBy() in group context
        );
    }

    private LambdaInfo extractLambdaInfo(List<PendingLambda> pendingLambdas, String terminalMethodName,
                                          String aggregationMethodName, boolean isGroupQuery) {
        List<LambdaPair> whereLambdas = new ArrayList<>();
        List<SortLambda> sortLambdas = new ArrayList<>();
        List<LambdaPair> biEntityWhereLambdas = new ArrayList<>();  // Iteration 6: BiQuerySpec WHERE lambdas
        // Iteration 7: Group-related lambdas
        List<LambdaPair> havingLambdas = new ArrayList<>();
        List<LambdaPair> groupSelectLambdas = new ArrayList<>();
        List<SortLambda> groupSortLambdas = new ArrayList<>();
        String groupByMethod = null;
        String groupByDescriptor = null;

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
            } else if (METHOD_GROUP_BY.equals(lambda.fluentMethod)) {
                // Iteration 7: groupBy() key extractor lambda
                groupByMethod = lambda.methodName;
                groupByDescriptor = lambda.descriptor;
            } else if (METHOD_HAVING.equals(lambda.fluentMethod)) {
                // Iteration 7: having() lambda (GroupQuerySpec)
                havingLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
            } else if (METHOD_SELECT.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                // Iteration 7: select() in group context (GroupQuerySpec)
                groupSelectLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
            } else if (METHOD_SORTED_BY.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                // Iteration 7: sortedBy() in group context (GroupQuerySpec)
                groupSortLambdas.add(new SortLambda(lambda.methodName, lambda.descriptor, SortDirection.ASCENDING));
            } else if (METHOD_SORTED_DESCENDING_BY.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                // Iteration 7: sortedDescendingBy() in group context (GroupQuerySpec)
                groupSortLambdas.add(new SortLambda(lambda.methodName, lambda.descriptor, SortDirection.DESCENDING));
            } else if (METHOD_JOIN.equals(lambda.fluentMethod) || METHOD_LEFT_JOIN.equals(lambda.fluentMethod)) {
                // Iteration 6: Join relationship lambda (e.g., p -> p.phones)
                joinRelationshipMethod = lambda.methodName;
                joinRelationshipDescriptor = lambda.descriptor;
            } else if (METHOD_WHERE.equals(lambda.fluentMethod)) {
                // Iteration 6: Check if this is a BiQuerySpec lambda (has two entity parameters)
                if (lambda.isBiEntity()) {
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
            biEntityWhereLambdas.isEmpty() ? null : biEntityWhereLambdas,
            isGroupQuery,  // Iteration 7: Group query flag
            groupByMethod,
            groupByDescriptor,
            havingLambdas.isEmpty() ? null : havingLambdas,
            groupSelectLambdas.isEmpty() ? null : groupSelectLambdas,
            groupSortLambdas.isEmpty() ? null : groupSortLambdas
        );
    }

    /**
     * Looks forward from invokedynamic to find the fluent method call (where/select/join/groupBy).
     * Iteration 6: Also recognizes join methods.
     * Iteration 7: Also recognizes group methods.
     */
    private String findFluentMethodForward(InsnList instructions, int startIndex) {
        // Look ahead a few instructions to find where/select/join/groupBy call
        for (int j = startIndex + 1; j < Math.min(startIndex + 5, instructions.size()); j++) {
            AbstractInsnNode nextInsn = instructions.get(j);
            if (nextInsn instanceof MethodInsnNode methodCall) {
                String methodName = methodCall.name;
                if (FLUENT_ENTRY_POINT_METHODS.contains(methodName) ||
                    FLUENT_INTERMEDIATE_METHODS.contains(methodName) ||
                    JOIN_METHODS.contains(methodName) ||
                    GROUP_METHODS.contains(methodName)) {
                    return methodName;
                }
            }
        }
        return null;
    }

    /**
     * Checks if invokedynamic creates a QuerySpec, BiQuerySpec, or GroupQuerySpec lambda.
     * Iteration 6: Also detects BiQuerySpec for join query lambdas.
     * Iteration 7: Also detects GroupQuerySpec for group query lambdas.
     */
    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR) ||
               desc.contains(BI_QUERY_SPEC_DESCRIPTOR) ||
               desc.contains(GROUP_QUERY_SPEC_DESCRIPTOR);
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
     * Checks if method call is a terminal operation on GroupStream.
     * Iteration 7: GroupStream has the same terminal methods as QusaqStream.
     */
    private boolean isGroupStreamTerminalCall(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a terminal method name
        if (!FLUENT_TERMINAL_METHODS.contains(name)) {
            return false;
        }

        // Check if owner is GroupStream interface
        return GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is a groupBy entry point.
     * Iteration 7: Detects groupBy calls on QusaqEntity, QusaqRepository, or QusaqStream.
     */
    private boolean isGroupMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's the groupBy method name
        if (!METHOD_GROUP_BY.equals(name)) {
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

        // Accept if called on QusaqStream (instance method)
        if (QUSAQ_STREAM_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if return type is GroupStream (covers virtual calls)
        String desc = methodCall.desc;
        return desc.contains("Lio/quarkus/qusaq/runtime/GroupStream;");
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
