package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnTypeContains;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
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

import io.quarkiverse.qubit.SortDirection;
import io.quarkiverse.qubit.deployment.analysis.CallSite.AggregationCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.Common;
import io.quarkiverse.qubit.deployment.analysis.CallSite.GroupCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.JoinCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.JoinType;
import io.quarkiverse.qubit.deployment.analysis.CallSite.LambdaPair;
import io.quarkiverse.qubit.deployment.analysis.CallSite.SimpleCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.SortLambda;
import io.quarkus.logging.Log;

/**
 * Scans bytecode for invokedynamic instructions creating QuerySpec lambdas.
 * Supports standard, join (BiQuerySpec), and group (GroupQuerySpec) queries.
 */
public class InvokeDynamicScanner {

    /** Query context type: STANDARD, JOIN, or GROUP. */
    public enum QueryContext {
        /** Standard query (no special context). */
        STANDARD,
        /** Join query context (after join() or leftJoin()). */
        JOIN,
        /** Group query context (after groupBy()). */
        GROUP
    }

    /** Scans class bytecode for QuerySpec lambda call sites. */
    public List<CallSite> scanClass(byte[] classBytes, String className) {
        List<CallSite> callSites = new ArrayList<>();

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

    /** Lambda spec type: QUERY_SPEC, BI_QUERY_SPEC, or GROUP_QUERY_SPEC. Package-private for testing. */
    enum LambdaSpecType {
        /** Standard QuerySpec lambda. */
        QUERY_SPEC,
        /** BiQuerySpec lambda (for join queries). */
        BI_QUERY_SPEC,
        /** GroupQuerySpec lambda (for group queries). */
        GROUP_QUERY_SPEC
    }

    /** Tracked lambda with spec type (QuerySpec, BiQuerySpec, GroupQuerySpec). Package-private for testing. */
    record PendingLambda(String methodName, String descriptor, String fluentMethod, LambdaSpecType specType) {
        boolean isBiEntity() {
            return specType == LambdaSpecType.BI_QUERY_SPEC;
        }

        boolean isGroupSpec() {
            return specType == LambdaSpecType.GROUP_QUERY_SPEC;
        }
    }

    /** Tracked aggregation method (min, max, avg, sum). Package-private for testing. */
    record PendingAggregation(String aggregationMethod) {
    }

    /**
     * Mutable state container for method scanning. Encapsulates all tracking variables
     * used during bytecode instruction iteration.
     *
     * <p>
     * <b>Why a class instead of a record?</b> State is incrementally mutated during
     * the scan loop. A mutable class with clear reset semantics is more appropriate
     * than creating new immutable objects on each instruction.
     *
     * <p>
     * <b>Thread safety:</b> Not thread-safe. Each scanMethod call creates its own instance.
     *
     * <p>
     * <b>Visibility:</b> Package-private to enable unit testing.
     */
    static final class MethodScanState {
        private final List<PendingLambda> pendingLambdas = new ArrayList<>();
        private @Nullable PendingAggregation pendingAggregation;
        private @Nullable JoinType pendingJoinType;
        private boolean pendingJoinSelectJoined;
        private boolean pendingJoinSelect;
        private boolean pendingGroupQuery;
        private boolean pendingGroupSelectKey;
        private boolean pendingDistinct;
        private @Nullable Integer pendingSkipValue;
        private @Nullable Integer pendingLimitValue;
        private int currentLine = -1;
        private int groupSelectLine = -1;
        private int joinSelectJoinedLine = -1;
        private int joinSelectLine = -1;

        List<PendingLambda> pendingLambdas() {
            return pendingLambdas;
        }

        void addLambda(PendingLambda lambda) {
            pendingLambdas.add(lambda);
        }

        boolean hasLambdas() {
            return !pendingLambdas.isEmpty();
        }

        @Nullable
        PendingAggregation pendingAggregation() {
            return pendingAggregation;
        }

        void setAggregation(String methodName) {
            this.pendingAggregation = new PendingAggregation(methodName);
        }

        @Nullable
        JoinType pendingJoinType() {
            return pendingJoinType;
        }

        void setJoinType(JoinType type) {
            this.pendingJoinType = type;
        }

        boolean isJoinContext() {
            return pendingJoinType != null;
        }

        boolean pendingJoinSelectJoined() {
            return pendingJoinSelectJoined;
        }

        void markJoinSelectJoined(int line) {
            this.pendingJoinSelectJoined = true;
            this.joinSelectJoinedLine = line;
        }

        boolean pendingJoinSelect() {
            return pendingJoinSelect;
        }

        void markJoinSelect(int line) {
            this.pendingJoinSelect = true;
            this.joinSelectLine = line;
            Log.infof("Join context: detected JoinStream.select() at line %d", line);
        }

        boolean isGroupContext() {
            return pendingGroupQuery;
        }

        void markGroupQuery() {
            this.pendingGroupQuery = true;
        }

        boolean pendingGroupSelectKey() {
            return pendingGroupSelectKey;
        }

        void markGroupSelectKey(int line) {
            this.pendingGroupSelectKey = true;
            this.groupSelectLine = line;
        }

        void markGroupSelect(int line) {
            this.groupSelectLine = line;
        }

        boolean hasDistinct() {
            return pendingDistinct;
        }

        void markDistinct() {
            this.pendingDistinct = true;
        }

        @Nullable
        Integer skipValue() {
            return pendingSkipValue;
        }

        void setSkipValue(@Nullable Integer value) {
            this.pendingSkipValue = value;
        }

        @Nullable
        Integer limitValue() {
            return pendingLimitValue;
        }

        void setLimitValue(@Nullable Integer value) {
            this.pendingLimitValue = value;
        }

        int currentLine() {
            return currentLine;
        }

        void updateLine(int line) {
            this.currentLine = line;
        }

        /**
         * Computes effective line number for call site identification.
         * Prioritizes context-specific lines over terminal line.
         */
        int effectiveLine() {
            if (pendingGroupQuery && groupSelectLine > 0) {
                return groupSelectLine;
            }
            if (pendingJoinSelectJoined && joinSelectJoinedLine > 0) {
                return joinSelectJoinedLine;
            }
            if (pendingJoinSelect && joinSelectLine > 0) {
                return joinSelectLine;
            }
            return currentLine;
        }

        /**
         * Checks if instruction is a terminal operation on QubitStream/JoinStream/GroupStream.
         */
        boolean isTerminalOperation(AbstractInsnNode insn, InvokeDynamicScanner scanner) {
            if (!(insn instanceof MethodInsnNode methodCall) || pendingLambdas.isEmpty()) {
                return false;
            }

            // Check for GroupStream terminals when in group context
            if (pendingGroupQuery) {
                if (scanner.isGroupStreamTerminalCall(methodCall)) {
                    Log.debugf("Group context: detected GroupStream terminal %s.%s", methodCall.owner, methodCall.name);
                    return true;
                }
                boolean hasSelectLambda = hasGroupSelectLambda();
                boolean isQubitTerminal = scanner.isQubitStreamTerminalCall(methodCall);
                Log.debugf("Group context check: hasSelectLambda=%b, hasSelectKey=%b, isQubitTerminal=%b, method=%s.%s",
                        hasSelectLambda, pendingGroupSelectKey, isQubitTerminal, methodCall.owner, methodCall.name);
                if ((hasSelectLambda || pendingGroupSelectKey) && isQubitTerminal) {
                    Log.debugf("Group context: detected QubitStream terminal after select %s.%s", methodCall.owner,
                            methodCall.name);
                    return true;
                }
                return false;
            }

            // Check for JoinStream terminals when in join context
            if (pendingJoinType != null) {
                if (pendingJoinSelectJoined) {
                    return scanner.isQubitStreamTerminalCall(methodCall);
                }
                if (pendingJoinSelect || hasJoinSelectLambda()) {
                    Log.infof("Join context: hasJoinSelect=%b, hasJoinSelectLambda=%b, checking terminal %s.%s",
                            pendingJoinSelect, hasJoinSelectLambda(), methodCall.owner, methodCall.name);
                    return scanner.isQubitStreamTerminalCall(methodCall);
                }
                return scanner.isJoinStreamTerminalCall(methodCall);
            }

            return scanner.isQubitStreamTerminalCall(methodCall);
        }

        /** Checks if pending lambdas include a select() with GroupQuerySpec. */
        private boolean hasGroupSelectLambda() {
            return hasSelectLambdaOfType(LambdaSpecType.GROUP_QUERY_SPEC);
        }

        /** Checks if pending lambdas include a select() with BiQuerySpec. */
        private boolean hasJoinSelectLambda() {
            return hasSelectLambdaOfType(LambdaSpecType.BI_QUERY_SPEC);
        }

        /** Checks if pending lambdas include a select() with the specified spec type. */
        private boolean hasSelectLambdaOfType(LambdaSpecType specType) {
            for (PendingLambda lambda : pendingLambdas) {
                if (METHOD_SELECT.equals(lambda.fluentMethod) && lambda.specType == specType) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Resets state for next call site detection within the same method.
         * Called after a terminal operation is found and processed.
         */
        void reset() {
            pendingLambdas.clear();
            pendingAggregation = null;
            pendingJoinType = null;
            pendingJoinSelectJoined = false;
            joinSelectJoinedLine = -1;
            pendingJoinSelect = false;
            joinSelectLine = -1;
            pendingGroupQuery = false;
            pendingGroupSelectKey = false;
            groupSelectLine = -1;
            pendingDistinct = false;
            pendingSkipValue = null;
            pendingLimitValue = null;
            // Note: currentLine is NOT reset - it tracks position in method
        }
    }

    /**
     * Mutable builder for LambdaInfo. Encapsulates the classification logic
     * that was previously a "Brain Method" in extractLambdaInfo.
     *
     * <p>
     * Each classify* method handles one category of lambda, returning true
     * if the lambda was handled (allowing early exit from the if-else chain).
     */
    private static final class LambdaInfoBuilder {
        // Collection fields
        private final List<LambdaPair> whereLambdas = new ArrayList<>();
        private final List<SortLambda> sortLambdas = new ArrayList<>();
        private final List<LambdaPair> biEntityWhereLambdas = new ArrayList<>();
        private final List<LambdaPair> havingLambdas = new ArrayList<>();
        private final List<LambdaPair> groupSelectLambdas = new ArrayList<>();
        private final List<SortLambda> groupSortLambdas = new ArrayList<>();

        // Single-value fields
        private @Nullable String groupByMethod;
        private @Nullable String groupByDescriptor;
        private @Nullable String firstWhereMethod;
        private @Nullable String firstWhereDescriptor;
        private @Nullable String selectMethod;
        private @Nullable String selectDescriptor;
        private @Nullable String aggregationMethod;
        private @Nullable String aggregationDescriptor;
        private @Nullable String joinRelationshipMethod;
        private @Nullable String joinRelationshipDescriptor;
        private @Nullable String biEntityProjectionMethod;
        private @Nullable String biEntityProjectionDescriptor;

        // Context
        private final boolean isGroupQuery;

        LambdaInfoBuilder(boolean isGroupQuery) {
            this.isGroupQuery = isGroupQuery;
        }

        /** Classifies a lambda and updates the appropriate field. Returns true if handled. */
        void classify(PendingLambda lambda, boolean isAggregation, boolean isLastLambda) {
            // Aggregation mapper (must be checked first - last lambda in aggregation query)
            if (isAggregation && isLastLambda) {
                aggregationMethod = lambda.methodName();
                aggregationDescriptor = lambda.descriptor();
                return;
            }

            String fluentMethod = lambda.fluentMethod();

            // Dispatch based on fluent method name
            if (classifyGroupByLambda(lambda, fluentMethod))
                return;
            if (classifyHavingLambda(lambda, fluentMethod))
                return;
            if (classifyGroupSelectLambda(lambda, fluentMethod))
                return;
            if (classifyGroupSortLambda(lambda, fluentMethod))
                return;
            if (classifyJoinRelationshipLambda(lambda, fluentMethod))
                return;
            if (classifyWhereLambda(lambda, fluentMethod))
                return;
            if (classifySelectLambda(lambda, fluentMethod))
                return;
            classifySortLambda(lambda, fluentMethod);
        }

        private boolean classifyGroupByLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!METHOD_GROUP_BY.equals(fluentMethod))
                return false;
            groupByMethod = lambda.methodName();
            groupByDescriptor = lambda.descriptor();
            return true;
        }

        private boolean classifyHavingLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!METHOD_HAVING.equals(fluentMethod))
                return false;
            havingLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
            return true;
        }

        private boolean classifyGroupSelectLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!METHOD_SELECT.equals(fluentMethod) || !lambda.isGroupSpec())
                return false;
            groupSelectLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
            return true;
        }

        private boolean classifyGroupSortLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!lambda.isGroupSpec())
                return false;
            if (METHOD_SORTED_BY.equals(fluentMethod)) {
                groupSortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.ASCENDING));
                return true;
            }
            if (METHOD_SORTED_DESCENDING_BY.equals(fluentMethod)) {
                groupSortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.DESCENDING));
                return true;
            }
            return false;
        }

        private boolean classifyJoinRelationshipLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!JOIN_ENTRY_METHODS.contains(fluentMethod))
                return false;
            joinRelationshipMethod = lambda.methodName();
            joinRelationshipDescriptor = lambda.descriptor();
            return true;
        }

        private boolean classifyWhereLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!METHOD_WHERE.equals(fluentMethod))
                return false;
            if (lambda.isBiEntity()) {
                biEntityWhereLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
            } else {
                whereLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
                if (firstWhereMethod == null) {
                    firstWhereMethod = lambda.methodName();
                    firstWhereDescriptor = lambda.descriptor();
                }
            }
            return true;
        }

        private boolean classifySelectLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (!METHOD_SELECT.equals(fluentMethod))
                return false;
            if (lambda.isBiEntity()) {
                biEntityProjectionMethod = lambda.methodName();
                biEntityProjectionDescriptor = lambda.descriptor();
            } else {
                selectMethod = lambda.methodName();
                selectDescriptor = lambda.descriptor();
            }
            return true;
        }

        private void classifySortLambda(PendingLambda lambda, @Nullable String fluentMethod) {
            if (METHOD_SORTED_BY.equals(fluentMethod)) {
                sortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.ASCENDING));
            } else if (METHOD_SORTED_DESCENDING_BY.equals(fluentMethod)) {
                sortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.DESCENDING));
            }
        }

        /** Builds the final LambdaInfo record from accumulated state. */
        LambdaInfo build(List<PendingLambda> pendingLambdas) {
            PendingLambda first = pendingLambdas.isEmpty() ? null : pendingLambdas.getFirst();
            return new LambdaInfo(
                    first != null ? first.methodName() : null,
                    first != null ? first.descriptor() : null,
                    first != null && first.fluentMethod() != null ? first.fluentMethod() : METHOD_WHERE,
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
                    biEntityProjectionMethod,
                    biEntityProjectionDescriptor,
                    isGroupQuery,
                    groupByMethod,
                    groupByDescriptor,
                    havingLambdas.isEmpty() ? null : havingLambdas,
                    groupSelectLambdas.isEmpty() ? null : groupSelectLambdas,
                    groupSortLambdas.isEmpty() ? null : groupSortLambdas);
        }
    }

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
            String aggregationLambdaMethod, // Aggregation mapper lambda
            String aggregationLambdaDescriptor, // Aggregation mapper descriptor
            String joinRelationshipLambdaMethod, // Join relationship lambda
            String joinRelationshipLambdaDescriptor, // Join relationship descriptor
            List<LambdaPair> biEntityWhereLambdas, // BiQuerySpec WHERE lambdas
            String biEntityProjectionLambdaMethod, // BiQuerySpec SELECT lambda for join projections
            String biEntityProjectionLambdaDescriptor, // BiQuerySpec SELECT lambda descriptor
            boolean isGroup, // True if this is a group query
            String groupByLambdaMethod, // groupBy() key extractor lambda
            String groupByLambdaDescriptor, // groupBy() lambda descriptor
            List<LambdaPair> havingLambdas, // having() lambdas
            List<LambdaPair> groupSelectLambdas, // select() on GroupStream
            List<SortLambda> groupSortLambdas //sortedBy() on GroupStream
    ) {
    }

    private void scanMethod(ClassNode classNode, MethodNode method, List<CallSite> callSites) {
        InsnList instructions = method.instructions;
        MethodScanState state = new MethodScanState();

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            updateScanState(insn, instructions, i, state);

            if (state.isTerminalOperation(insn, this)) {
                CallSite callSite = createCallSite(classNode, method, (MethodInsnNode) insn, state, i);
                callSites.add(callSite);
                Log.debugf("Found fluent API terminal operation: %s", callSite);

                // Debug logging for group call sites
                if (callSite instanceof GroupCallSite g) {
                    Log.infof("Detected GROUP call site: %s (groupSelect=%d, hasSelectKey=%b, usedLine=%d)",
                            g.getCallSiteId(),
                            g.groupSelectLambdas().size(),
                            g.isGroupSelectKey(), state.effectiveLine());
                }

                state.reset();
            }
        }
    }

    /**
     * Updates scan state based on current instruction.
     * Detects lambdas, aggregations, joins, groups, pagination, etc.
     */
    private void updateScanState(AbstractInsnNode insn, InsnList instructions, int index, MethodScanState state) {
        // Update line number
        if (insn instanceof LineNumberNode lineNode) {
            state.updateLine(lineNode.line);
        }

        // Detect lambdas
        detectAndAddLambda(insn, instructions, index, state);

        // Process method calls for context detection
        if (insn instanceof MethodInsnNode methodCall) {
            detectMethodCallContext(methodCall, instructions, index, state);
        }
    }

    /**
     * Detects context-setting method calls (aggregation, join, group, pagination).
     */
    private void detectMethodCallContext(MethodInsnNode methodCall, InsnList instructions, int index, MethodScanState state) {
        // Aggregation (min, max, avg, sum)
        if (isAggregationMethod(methodCall)) {
            state.setAggregation(methodCall.name);
        }

        // Join (join, leftJoin)
        if (isJoinMethod(methodCall)) {
            state.setJoinType(METHOD_LEFT_JOIN.equals(methodCall.name) ? JoinType.LEFT : JoinType.INNER);
        }

        // JoinStream methods
        if (isJoinSelectJoinedMethod(methodCall)) {
            state.markJoinSelectJoined(state.currentLine());
        }
        if (isJoinSelectMethod(methodCall)) {
            state.markJoinSelect(state.currentLine());
        }

        // GroupStream methods
        if (isGroupMethod(methodCall)) {
            state.markGroupQuery();
        }
        if (isGroupSelectKeyMethod(methodCall)) {
            state.markGroupSelectKey(state.currentLine());
        }
        if (isGroupSelectMethod(methodCall)) {
            state.markGroupSelect(state.currentLine());
        }

        // Distinct
        if (isDistinctMethod(methodCall)) {
            state.markDistinct();
        }

        // Pagination
        if (isSkipMethod(methodCall)) {
            state.setSkipValue(extractIntegerArgument(instructions, index));
        }
        if (isLimitMethod(methodCall)) {
            state.setLimitValue(extractIntegerArgument(instructions, index));
        }
    }

    private void detectAndAddLambda(AbstractInsnNode insn, InsnList instructions, int index,
            MethodScanState state) {
        if (insn instanceof InvokeDynamicInsnNode invokeDynamic && isQuerySpecLambda(invokeDynamic)) {
            Handle handle = extractLambdaHandle(invokeDynamic);
            if (handle != null) {
                String fluentMethod = findFluentMethodForward(instructions, index);
                LambdaSpecType specType = determineLambdaSpecType(invokeDynamic);
                state.addLambda(new PendingLambda(handle.getName(), handle.getDesc(), fluentMethod, specType));
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

    /** Extracts integer value from a bytecode instruction if it's a constant. */
    @FunctionalInterface
    private interface IntegerExtractor {
        /** Returns extracted integer, or null if this extractor doesn't apply. */
        Integer extract(AbstractInsnNode insn);
    }

    /** Registry of integer constant extraction patterns, checked in order. */
    private static final List<IntegerExtractor> INTEGER_EXTRACTORS = List.of(
            // ICONST_M1 to ICONST_5 (opcode 2-8)
            insn -> {
                int opcode = insn.getOpcode();
                return (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
                        ? opcode - Opcodes.ICONST_0
                        : null;
            },
            // BIPUSH (byte value)
            insn -> (insn instanceof IntInsnNode intInsn && insn.getOpcode() == Opcodes.BIPUSH)
                    ? intInsn.operand
                    : null,
            // SIPUSH (short value)
            insn -> (insn instanceof IntInsnNode intInsn && insn.getOpcode() == Opcodes.SIPUSH)
                    ? intInsn.operand
                    : null,
            // LDC (constant from pool)
            insn -> (insn instanceof LdcInsnNode ldcInsn && ldcInsn.cst instanceof Integer intValue)
                    ? intValue
                    : null);

    /** Checks if instruction is metadata (label, line number) that should be skipped. */
    private static boolean isMetadataInstruction(AbstractInsnNode insn) {
        return insn instanceof LineNumberNode || insn instanceof LabelNode;
    }

    /** Extracts integer argument from instruction before method call. Returns null if not a constant. */
    private @Nullable Integer extractIntegerArgument(InsnList instructions, int methodCallIndex) {
        // Look backward to find the instruction that pushes the integer argument
        for (int i = methodCallIndex - 1; i >= 0 && i >= methodCallIndex - 3; i--) {
            AbstractInsnNode insn = instructions.get(i);

            // Skip metadata instructions (labels, line numbers)
            if (isMetadataInstruction(insn)) {
                continue;
            }

            // Try each extractor pattern
            for (IntegerExtractor extractor : INTEGER_EXTRACTORS) {
                Integer value = extractor.extract(insn);
                if (value != null) {
                    return value;
                }
            }

            // Unknown instruction type - stop looking
            return null;
        }
        return null; // Value is not a constant (e.g., from a variable)
    }

    /**
     * Creates the appropriate CallSite subtype from MethodScanState.
     * Dispatches to GroupCallSite, JoinCallSite, AggregationCallSite, or SimpleCallSite
     * based on the scan state.
     */
    private CallSite createCallSite(ClassNode classNode, MethodNode method, MethodInsnNode methodCall,
            MethodScanState state, int insnIndex) {
        // Extract aggregation method if present
        PendingAggregation agg = state.pendingAggregation();
        String aggregationMethod = agg != null ? agg.aggregationMethod() : null;

        // Classify lambdas using the builder
        LambdaInfo info = extractLambdaInfo(state.pendingLambdas(), aggregationMethod, state.isGroupContext());

        // For aggregation queries, use aggregation method as target instead of getSingleResult
        String targetMethod = aggregationMethod != null ? aggregationMethod : methodCall.name;

        Common common = new Common(
                classNode.name.replace('/', '.'),
                method.name,
                state.effectiveLine(),
                targetMethod,
                insnIndex,
                state.hasDistinct(),
                state.skipValue(),
                state.limitValue());

        if (info.isGroup) {
            return new GroupCallSite(common,
                    info.whereLambdas,
                    info.groupByLambdaMethod,
                    info.groupByLambdaDescriptor,
                    info.havingLambdas,
                    info.groupSelectLambdas,
                    info.groupSortLambdas,
                    state.pendingGroupSelectKey());
        }

        if (state.pendingJoinType() != null) {
            return new JoinCallSite(common,
                    state.pendingJoinType(),
                    info.joinRelationshipLambdaMethod,
                    info.joinRelationshipLambdaDescriptor,
                    info.whereLambdas,
                    info.biEntityWhereLambdas,
                    info.sortLambdas,
                    state.pendingJoinSelectJoined(),
                    info.biEntityProjectionLambdaMethod,
                    info.biEntityProjectionLambdaDescriptor);
        }

        if (aggregationMethod != null) {
            return new AggregationCallSite(common,
                    info.whereLambdas,
                    info.aggregationLambdaMethod,
                    info.aggregationLambdaDescriptor);
        }

        return new SimpleCallSite(common,
                info.primaryLambdaMethod,
                info.primaryLambdaDescriptor,
                info.primaryFluentMethod,
                info.whereLambdas,
                info.selectLambdaMethod,
                info.selectLambdaDescriptor,
                info.sortLambdas);
    }

    /**
     * Extracts and classifies lambdas into a LambdaInfo structure.
     * Uses LambdaInfoBuilder to encapsulate the classification logic.
     */
    private LambdaInfo extractLambdaInfo(List<PendingLambda> pendingLambdas,
            @Nullable String aggregationMethodName, boolean isGroupQuery) {
        LambdaInfoBuilder builder = new LambdaInfoBuilder(isGroupQuery);
        boolean isAggregation = aggregationMethodName != null;

        for (int i = 0; i < pendingLambdas.size(); i++) {
            PendingLambda lambda = pendingLambdas.get(i);
            boolean isLastLambda = (i == pendingLambdas.size() - 1);
            builder.classify(lambda, isAggregation, isLastLambda);
        }

        return builder.build(pendingLambdas);
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
