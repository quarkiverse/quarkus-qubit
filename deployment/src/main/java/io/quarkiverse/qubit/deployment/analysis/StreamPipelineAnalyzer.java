package io.quarkiverse.qubit.deployment.analysis;

import org.jboss.logging.Logger;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.quarkiverse.qubit.runtime.QubitConstants.*;

/**
 * Analyzes bytecode to extract fluent API operation pipelines.
 * <p>
 * Scans backwards from a terminal operation to collect all intermediate operations
 * and their lambda expressions.
 */
public class StreamPipelineAnalyzer {

    private static final Logger log = Logger.getLogger(StreamPipelineAnalyzer.class);

    /**
     * Represents a single operation in a fluent API pipeline.
     */
    public record PipelineOperation(
            String operationName,
            String lambdaMethodName,
            String lambdaMethodDescriptor,
            int instructionIndex
    ) {
        @Override
        public String toString() {
            return operationName + "(lambda=" + lambdaMethodName + ")";
        }
    }

    /**
     * Represents a complete fluent API pipeline from entry point to terminal operation.
     */
    public record StreamPipeline(
            String className,
            String methodName,
            String terminalOperation,
            List<PipelineOperation> operations,
            int lineNumber
    ) {
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

    /**
     * Analyzes bytecode to extract the complete pipeline for a fluent API call.
     * <p>
     * Scans backwards from the terminal operation instruction to find all
     * intermediate operations and their associated lambda expressions.
     *
     * @param classNode the class containing the method
     * @param method the method containing the fluent API call
     * @param terminalInsnIndex instruction index of the terminal operation
     * @param terminalOperation name of terminal operation (e.g., "toList")
     * @param lineNumber source line number
     * @return the complete pipeline, or null if analysis fails
     */
    public StreamPipeline analyzePipeline(
            ClassNode classNode,
            MethodNode method,
            int terminalInsnIndex,
            String terminalOperation,
            int lineNumber) {

        InsnList instructions = method.instructions;
        List<PipelineOperation> operations = new ArrayList<>();

        // Scan backwards from terminal operation to collect all operations
        int currentIndex = terminalInsnIndex - 1;
        String pendingLambdaMethod = null;
        String pendingLambdaDescriptor = null;

        while (currentIndex >= 0) {
            AbstractInsnNode insn = instructions.get(currentIndex);

            // Found a lambda - save it for the next operation we find
            if (insn instanceof InvokeDynamicInsnNode invokeDynamic) {
                if (isQuerySpecLambda(invokeDynamic)) {
                    Handle lambdaHandle = extractLambdaHandle(invokeDynamic);
                    if (lambdaHandle != null) {
                        pendingLambdaMethod = lambdaHandle.getName();
                        pendingLambdaDescriptor = lambdaHandle.getDesc();
                        log.tracef("Found lambda at index %d: %s", currentIndex, pendingLambdaMethod);
                    }
                }
            }

            // Found an intermediate operation (where, select, etc.)
            if (insn instanceof MethodInsnNode methodCall) {
                String methodName = methodCall.name;

                // Check if this is a fluent intermediate operation
                if (FLUENT_INTERMEDIATE_METHODS.contains(methodName) && pendingLambdaMethod != null) {
                    PipelineOperation op = new PipelineOperation(
                            methodName,
                            pendingLambdaMethod,
                            pendingLambdaDescriptor,
                            currentIndex
                    );
                    operations.add(op);
                    log.tracef("Found operation at index %d: %s", currentIndex, op);

                    pendingLambdaMethod = null;
                    pendingLambdaDescriptor = null;
                }
                // Check if this is an entry point - we've reached the start of the pipeline
                else if (FLUENT_ENTRY_POINT_METHODS.contains(methodName)) {
                    log.tracef("Reached entry point at index %d: %s", currentIndex, methodName);
                    break;
                }
            }

            currentIndex--;
        }

        if (operations.isEmpty()) {
            log.warnf("No operations found in pipeline for %s.%s line %d",
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
                lineNumber
        );

        log.debugf("Analyzed pipeline: %s", pipeline);
        return pipeline;
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
}
