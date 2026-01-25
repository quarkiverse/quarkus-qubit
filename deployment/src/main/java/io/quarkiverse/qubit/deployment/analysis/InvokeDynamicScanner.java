package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.SortDirection;
import io.quarkus.logging.Log;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnsBooleanType;
import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnTypeContains;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

/**
 * Scans bytecode for invokedynamic instructions creating QuerySpec lambdas.
 * Supports standard, join (BiQuerySpec), and group (GroupQuerySpec) queries.
 */
public class InvokeDynamicScanner {

    /** Join type: INNER or LEFT OUTER. */
    public enum JoinType {
        /** Standard inner join - excludes source entities without matching joined entities. */
        INNER,
        /** Left outer join - includes all source entities even without matching joined entities. */
        LEFT
    }

    /** Query context type: STANDARD, JOIN, or GROUP. */
    public enum QueryContext {
        /** Standard query (no special context). */
        STANDARD,
        /** Join query context (after join() or leftJoin()). */
        JOIN,
        /** Group query context (after groupBy()). */
        GROUP
    }

    /** Pair of lambda method name and descriptor. */
    public record LambdaPair(String methodName, String descriptor) {}

    /** Sort lambda with direction (ascending/descending). */
    public record SortLambda(String methodName, String descriptor, SortDirection direction) {}

    /** Discovered lambda call site with predicates, projections, sorting, joins, and groups. */
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
            List<SortLambda> groupSortLambdas,     // sortedBy() lambdas in group context (GroupQuerySpec)
            boolean isGroupSelectKey,              // True if selectKey() was called on GroupStream
            boolean hasDistinct,                   // True if distinct() was called
            Integer skipValue,                     // Value passed to skip(), null if not called
            Integer limitValue) {                  // Value passed to limit(), null if not called

        /** Returns true if this is a count query (count() or exists()). */
        public boolean isCountQuery() {
            return METHOD_COUNT.equals(targetMethodName) || METHOD_EXISTS.equals(targetMethodName);
        }

        /** Returns true if this is an aggregation query (min, max, avg, sum*). */
        public boolean isAggregationQuery() {
            return AGGREGATION_METHOD_NAMES.contains(targetMethodName);
        }

        /** Returns true if this is a join query. */
        public boolean isJoinQuery() {
            return joinType != null;
        }

        /** Returns true if selectJoined() was called. */
        public boolean isSelectJoinedQuery() {
            return isSelectJoined;
        }

        /** Returns true if this is a join projection query with BiQuerySpec. */
        public boolean isJoinProjectionQuery() {
            return joinType != null && biEntityProjectionLambdaMethodName != null;
        }

        /** Returns true if this is a GROUP BY query. */
        public boolean isGroupByQuery() {
            return isGroupQuery;
        }

        /** Returns true if this is a projection query (select). */
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
            if (AGGREGATION_METHOD_NAMES.contains(fluentMethodName)) {
                return false;
            }

            // Fallback: Use descriptor heuristic
            if (returnsBooleanType(lambdaMethodDescriptor)) {
                return false;
            }

            Log.warnf("Treating as projection (non-boolean): descriptor=%s, fluent=%s", lambdaMethodDescriptor, fluentMethodName);
            return true;
        }

        /** Returns true if this is a combined WHERE + SELECT query. */
        public boolean isCombinedQuery() {
            return (predicateLambdas != null && !predicateLambdas.isEmpty()) && projectionLambdaMethodName != null;
        }

        /** Returns unique identifier: ownerClassName:methodName:lineNumber:lambdaMethodName. */
        public String getCallSiteId() {
            // Use the primary lambda method name as discriminator
            // Priority: predicate > groupBy > join relationship > projection > aggregation
            String primaryLambda = getPrimaryLambdaMethodName();
            return ownerClassName + ":" + methodName + ":" + lineNumber + ":" + primaryLambda;
        }

        /** Returns primary lambda method name (predicate > groupBy > join > projection > sort). */
        public String getPrimaryLambdaMethodName() {
            // Single-entity predicates first (QubitStream.where())
            if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
                return predicateLambdas.get(0).methodName();
            }
            // Bi-entity predicates for join queries (JoinStream.where(BiQuerySpec))
            if (biEntityPredicateLambdas != null && !biEntityPredicateLambdas.isEmpty()) {
                return biEntityPredicateLambdas.get(0).methodName();
            }
            if (groupByLambdaMethodName != null) {
                return groupByLambdaMethodName;
            }
            if (joinRelationshipLambdaMethodName != null) {
                return joinRelationshipLambdaMethodName;
            }
            if (projectionLambdaMethodName != null) {
                return projectionLambdaMethodName;
            }
            if (aggregationLambdaMethodName != null) {
                return aggregationLambdaMethodName;
            }
            // Sort lambdas (sortedBy/sortedDescendingBy)
            if (sortLambdas != null && !sortLambdas.isEmpty()) {
                return sortLambdas.get(0).methodName();
            }
            if (lambdaMethodName != null) {
                return lambdaMethodName;
            }
            // Fallback to terminal instruction index for queries without explicit lambdas
            return String.valueOf(terminalInsnIndex);
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

    /** Scans class bytecode for QuerySpec lambda call sites. */
    public List<LambdaCallSite> scanClass(byte[] classBytes, String className) {
        List<LambdaCallSite> callSites = new ArrayList<>();

        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            // Skip frames (not needed for scanning), but keep debug info for line numbers
            reader.accept(classNode, ClassReader.SKIP_FRAMES);

            for (MethodNode method : classNode.methods) {
                scanMethod(classNode, method, callSites);
            }
        } catch (Exception e) {
            Log.warnf(e, "Failed to scan class %s for lambda call sites", className);
        }

        return callSites;
    }

    /** Lambda spec type: QUERY_SPEC, BI_QUERY_SPEC, or GROUP_QUERY_SPEC. */
    private enum LambdaSpecType {
        /** Standard QuerySpec lambda. */
        QUERY_SPEC,
        /** BiQuerySpec lambda (for join queries). */
        BI_QUERY_SPEC,
        /** GroupQuerySpec lambda (for group queries). */
        GROUP_QUERY_SPEC
    }

    /** Tracked lambda with spec type (QuerySpec, BiQuerySpec, GroupQuerySpec). */
    private record PendingLambda(String methodName, String descriptor, String fluentMethod, LambdaSpecType specType) {
        boolean isBiEntity() {
            return specType == LambdaSpecType.BI_QUERY_SPEC;
        }

        boolean isGroupSpec() {
            return specType == LambdaSpecType.GROUP_QUERY_SPEC;
        }
    }

    /** Tracked aggregation method (min, max, avg, sum). */
    private record PendingAggregation(String aggregationMethod) {}

    /** Grouped lambda info with aggregation, join, and group fields. */
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
        boolean pendingDistinct = false; //Track distinct() on QubitStream or JoinStream
        Integer pendingSkipValue = null; //Track skip() value
        Integer pendingLimitValue = null; //Track limit() value
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

            // Detect distinct() method calls on QubitStream or JoinStream
            if (insn instanceof MethodInsnNode methodCall && isDistinctMethod(methodCall)) {
                pendingDistinct = true;
            }

            // Detect skip() method calls and extract integer value
            if (insn instanceof MethodInsnNode methodCall && isSkipMethod(methodCall)) {
                pendingSkipValue = extractIntegerArgument(instructions, i);
            }

            // Detect limit() method calls and extract integer value
            if (insn instanceof MethodInsnNode methodCall && isLimitMethod(methodCall)) {
                pendingLimitValue = extractIntegerArgument(instructions, i);
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
                                                          pendingJoinSelectJoined, pendingGroupQuery, pendingGroupSelectKey,
                                                          pendingDistinct, pendingSkipValue, pendingLimitValue,
                                                          effectiveLine, i);
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
                pendingDistinct = false; //Reset distinct flag
                pendingSkipValue = null; //Reset skip value
                pendingLimitValue = null; //Reset limit value
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

    /** Determines lambda spec type from invokedynamic descriptor. */
    private LambdaSpecType determineLambdaSpecType(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        if (desc.contains(GROUP_QUERY_SPEC_DESCRIPTOR)) {
            return LambdaSpecType.GROUP_QUERY_SPEC;
        } else if (desc.contains(BI_QUERY_SPEC_DESCRIPTOR)) {
            return LambdaSpecType.BI_QUERY_SPEC;
        }
        return LambdaSpecType.QUERY_SPEC;
    }

    /** Checks if instruction is a terminal operation on QubitStream/JoinStream/GroupStream. */
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

    /** Checks if pending lambdas include a select() with the specified spec type. */
    private boolean hasSelectLambdaOfType(List<PendingLambda> pendingLambdas, LambdaSpecType specType) {
        for (PendingLambda lambda : pendingLambdas) {
            if (METHOD_SELECT.equals(lambda.fluentMethod) && lambda.specType == specType) {
                return true;
            }
        }
        return false;
    }

    /** Checks if pending lambdas include a select() call with GroupQuerySpec. */
    private boolean hasGroupSelectLambda(List<PendingLambda> pendingLambdas) {
        return hasSelectLambdaOfType(pendingLambdas, LambdaSpecType.GROUP_QUERY_SPEC);
    }

    /** Checks if pending lambdas include a select() call with BiQuerySpec. */
    private boolean hasJoinSelectLambda(List<PendingLambda> pendingLambdas) {
        return hasSelectLambdaOfType(pendingLambdas, LambdaSpecType.BI_QUERY_SPEC);
    }

    /** Checks if method call is selectJoined() on JoinStream. */
    private boolean isJoinSelectJoinedMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT_JOINED.equals(methodCall.name) &&
               JOIN_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /** Checks if method call is select() on JoinStream. */
    private boolean isJoinSelectMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT.equals(methodCall.name) &&
               JOIN_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /** Checks if method call is selectKey() on GroupStream. */
    private boolean isGroupSelectKeyMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT_KEY.equals(methodCall.name) &&
               GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /** Checks if method call is select() on GroupStream. */
    private boolean isGroupSelectMethod(MethodInsnNode methodCall) {
        return METHOD_SELECT.equals(methodCall.name) &&
               GROUP_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /** Checks if method call is distinct() on QubitStream or JoinStream. */
    private boolean isDistinctMethod(MethodInsnNode methodCall) {
        return METHOD_DISTINCT.equals(methodCall.name) &&
               (QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner) ||
                JOIN_STREAM_INTERNAL_NAME.equals(methodCall.owner));
    }

    /** Checks if method call is skip() on QubitStream (OFFSET in SQL). */
    private boolean isSkipMethod(MethodInsnNode methodCall) {
        return METHOD_SKIP.equals(methodCall.name) &&
               QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /** Checks if method call is limit() on QubitStream (LIMIT in SQL). */
    private boolean isLimitMethod(MethodInsnNode methodCall) {
        return METHOD_LIMIT.equals(methodCall.name) &&
               QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner);
    }

    /** Extracts integer argument from instruction before method call. */
    private Integer extractIntegerArgument(InsnList instructions, int methodCallIndex) {
        // Look backward to find the instruction that pushes the integer argument
        for (int i = methodCallIndex - 1; i >= 0 && i >= methodCallIndex - 3; i--) {
            AbstractInsnNode insn = instructions.get(i);

            // ICONST_M1 to ICONST_5 (opcode 2-8)
            if (insn.getOpcode() >= Opcodes.ICONST_M1 &&
                insn.getOpcode() <= Opcodes.ICONST_5) {
                return insn.getOpcode() - Opcodes.ICONST_0;
            }

            // BIPUSH (byte value)
            if (insn instanceof IntInsnNode intInsn &&
                insn.getOpcode() == Opcodes.BIPUSH) {
                return intInsn.operand;
            }

            // SIPUSH (short value)
            if (insn instanceof IntInsnNode intInsn &&
                insn.getOpcode() == Opcodes.SIPUSH) {
                return intInsn.operand;
            }

            // LDC (constant from pool)
            if (insn instanceof LdcInsnNode ldcInsn &&
                ldcInsn.cst instanceof Integer intValue) {
                return intValue;
            }

            // Skip labels and line numbers
            if (insn instanceof LineNumberNode || insn instanceof LabelNode) {
                continue;
            }

            // If we hit any other instruction type, stop looking
            break;
        }
        return null;  // Value is not a constant (e.g., from a variable)
    }

    private LambdaCallSite createCallSite(ClassNode classNode, MethodNode method, MethodInsnNode methodCall,
                                          List<PendingLambda> pendingLambdas, PendingAggregation pendingAggregation,
                                          JoinType pendingJoinType, boolean pendingJoinSelectJoined,
                                          boolean pendingGroupQuery, boolean pendingGroupSelectKey,
                                          boolean pendingDistinct,
                                          Integer pendingSkipValue, Integer pendingLimitValue,
                                          int lineNumber, int insnIndex) {
        // Pass aggregation method to extractLambdaInfo
        String aggregationMethod = pendingAggregation != null ? pendingAggregation.aggregationMethod : null;
        LambdaInfo info = extractLambdaInfo(pendingLambdas, aggregationMethod, pendingGroupQuery);

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
            info.groupSortLambdas,  //sortedBy() in group context
            pendingGroupSelectKey,  //selectKey() flag
            pendingDistinct,  //distinct() flag
            pendingSkipValue,  //skip() value
            pendingLimitValue  //limit() value
        );
    }

    private LambdaInfo extractLambdaInfo(List<PendingLambda> pendingLambdas,
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
            } else if (JOIN_ENTRY_METHODS.contains(lambda.fluentMethod)) {
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

    /** Looks forward to find fluent method (where/select/join/groupBy). */
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
        Log.tracef("No fluent method found within %d instructions of index %d (total instructions: %d)",
                Math.min(5, instructions.size() - startIndex - 1), startIndex, instructions.size());
        return null;
    }

    /** Checks if invokedynamic creates a QuerySpec/BiQuerySpec/GroupQuerySpec lambda. */
    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR) ||
               desc.contains(BI_QUERY_SPEC_DESCRIPTOR) ||
               desc.contains(GROUP_QUERY_SPEC_DESCRIPTOR);
    }

    /** Extracts lambda method handle from invokedynamic instruction. */
    private Handle extractLambdaHandle(InvokeDynamicInsnNode invokeDynamic) {
        Object[] bsmArgs = invokeDynamic.bsmArgs;

        if (bsmArgs != null && bsmArgs.length >= 2 && bsmArgs[1] instanceof Handle handle) {
            return handle;
        }

        // Log why extraction failed for debugging
        if (bsmArgs == null) {
            Log.tracef("Lambda handle extraction failed: bsmArgs is null for invokedynamic %s", invokeDynamic.name);
        } else if (bsmArgs.length < 2) {
            Log.tracef("Lambda handle extraction failed: bsmArgs has %d elements (expected >= 2) for invokedynamic %s",
                    bsmArgs.length, invokeDynamic.name);
        } else {
            Log.tracef("Lambda handle extraction failed: bsmArgs[1] is %s (expected Handle) for invokedynamic %s",
                    bsmArgs[1] != null ? bsmArgs[1].getClass().getSimpleName() : "null", invokeDynamic.name);
        }

        return null;
    }

    /** Checks if method call is a terminal on the specified stream type. */
    private boolean isStreamTerminalCall(MethodInsnNode methodCall, String streamInternalName) {
        return FLUENT_TERMINAL_METHODS.contains(methodCall.name) &&
               streamInternalName.equals(methodCall.owner);
    }

    /** Checks if method call is a terminal operation on QubitStream. */
    private boolean isQubitStreamTerminalCall(MethodInsnNode methodCall) {
        return isStreamTerminalCall(methodCall, QUBIT_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is a terminal operation on JoinStream. */
    private boolean isJoinStreamTerminalCall(MethodInsnNode methodCall) {
        return isStreamTerminalCall(methodCall, JOIN_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is a terminal operation on GroupStream. */
    private boolean isGroupStreamTerminalCall(MethodInsnNode methodCall) {
        return isStreamTerminalCall(methodCall, GROUP_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is groupBy on QubitEntity/Repository/Stream. */
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
        return returnTypeContains(methodCall.desc, GROUP_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is join/leftJoin on QubitEntity/Repository/Stream. */
    private boolean isJoinMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's a join entry point method name
        if (!JOIN_ENTRY_METHODS.contains(name)) {
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
        return returnTypeContains(methodCall.desc, JOIN_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is an aggregation (min/max/avg/sum). */
    private boolean isAggregationMethod(MethodInsnNode methodCall) {
        String name = methodCall.name;

        // Check if it's an aggregation method name
        if (!AGGREGATION_METHOD_NAMES.contains(name)) {
            return false;
        }

        // Accept if owner is QubitStream interface (instance method call)
        if (QUBIT_STREAM_INTERNAL_NAME.equals(methodCall.owner)) {
            return true;
        }

        // Also accept if it's a static method call (invokestatic) with QubitStream return type
        // This handles direct static calls like Person.min(...)
        if (methodCall.getOpcode() == Opcodes.INVOKESTATIC) {
            return returnTypeContains(methodCall.desc, QUBIT_STREAM_INTERNAL_NAME);
        }

        // Also accept instance method calls on QubitRepository that return QubitStream
        // This handles repository.min(...) calls
        if (methodCall.getOpcode() == Opcodes.INVOKEVIRTUAL ||
            methodCall.getOpcode() == Opcodes.INVOKEINTERFACE) {
            return returnTypeContains(methodCall.desc, QUBIT_STREAM_INTERNAL_NAME);
        }

        return false;
    }

}
