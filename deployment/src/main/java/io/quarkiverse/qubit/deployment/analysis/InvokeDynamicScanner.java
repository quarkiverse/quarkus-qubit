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

import io.quarkiverse.qubit.deployment.analysis.CallSite.AggregationCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.Common;
import io.quarkiverse.qubit.deployment.analysis.CallSite.GroupCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.JoinCallSite;
import io.quarkiverse.qubit.deployment.analysis.CallSite.JoinType;
import io.quarkiverse.qubit.deployment.analysis.CallSite.SimpleCallSite;
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

        // Detect orphaned lambdas: QuerySpec lambdas without a terminal operation in the same method.
        // Skip lambda$ methods — they legitimately contain nested QuerySpec lambdas for subqueries.
        if (state.hasLambdas() && !method.name.startsWith("lambda$")) {
            String ownerName = classNode.name.replace('/', '.');
            for (PendingLambda lambda : state.pendingLambdas()) {
                Log.warnf("Qubit: QuerySpec lambda '%s' in %s.%s has no terminal operation " +
                        "(toList/count/exists/getSingleResult/findFirst) in the same method. " +
                        "This query will not be pre-compiled and will fail at runtime.",
                        lambda.methodName(), ownerName, method.name);
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

        if (info.isGroup()) {
            return new GroupCallSite(common,
                    info.whereLambdas(),
                    info.groupByLambdaMethod(),
                    info.groupByLambdaDescriptor(),
                    info.havingLambdas(),
                    info.groupSelectLambdas(),
                    info.groupSortLambdas(),
                    state.pendingGroupSelectKey());
        }

        if (state.pendingJoinType() != null) {
            return new JoinCallSite(common,
                    state.pendingJoinType(),
                    info.joinRelationshipLambdaMethod(),
                    info.joinRelationshipLambdaDescriptor(),
                    info.whereLambdas(),
                    info.biEntityWhereLambdas(),
                    info.sortLambdas(),
                    state.pendingJoinSelectJoined(),
                    info.biEntityProjectionLambdaMethod(),
                    info.biEntityProjectionLambdaDescriptor());
        }

        if (aggregationMethod != null) {
            return new AggregationCallSite(common,
                    info.whereLambdas(),
                    info.aggregationLambdaMethod(),
                    info.aggregationLambdaDescriptor());
        }

        return new SimpleCallSite(common,
                info.primaryLambdaMethod(),
                info.primaryLambdaDescriptor(),
                info.primaryFluentMethod(),
                info.whereLambdas(),
                info.selectLambdaMethod(),
                info.selectLambdaDescriptor(),
                info.sortLambdas());
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

    /** Checks if method call is a terminal operation on QubitStream. Package-private for {@link MethodScanState}. */
    boolean isQubitStreamTerminalCall(MethodInsnNode methodCall) {
        return isStreamTerminalCall(methodCall, QUBIT_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is a terminal operation on JoinStream. Package-private for {@link MethodScanState}. */
    boolean isJoinStreamTerminalCall(MethodInsnNode methodCall) {
        return isStreamTerminalCall(methodCall, JOIN_STREAM_INTERNAL_NAME);
    }

    /** Checks if method call is a terminal operation on GroupStream. Package-private for {@link MethodScanState}. */
    boolean isGroupStreamTerminalCall(MethodInsnNode methodCall) {
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
