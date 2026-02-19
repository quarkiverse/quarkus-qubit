package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import io.quarkiverse.qubit.deployment.analysis.CallSite.JoinType;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaSpecType;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.PendingAggregation;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.PendingLambda;
import io.quarkus.logging.Log;

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
final class MethodScanState {
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
            if (isQubitTerminal) {
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
            if (METHOD_SELECT.equals(lambda.fluentMethod()) && lambda.specType() == specType) {
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
