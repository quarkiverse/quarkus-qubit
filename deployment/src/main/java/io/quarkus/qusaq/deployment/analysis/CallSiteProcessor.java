package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.qusaq.deployment.InvokeDynamicScanner;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.QusaqProcessor;
import io.quarkus.qusaq.deployment.generation.QueryExecutorClassGenerator;
import io.quarkus.qusaq.deployment.util.BytecodeLoader;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes lambda call sites: analyzes bytecode, deduplicates, generates executors.
 */
public class CallSiteProcessor {

    private static final Logger log = Logger.getLogger(CallSiteProcessor.class);

    private final LambdaBytecodeAnalyzer bytecodeAnalyzer;
    private final LambdaDeduplicator deduplicator;
    private final QueryExecutorClassGenerator classGenerator;
    private final AtomicInteger queryCounter;

    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator,
            AtomicInteger queryCounter) {
        this.bytecodeAnalyzer = bytecodeAnalyzer;
        this.deduplicator = deduplicator;
        this.classGenerator = classGenerator;
        this.queryCounter = queryCounter;
    }

    /**
     * Analyzes lambda at call site and generates query executor class.
     */
    public void processCallSite(
            InvokeDynamicScanner.LambdaCallSite callSite,
            ApplicationArchivesBuildItem applicationArchives,
            AtomicInteger generatedCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {
        try {
            log.debugf("Processing call site: %s", callSite);

            String ownerClassName = callSite.ownerClassName();
            byte[] classBytes = BytecodeLoader.loadClassBytecode(ownerClassName, applicationArchives);

            if (classBytes == null) {
                log.warnf("Could not load bytecode for class: %s", ownerClassName);
                return;
            }

            LambdaExpression lambdaExpression = bytecodeAnalyzer.analyze(
                    classBytes,
                    callSite.lambdaMethodName(),
                    callSite.lambdaMethodDescriptor());

            if (lambdaExpression == null) {
                log.warnf("Could not analyze lambda expression at: %s", callSite.getCallSiteId());
                return;
            }

            log.debugf("Analyzed lambda at %s: %s", callSite.getCallSiteId(), lambdaExpression);

            boolean isCountQuery = callSite.isCountQuery();
            String lambdaHash = deduplicator.computeLambdaHash(lambdaExpression, isCountQuery);
            String callSiteId = callSite.getCallSiteId();

            int capturedVarCount = countCapturedVariables(lambdaExpression);

            if (capturedVarCount > 0) {
                log.debugf("Lambda at %s contains %d captured variable(s)", callSiteId, capturedVarCount);
            }

            if (deduplicator.handleDuplicateLambda(callSiteId, lambdaHash,
                    isCountQuery, capturedVarCount, deduplicatedCount, queryTransformations)) {
                return;
            }

            String executorClassName = generateAndRegisterExecutor(
                    lambdaExpression,
                    isCountQuery,
                    callSiteId,
                    generatedClass,
                    queryTransformations);

            deduplicator.registerExecutor(lambdaHash, executorClassName);
            generatedCount.incrementAndGet();

            log.debugf("Generated executor: %s for call site %s (hash: %s)",
                    executorClassName, callSiteId, lambdaHash.substring(0, 8));

        } catch (Exception e) {
            log.errorf(e, "Failed to process call site: %s", callSite);
        }
    }

    /**
     * Generates executor class and registers it.
     */
    private String generateAndRegisterExecutor(
            LambdaExpression expression,
            boolean isCountQuery,
            String queryId,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        int capturedVarCount = countCapturedVariables(expression);

        String className = "io.quarkus.qusaq.generated.QueryExecutor_" +
                           queryCounter.getAndIncrement();

        byte[] bytecode = classGenerator.generateQueryExecutorClass(
                expression,
                className,
                isCountQuery);

        generatedClass.produce(new GeneratedClassBuildItem(true, className, bytecode));
        queryTransformations.produce(
                new QusaqProcessor.QueryTransformationBuildItem(queryId, className, Object.class, isCountQuery, capturedVarCount));

        log.debugf("Generated query executor: %s (%s, %d captured vars)",
                   className, isCountQuery ? "COUNT" : "LIST", capturedVarCount);

        return className;
    }

    /**
     * Counts distinct captured variables in lambda expression.
     */
    private int countCapturedVariables(LambdaExpression expression) {
        Set<Integer> capturedIndices = new HashSet<>();
        collectCapturedVariableIndices(expression, capturedIndices);

        return capturedIndices.size();
    }

    /**
     * Recursively collects captured variable indices.
     */
    private void collectCapturedVariableIndices(LambdaExpression expression, Set<Integer> capturedIndices) {
        if (expression == null) {
            return;
        }

        if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
            capturedIndices.add(capturedVar.index());
        } else if (expression instanceof LambdaExpression.BinaryOp binOp) {
            collectCapturedVariableIndices(binOp.left(), capturedIndices);
            collectCapturedVariableIndices(binOp.right(), capturedIndices);
        } else if (expression instanceof LambdaExpression.UnaryOp unaryOp) {
            collectCapturedVariableIndices(unaryOp.operand(), capturedIndices);
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            collectCapturedVariableIndices(methodCall.target(), capturedIndices);
            for (LambdaExpression arg : methodCall.arguments()) {
                collectCapturedVariableIndices(arg, capturedIndices);
            }
        }
    }
}
