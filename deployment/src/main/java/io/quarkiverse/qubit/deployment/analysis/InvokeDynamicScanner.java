package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.runtime.SortDirection;
import io.quarkus.logging.Log;
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

import static io.quarkiverse.qubit.runtime.QubitConstants.*;

/**
 * Scans bytecode for invokedynamic instructions creating QuerySpec lambdas.
 * Detects fluent API terminal operations.
 * <p>
 * Enhanced to support join queries with BiQuerySpec lambdas.
 * Detects join()/leftJoin() entry points and JoinStream terminal operations.
 * <p>
 * Enhanced to support group queries with GroupQuerySpec lambdas.
 * Detects groupBy() entry points and GroupStream terminal operations.
 */
public class InvokeDynamicScanner {

    /**
     * Join type for join queries.
     * Tracks whether a join is INNER or LEFT OUTER.
     */
    public enum JoinType {
        /** Standard inner join - excludes source entities without matching joined entities. */
        INNER,
        /** Left outer join - includes all source entities even without matching joined entities. */
        LEFT
    }

    /**
     * Context type for tracking query context during scanning.
     * Tracks whether we're in a group query context.
     */
    public enum QueryContext {
        /** Standard query (no special context). */
        STANDARD,
        /** Join query context (after join() or leftJoin()). */
        JOIN,
        /** Group query context (after groupBy()). */
        GROUP
    }

    /**
     * Pair of lambda method name and descriptor.
     */
    public record LambdaPair(String methodName, String descriptor) {}

    /**
     * Sort lambda with direction (ascending/descending).
     * Used to track sortedBy() and sortedDescendingBy() operations.
     */
    public record SortLambda(String methodName, String descriptor, SortDirection direction) {}

    /**
     * Discovered lambda call site for fluent API terminal operations.
     * Enhanced to track both predicate and projection lambdas for combined queries.
     * Enhanced to support multiple where() predicates.
     * Enhanced to support sorting (sortedBy/sortedDescendingBy).
     * Enhanced to support aggregations (min/max/avg/sum*).
     * Enhanced to support join queries with BiQuerySpec lambdas.
     * Enhanced to support join projections with BiQuerySpec select().
     * Enhanced to support group queries with GroupQuerySpec lambdas.
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
            List<LambdaPair> predicateLambdas,     // ALL WHERE lambdas for multiple where() support
            List<SortLambda> sortLambdas,          // ALL sort lambdas with direction
            String aggregationLambdaMethodName,    // Aggregation mapper lambda (e.g., p -> p.salary for min/max/avg/sum)
            String aggregationLambdaMethodDescriptor, // Aggregation mapper descriptor
            JoinType joinType,                     // Join type (INNER/LEFT) or null if not a join query
            String joinRelationshipLambdaMethodName,    // Join relationship lambda (e.g., p -> p.phones)
            String joinRelationshipLambdaDescriptor,    // Join relationship lambda descriptor
            List<LambdaPair> biEntityPredicateLambdas,  // BiQuerySpec WHERE lambdas for join queries
            boolean isSelectJoined,                // True if selectJoined() was called
            String biEntityProjectionLambdaMethodName,  // BiQuerySpec SELECT lambda for join projections
            String biEntityProjectionLambdaDescriptor,  // BiQuerySpec SELECT lambda descriptor
            boolean isGroupQuery,                  // True if this is a GROUP BY query
            String groupByLambdaMethodName,        // groupBy() lambda (e.g., p -> p.department)
            String groupByLambdaDescriptor,        // groupBy() lambda descriptor
            List<LambdaPair> havingLambdas,        // having() lambdas (GroupQuerySpec)
            List<LambdaPair> groupSelectLambdas,   // select() lambdas in group context (GroupQuerySpec)
            List<SortLambda> groupSortLambdas) {   // sortedBy() lambdas in group context (GroupQuerySpec)

        /**
         * Returns true if this is a count query.
         * Both count() and exists() are count queries since exists() delegates to count().
         */
        public boolean isCountQuery() {
            return METHOD_COUNT.equals(targetMethodName) || METHOD_EXISTS.equals(targetMethodName);
        }

        /**
         * Returns true if this is an aggregation query (min, max, avg, sum*).
         * Aggregation terminals require mapper lambda analysis.
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
         * Join queries have a non-null join type.
         */
        public boolean isJoinQuery() {
            return joinType != null;
        }

        /**
         * Returns true if this is a selectJoined() query.
         * selectJoined() returns joined entities instead of source entities.
         */
        public boolean isSelectJoinedQuery() {
            return isSelectJoined;
        }

        /**
         * Returns true if this is a join projection query.
         * join().select((p, ph) -> ...) uses BiQuerySpec to project entities.
         */
        public boolean isJoinProjectionQuery() {
            return joinType != null && biEntityProjectionLambdaMethodName != null;
        }

        /**
         * Returns true if this is a GROUP BY query.
         * Group queries have isGroupQuery flag set.
         */
        public boolean isGroupByQuery() {
            return isGroupQuery;
        }

        /**
         * Returns true if this is a projection query (select).
         * Checks if projection lambda is present.
         * Excludes sorting methods from being treated as projections.
         */
        public boolean isProjectionQuery() {
            // Check if projection lambda is present
            if (projectionLambdaMethodName != null) {
                return true;
            }

            // Backward compatibility: Check fluent method name
            if (METHOD_SELECT.equals(fluentMethodName)) {
                return true;
            }

            if (METHOD_WHERE.equals(fluentMethodName)) {
                return false;
            }

            // Sorting methods are not projections
            if (METHOD_SORTED_BY.equals(fluentMethodName) || METHOD_SORTED_DESCENDING_BY.equals(fluentMethodName)) {
                return false;
            }

            // Aggregation methods are not projections - they are aggregations
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

            Log.warnf("Treating as projection (non-boolean): descriptor=%s, fluent=%s", lambdaMethodDescriptor, fluentMethodName);
            return true;
        }

        /**
         * Returns true if this call site has both a where() predicate and a select() projection.
         * This indicates a combined query.
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
            Log.warnf(e, "Failed to scan class %s for lambda call sites", className);
        }

        return callSites;
    }

    /**
     * Lambda spec type for tracking lambda kind during scanning.
     * Added GROUP for GroupQuerySpec lambdas.
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
     * Added isBiEntity to track BiQuerySpec vs QuerySpec lambdas.
     * Enhanced with LambdaSpecType to support GroupQuerySpec.
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
     * Used to detect aggregation methods that are now intermediate operations.
     */
    private record PendingAggregation(String aggregationMethod) {}

    /**
     * Grouped lambda information for building call sites.
     * Added aggregation lambda fields.
     * Added join-related fields.
     * Added bi-entity projection fields for join select().
     * Added group-related fields.
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
        String aggregationLambdaMethod,      // Aggregation mapper lambda
        String aggregationLambdaDescriptor,  // Aggregation mapper descriptor
        String joinRelationshipLambdaMethod,   // Join relationship lambda
        String joinRelationshipLambdaDescriptor, // Join relationship descriptor
        List<LambdaPair> biEntityWhereLambdas, // BiQuerySpec WHERE lambdas
        String biEntityProjectionLambdaMethod,   // BiQuerySpec SELECT lambda for join projections
        String biEntityProjectionLambdaDescriptor, // BiQuerySpec SELECT lambda descriptor
        boolean isGroup,                       // True if this is a group query
        String groupByLambdaMethod,            // groupBy() key extractor lambda
        String groupByLambdaDescriptor,        // groupBy() lambda descriptor
        List<LambdaPair> havingLambdas,        // having() lambdas
        List<LambdaPair> groupSelectLambdas,   // select() on GroupStream
        List<SortLambda> groupSortLambdas      //sortedBy() on GroupStream
    ) {}

    private void scanMethod(ClassNode classNode, MethodNode method, List<LambdaCallSite> callSites) {
        InsnList instructions = method.instructions;
        List<PendingLambda> pendingLambdas = new ArrayList<>();
        PendingAggregation pendingAggregation = null;
        JoinType pendingJoinType = null;  //Track join type
        boolean pendingJoinSelectJoined = false; //Track selectJoined() on JoinStream
        boolean pendingJoinSelect = false; //Track select() with BiQuerySpec on JoinStream
        boolean pendingGroupQuery = false; //Track group query context
        boolean pendingGroupSelectKey = false; //Track selectKey() on GroupStream
        int currentLine = -1;
        int groupSelectLine = -1;  //Track line of select() on GroupStream
        int joinSelectJoinedLine = -1; //Track line of selectJoined() on JoinStream
        int joinSelectLine = -1; //Track line of select() on JoinStream

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            currentLine = updateLineNumber(insn, currentLine);
            detectAndAddLambda(insn, instructions, i, pendingLambdas);

            // Detect aggregation method calls (now intermediate operations)
            if (insn instanceof MethodInsnNode methodCall && isAggregationMethod(methodCall)) {
                pendingAggregation = new PendingAggregation(methodCall.name);
            }

            // Detect join method calls
            if (insn instanceof MethodInsnNode methodCall && isJoinMethod(methodCall)) {
                pendingJoinType = METHOD_LEFT_JOIN.equals(methodCall.name) ? JoinType.LEFT : JoinType.INNER;
            }

            // Detect selectJoined() method calls on JoinStream
            if (insn instanceof MethodInsnNode methodCall && isJoinSelectJoinedMethod(methodCall)) {
                pendingJoinSelectJoined = true;
                joinSelectJoinedLine = currentLine;  // Record line of selectJoined()
            }

            // Detect select() method calls on JoinStream (for line number tracking)
            if (insn instanceof MethodInsnNode methodCall && isJoinSelectMethod(methodCall)) {
                pendingJoinSelect = true;
                joinSelectLine = currentLine;  // Record line of select()
                Log.infof("Join context: detected JoinStream.select() at line %d", currentLine);
            }

            // Detect groupBy method calls
            if (insn instanceof MethodInsnNode methodCall && isGroupMethod(methodCall)) {
                pendingGroupQuery = true;
            }

            // Detect selectKey() method calls on GroupStream
            if (insn instanceof MethodInsnNode methodCall && isGroupSelectKeyMethod(methodCall)) {
                pendingGroupSelectKey = true;
                groupSelectLine = currentLine;  // Record line of selectKey()
            }

            // Detect select() method calls on GroupStream (for line number tracking)
            if (insn instanceof MethodInsnNode methodCall && isGroupSelectMethod(methodCall)) {
                groupSelectLine = currentLine;  // Record line of select()
            }

            if (isTerminalOperation(insn, pendingLambdas, pendingJoinType, pendingJoinSelectJoined, pendingJoinSelect, pendingGroupQuery, pendingGroupSelectKey)) {
                // For group queries with select(), use the select() line, not terminal line
                // For join queries with selectJoined(), use the selectJoined() line
                // For join queries with select(), use the select() line
                int effectiveLine = currentLine;
                if (pendingGroupQuery && groupSelectLine > 0) {
                    effectiveLine = groupSelectLine;
                } else if (pendingJoinSelectJoined && joinSelectJoinedLine > 0) {
                    effectiveLine = joinSelectJoinedLine;
                } else if (pendingJoinSelect && joinSelectLine > 0) {
                    effectiveLine = joinSelectLine;
                }
                LambdaCallSite callSite = createCallSite(classNode, method, (MethodInsnNode) insn,
                                                          pendingLambdas, pendingAggregation, pendingJoinType,
                                                          pendingJoinSelectJoined, pendingGroupQuery, effectiveLine, i);
                callSites.add(callSite);
                Log.debugf("Found fluent API terminal operation: %s", callSite);
                // Temporary debug: log group call sites at info level
                if (pendingGroupQuery) {
                    Log.infof("Detected GROUP call site: %s (groupSelect=%d, hasSelectKey=%b, usedLine=%d)",
                             callSite.getCallSiteId(),
                             callSite.groupSelectLambdas() != null ? callSite.groupSelectLambdas().size() : 0,
                             pendingGroupSelectKey, effectiveLine);
                }
                pendingLambdas.clear();
                pendingAggregation = null;
                pendingJoinType = null;  //Reset join type
                pendingJoinSelectJoined = false; //Reset selectJoined flag
                joinSelectJoinedLine = -1; //Reset selectJoined line
                pendingJoinSelect = false; //Reset select flag
                joinSelectLine = -1; //Reset select line
                pendingGroupQuery = false; //Reset group query flag
                pendingGroupSelectKey = false; //Reset selectKey flag
                groupSelectLine = -1;  //Reset select line
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
                Log.debugf("Detected lambda: method=%s, fluent=%s, specType=%s, desc=%s",
                          handle.getName(), fluentMethod, specType, invokeDynamic.desc);
            }
        }
    }

    /**
     * Determines the lambda spec type from the invokedynamic descriptor.
     * Added GroupQuerySpec detection.
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
     * Also checks for JoinStream terminal calls when in join context.
     * Also handles selectJoined() returning QubitStream from JoinStream.
     * Also handles select() with BiQuerySpec returning QubitStream from JoinStream.
     * Also checks for GroupStream terminal calls when in group context.
     *              Also handles select()/selectKey() returning QubitStream from GroupStream.
     */
    private boolean isTerminalOperation(AbstractInsnNode insn, List<PendingLambda> pendingLambdas,
                                         JoinType joinType, boolean hasJoinSelectJoined, boolean hasJoinSelect,
                                         boolean isGroupQuery, boolean hasGroupSelectKey) {
        if (!(insn instanceof MethodInsnNode methodCall) || pendingLambdas.isEmpty()) {
            return false;
        }

        // Check for GroupStream terminals when in group context
        if (isGroupQuery) {
            // Check GroupStream terminals (toList, count on GroupStream)
            if (isGroupStreamTerminalCall(methodCall)) {
                Log.debugf("Group context: detected GroupStream terminal %s.%s", methodCall.owner, methodCall.name);
                return true;
            }
            // Also check QubitStream terminals after select() or selectKey() in group context
            // When GroupStream.select() or selectKey() is called, it returns QubitStream
            // So the terminal operation (toList, etc.) is on QubitStream
            boolean hasSelectLambda = hasGroupSelectLambda(pendingLambdas);
            boolean isQubitTerminal = isQubitStreamTerminalCall(methodCall);
            Log.debugf("Group context check: hasSelectLambda=%b, hasSelectKey=%b, isQubitTerminal=%b, method=%s.%s",
                      hasSelectLambda, hasGroupSelectKey, isQubitTerminal, methodCall.owner, methodCall.name);
            if ((hasSelectLambda || hasGroupSelectKey) && isQubitTerminal) {
                Log.debugf("Group context: detected QubitStream terminal after select %s.%s", methodCall.owner, methodCall.name);
                return true;
            }
            return false;
        }

        // Check for JoinStream terminals when in join context
        if (joinType != null) {
            // If selectJoined() was called, look for QubitStream terminals
            if (hasJoinSelectJoined) {
                return isQubitStreamTerminalCall(methodCall);
            }
            // If select() with BiQuerySpec was called, look for QubitStream terminals
            if (hasJoinSelect || hasJoinSelectLambda(pendingLambdas)) {
                Log.infof("Join context: hasJoinSelect=%b, hasJoinSelectLambda=%b, checking terminal %s.%s",
                         hasJoinSelect, hasJoinSelectLambda(pendingLambdas), methodCall.owner, methodCall.name);
                return isQubitStreamTerminalCall(methodCall);
            }
            return isJoinStreamTerminalCall(methodCall);
        }

        return isQubitStreamTerminalCall(methodCall);
    }

    /**
     * Checks if pending lambdas include a select() call with GroupQuerySpec.
     * Used to detect when GroupStream.select() returns QubitStream.
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
     * Checks if pending lambdas include a select() call with BiQuerySpec.
     * Used to detect when JoinStream.select() returns QubitStream.
     */
    private boolean hasJoinSelectLambda(List<PendingLambda> pendingLambdas) {
        for (PendingLambda lambda : pendingLambdas) {
            if (METHOD_SELECT.equals(lambda.fluentMethod) && lambda.isBiEntity()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if method call is selectJoined() on JoinStream.
     * selectJoined() returns QubitStream containing joined entities.
     */
    private boolean isJoinSelectJoinedMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT_JOINED.equals(methodCall.name) &&
               JOIN_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is select() on JoinStream.
     * select() with BiQuerySpec returns QubitStream.
     */
    private boolean isJoinSelectMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT.equals(methodCall.name) &&
               JOIN_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is selectKey() on GroupStream.
     * selectKey() returns QubitStream without a lambda argument.
     */
    private boolean isGroupSelectKeyMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT_KEY.equals(methodCall.name) &&
               GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is select() on GroupStream.
     * select() with GroupQuerySpec returns QubitStream.
     */
    private boolean isGroupSelectMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT.equals(methodCall.name) &&
               GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    private LambdaCallSite createCallSite(ClassNode classNode, MethodNode method, MethodInsnNode methodCall,
                                          List<PendingLambda> pendingLambdas, PendingAggregation pendingAggregation,
                                          JoinType pendingJoinType, boolean pendingJoinSelectJoined,
                                          boolean pendingGroupQuery, int lineNumber, int insnIndex) {
        // Pass aggregation method to extractLambdaInfo
        String aggregationMethod = pendingAggregation != null ? pendingAggregation.aggregationMethod : null;
        LambdaInfo info = extractLambdaInfo(pendingLambdas, methodCall.name, aggregationMethod, pendingGroupQuery);

        // For aggregation queries, use aggregation method as target instead of getSingleResult
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
            pendingJoinType,  //Join type
            info.joinRelationshipLambdaMethod,  //Join relationship lambda
            info.joinRelationshipLambdaDescriptor,  //Join relationship descriptor
            info.biEntityWhereLambdas,  //BiQuerySpec WHERE lambdas
            pendingJoinSelectJoined,  //selectJoined() flag
            info.biEntityProjectionLambdaMethod,  //BiQuerySpec SELECT lambda
            info.biEntityProjectionLambdaDescriptor,  //BiQuerySpec SELECT descriptor
            info.isGroup,  //Group query flag
            info.groupByLambdaMethod,  //groupBy() lambda
            info.groupByLambdaDescriptor,  //groupBy() descriptor
            info.havingLambdas,  //having() lambdas
            info.groupSelectLambdas,  //select() in group context
            info.groupSortLambdas  //sortedBy() in group context
        );
    }

    private LambdaInfo extractLambdaInfo(List<PendingLambda> pendingLambdas, String terminalMethodName,
                                          String aggregationMethodName, boolean isGroupQuery) {
        List<LambdaPair> whereLambdas = new ArrayList<>();
        List<SortLambda> sortLambdas = new ArrayList<>();
        List<LambdaPair> biEntityWhereLambdas = new ArrayList<>();  //BiQuerySpec WHERE lambdas
        // Group-related lambdas
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
        String joinRelationshipMethod = null;  //Join relationship lambda
        String joinRelationshipDescriptor = null;
        String biEntityProjectionMethod = null;  //BiQuerySpec SELECT lambda
        String biEntityProjectionDescriptor = null;

        // Use provided aggregation method name (detected from intermediate operation)
        boolean isAggregation = aggregationMethodName != null;

        // If aggregation, the last lambda is the mapper (e.g., p -> p.salary)
        // All others are treated as WHERE predicates
        for (int i = 0; i < pendingLambdas.size(); i++) {
            PendingLambda lambda = pendingLambdas.get(i);
            boolean isLastLambda = (i == pendingLambdas.size() - 1);

            // For aggregations, last lambda is the mapper
            if (isAggregation && isLastLambda) {
                aggregationMethod = lambda.methodName;
                aggregationDescriptor = lambda.descriptor;
            } else if (METHOD_GROUP_BY.equals(lambda.fluentMethod)) {
                // groupBy() key extractor lambda
                groupByMethod = lambda.methodName;
                groupByDescriptor = lambda.descriptor;
            } else if (METHOD_HAVING.equals(lambda.fluentMethod)) {
                // having() lambda (GroupQuerySpec)
                havingLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
            } else if (METHOD_SELECT.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                // select() in group context (GroupQuerySpec)
                groupSelectLambdas.add(new LambdaPair(lambda.methodName, lambda.descriptor));
            } else if (METHOD_SORTED_BY.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                // sortedBy() in group context (GroupQuerySpec)
                groupSortLambdas.add(new SortLambda(lambda.methodName, lambda.descriptor, SortDirection.ASCENDING));
            } else if (METHOD_SORTED_DESCENDING_BY.equals(lambda.fluentMethod) && lambda.isGroupSpec()) {
                // sortedDescendingBy() in group context (GroupQuerySpec)
                groupSortLambdas.add(new SortLambda(lambda.methodName, lambda.descriptor, SortDirection.DESCENDING));
            } else if (METHOD_JOIN.equals(lambda.fluentMethod) || METHOD_LEFT_JOIN.equals(lambda.fluentMethod)) {
                // Join relationship lambda (e.g., p -> p.phones)
                joinRelationshipMethod = lambda.methodName;
                joinRelationshipDescriptor = lambda.descriptor;
            } else if (METHOD_WHERE.equals(lambda.fluentMethod)) {
                // Check if this is a BiQuerySpec lambda (has two entity parameters)
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
                // Check if this is a BiQuerySpec select (for join projections)
                if (lambda.isBiEntity()) {
                    biEntityProjectionMethod = lambda.methodName;
                    biEntityProjectionDescriptor = lambda.descriptor;
                } else {
                    selectMethod = lambda.methodName;
                    selectDescriptor = lambda.descriptor;
                }
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
            biEntityProjectionMethod,  //BiQuerySpec SELECT lambda
            biEntityProjectionDescriptor,  //BiQuerySpec SELECT descriptor
            isGroupQuery,  //Group query flag
            groupByMethod,
            groupByDescriptor,
            havingLambdas.isEmpty() ? null : havingLambdas,
            groupSelectLambdas.isEmpty() ? null : groupSelectLambdas,
            groupSortLambdas.isEmpty() ? null : groupSortLambdas
        );
    }

    /**
     * Looks forward from invokedynamic to find the fluent method call (where/select/join/groupBy).
     * Also recognizes join methods.
     * Also recognizes group methods.
     *
     * @param instructions the instruction list
     * @param startIndex the starting index to look forward from
     * @return the fluent method name, or null if not found within lookahead
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
     * Also detects BiQuerySpec for join query lambdas.
     * Also detects GroupQuerySpec for group query lambdas.
     */
    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR) ||
               desc.contains(BI_QUERY_SPEC_DESCRIPTOR) ||
               desc.contains(GROUP_QUERY_SPEC_DESCRIPTOR);
    }

    /**
     * Extracts the lambda method handle from an invokedynamic instruction.
     *
     * @param invokeDynamic the invokedynamic instruction
     * @return the lambda method handle, or null if not found
     */
    private Handle extractLambdaHandle(InvokeDynamicInsnNode invokeDynamic) {
        Object[] bsmArgs = invokeDynamic.bsmArgs;

        if (bsmArgs != null && bsmArgs.length >= 2 && bsmArgs[1] instanceof Handle handle) {
            return handle;
        }

        return null;
    }

    /**
     * Checks if method call is a terminal operation on QubitStream.
     */
    private boolean isQubitStreamTerminalCall(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a terminal method name
        if (!FLUENT_TERMINAL_METHODS.contains(name)) {
            return false;
        }

        // Check if owner is QubitStream interface
        return QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /**
     * Checks if method call is a terminal operation on JoinStream.
     * JoinStream has the same terminal methods as QubitStream.
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
     * GroupStream has the same terminal methods as QubitStream.
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
     * Detects groupBy calls on QubitEntity, QubitRepository, or QubitStream.
     */
    private boolean isGroupMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's the groupBy method name
        if (!METHOD_GROUP_BY.equals(name)) {
            return false;
        }

        // Accept if called on QubitEntity (static method)
        if (QUBIT_ENTITY_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if called on QubitRepository (instance method)
        if (QUBIT_REPOSITORY_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if called on QubitStream (instance method)
        if (QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if return type is GroupStream (covers virtual calls)
        String desc = methodCall.desc;
        return desc.contains("Lio/quarkiverse/qubit/runtime/GroupStream;");
    }

    /**
     * Checks if method call is a join entry point (join or leftJoin).
     * Detects join/leftJoin calls on QubitEntity, QubitRepository, or QubitStream.
     */
    private boolean isJoinMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a join method name
        if (!METHOD_JOIN.equals(name) && !METHOD_LEFT_JOIN.equals(name)) {
            return false;
        }

        // Accept if called on QubitEntity (static method)
        if (QUBIT_ENTITY_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if called on QubitRepository (instance method)
        if (QUBIT_REPOSITORY_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Accept if return type is JoinStream (covers virtual calls)
        String desc = methodCall.desc;
        return desc.contains("Lio/quarkiverse/qubit/runtime/JoinStream;");
    }

    /**
     * Checks if method call is an aggregation method.
     * Aggregation methods are now intermediate operations.
     * Detects both:
     * - Instance calls on QubitStream: stream.min(...)
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

        // Accept if owner is QubitStream interface (instance method call)
        if (QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Also accept if it's a static method call (invokestatic) with QubitStream return type
        // This handles direct static calls like Person.min(...)
        if (methodCall.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC) {
            // Check if return type is QubitStream
            String desc = methodCall.desc;
            return desc.contains("Lio/quarkiverse/qubit/runtime/QubitStream;");
        }

        // Also accept instance method calls on QubitRepository that return QubitStream
        // This handles repository.min(...) calls
        if (methodCall.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL ||
            methodCall.getOpcode() == org.objectweb.asm.Opcodes.INVOKEINTERFACE) {
            String desc = methodCall.desc;
            return desc.contains("Lio/quarkiverse/qubit/runtime/QubitStream;");
        }

        return false;
    }

}
