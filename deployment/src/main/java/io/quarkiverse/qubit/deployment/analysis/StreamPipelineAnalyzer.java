package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import io.quarkus.logging.Log;

/**
 * Extracts fluent API pipelines by scanning backwards from terminal operations.
 */
public class StreamPipelineAnalyzer {

    /** Single operation in a fluent API pipeline. */
    public record PipelineOperation(
            String operationName,
            String lambdaMethodName,
            String lambdaMethodDescriptor,
            int instructionIndex) {
        @Override
        public String toString() {
            return operationName + "(lambda=" + lambdaMethodName + ")";
        }
    }

    /** Complete pipeline from entry point to terminal operation. */
    public record StreamPipeline(
            String className,
            String methodName,
            String terminalOperation,
            List<PipelineOperation> operations,
            int lineNumber) {
        public int operationCount() {
            return operations.size();
        }

        public boolean isCountQuery() {
            return METHOD_COUNT.equals(terminalOperation);
        }

        @Override
        public String toString() {
            return String.format("Pipeline{%s.%s line %d: %s -> %s (%d ops)}",
                    className, methodName, lineNumber,
                    operations, terminalOperation, operations.size());
        }
    }

    /** Extracts complete pipeline by scanning backwards from terminal operation. */
    public StreamPipeline analyzePipeline(
            ClassNode classNode,
            MethodNode method,
            int terminalInsnIndex,
            String terminalOperation,
            int lineNumber) {

        InsnList instructions = method.instructions;
        List<PipelineOperation> operations = new ArrayList<>();
        PipelineScanState state = new PipelineScanState();

        // Scan backwards from terminal operation to collect all operations
        int currentIndex = terminalInsnIndex - 1;
        while (currentIndex >= 0) {
            AbstractInsnNode insn = instructions.get(currentIndex);

            if (insn instanceof InvokeDynamicInsnNode invokeDynamic) {
                processInvokeDynamic(invokeDynamic, state, currentIndex);
            } else if (insn instanceof MethodInsnNode methodCall
                    && processMethodCall(methodCall, state, operations, currentIndex)) {
                break; // Reached entry point
            }

            currentIndex--;
        }

        return buildPipeline(classNode, method, terminalOperation, operations, lineNumber);
    }

    /** Mutable state for pipeline scanning. */
    private static class PipelineScanState {
        String pendingLambdaMethod;
        String pendingLambdaDescriptor;

        void setLambda(String method, String descriptor) {
            this.pendingLambdaMethod = method;
            this.pendingLambdaDescriptor = descriptor;
        }

        void clearLambda() {
            this.pendingLambdaMethod = null;
            this.pendingLambdaDescriptor = null;
        }

        boolean hasLambda() {
            return pendingLambdaMethod != null;
        }
    }

    /** Processes InvokeDynamic instruction to extract lambda info. */
    private void processInvokeDynamic(InvokeDynamicInsnNode invokeDynamic,
            PipelineScanState state, int currentIndex) {
        if (isQuerySpecLambda(invokeDynamic)) {
            Handle lambdaHandle = extractLambdaHandle(invokeDynamic);
            if (lambdaHandle != null) {
                state.setLambda(lambdaHandle.getName(), lambdaHandle.getDesc());
                Log.tracef("Found lambda at index %d: %s", currentIndex, state.pendingLambdaMethod);
            }
        }
    }

    /** Processes method call instruction. Returns true if entry point reached. */
    private boolean processMethodCall(MethodInsnNode methodCall, PipelineScanState state,
            List<PipelineOperation> operations, int currentIndex) {
        String methodName = methodCall.name;

        if (FLUENT_INTERMEDIATE_METHODS.contains(methodName) && state.hasLambda()) {
            PipelineOperation op = new PipelineOperation(
                    methodName,
                    state.pendingLambdaMethod,
                    state.pendingLambdaDescriptor,
                    currentIndex);
            operations.add(op);
            Log.tracef("Found operation at index %d: %s", currentIndex, op);
            state.clearLambda();
            return false;
        }

        if (FLUENT_ENTRY_POINT_METHODS.contains(methodName)) {
            Log.tracef("Reached entry point at index %d: %s", currentIndex, methodName);
            return true;
        }

        return false;
    }

    /** Builds the final pipeline result. */
    private StreamPipeline buildPipeline(ClassNode classNode, MethodNode method,
            String terminalOperation, List<PipelineOperation> operations, int lineNumber) {
        if (operations.isEmpty()) {
            Log.debugf("No operations found in pipeline for %s.%s line %d",
                    classNode.name, method.name, lineNumber);
            return null;
        }

        // Operations were collected in reverse order (backwards scan), so reverse them
        Collections.reverse(operations);

        String className = classNode.name.replace('/', '.');
        StreamPipeline pipeline = new StreamPipeline(
                className,
                method.name,
                terminalOperation,
                operations,
                lineNumber);

        Log.debugf("Analyzed pipeline: %s", pipeline);
        return pipeline;
    }

    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR);
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
}
