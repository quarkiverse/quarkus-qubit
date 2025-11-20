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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes lambda call sites: analyzes bytecode, deduplicates, generates executors.
 */
public class CallSiteProcessor {

    private static final Logger log = Logger.getLogger(CallSiteProcessor.class);

    /**
     * Result of lambda bytecode analysis containing expressions and captured variable count.
     */
    private record LambdaAnalysisResult(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            int totalCapturedVarCount) {}

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
     * <p>
     * Phase 2.2: Supports combined where() + select() queries with both predicate and projection.
     * Phase 2.5: Supports multiple where() predicates combined with AND.
     */
    public void processCallSite(
            InvokeDynamicScanner.LambdaCallSite callSite,
            ApplicationArchivesBuildItem applicationArchives,
            AtomicInteger generatedCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {
        try {
            log.debugf("Processing fluent API call site: %s", callSite);

            byte[] classBytes = BytecodeLoader.loadClassBytecode(callSite.ownerClassName(), applicationArchives);
            if (classBytes == null) {
                log.warnf("Could not load bytecode for class: %s", callSite.ownerClassName());
                return;
            }

            String callSiteId = callSite.getCallSiteId();
            LambdaAnalysisResult result = analyzeLambdas(classBytes, callSite, callSiteId);
            if (result == null) {
                return;
            }

            if (result.totalCapturedVarCount > 0) {
                log.debugf("Lambda(s) at %s contain %d captured variable(s)",
                          callSiteId, result.totalCapturedVarCount);
            }

            String lambdaHash = computeHash(callSite, result);
            if (deduplicator.handleDuplicateLambda(callSiteId, lambdaHash,
                    callSite.isCountQuery(),
                    result.totalCapturedVarCount, deduplicatedCount, queryTransformations)) {
                return;
            }

            String executorClassName = generateAndRegisterExecutor(
                    result.predicateExpression,
                    result.projectionExpression,
                    callSite.isCountQuery(),
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
     * Analyzes lambdas based on call site type (multiple predicates, combined, or single).
     */
    private LambdaAnalysisResult analyzeLambdas(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        var predicateLambdas = callSite.predicateLambdas();
        boolean hasMultiplePredicates = predicateLambdas != null && predicateLambdas.size() > 1;

        if (hasMultiplePredicates) {
            return analyzeMultiplePredicates(classBytes, callSite, callSiteId);
        } else if (callSite.isCombinedQuery()) {
            return analyzeCombinedQuery(classBytes, callSite, callSiteId);
        } else {
            return analyzeSingleLambda(classBytes, callSite, callSiteId, callSite.isProjectionQuery());
        }
    }

    /**
     * Computes deduplication hash based on query type.
     */
    private String computeHash(InvokeDynamicScanner.LambdaCallSite callSite, LambdaAnalysisResult result) {
        if (callSite.isCombinedQuery()) {
            return deduplicator.computeCombinedHash(
                    result.predicateExpression,
                    result.projectionExpression,
                    callSite.isCountQuery());
        } else {
            LambdaExpression expr = result.predicateExpression != null
                    ? result.predicateExpression
                    : result.projectionExpression;
            return deduplicator.computeLambdaHash(expr, callSite.isCountQuery(), callSite.isProjectionQuery());
        }
    }

    /**
     * Generates executor class and registers it.
     * Phase 2.2: Accepts both predicate and projection expressions.
     */
    private String generateAndRegisterExecutor(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            boolean isCountQuery,
            String queryId,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        // Count captured variables from both expressions
        int capturedVarCount = 0;
        if (predicateExpression != null) {
            capturedVarCount += countCapturedVariables(predicateExpression);
        }
        if (projectionExpression != null) {
            capturedVarCount += countCapturedVariables(projectionExpression);
        }

        String className = "io.quarkus.qusaq.generated.QueryExecutor_" +
                           queryCounter.getAndIncrement();

        byte[] bytecode = classGenerator.generateQueryExecutorClass(
                predicateExpression,
                projectionExpression,
                className,
                isCountQuery);

        generatedClass.produce(new GeneratedClassBuildItem(true, className, bytecode));
        queryTransformations.produce(
                new QusaqProcessor.QueryTransformationBuildItem(queryId, className, Object.class, isCountQuery, capturedVarCount));

        String queryTypeDesc = getQueryTypeDescription(predicateExpression, projectionExpression, isCountQuery);

        log.debugf("Generated query executor: %s (%s, %d captured vars)",
                   className, queryTypeDesc, capturedVarCount);

        return className;
    }

    /**
     * Determines query type identifier based on query characteristics.
     * This is the canonical method for query type determination used across the codebase.
     *
     * @param isCountQuery true if this is a count/exists query
     * @param hasPredicate true if query has WHERE clause
     * @param hasProjection true if query has SELECT clause
     * @return query type: "COUNT", "COMBINED", "PROJECTION", or "LIST"
     */
    static String getQueryType(boolean isCountQuery, boolean hasPredicate, boolean hasProjection) {
        if (isCountQuery) {
            return "COUNT";
        }
        if (hasPredicate && hasProjection) {
            return "COMBINED";
        }
        if (hasProjection) {
            return "PROJECTION";
        }
        return "LIST";
    }

    /**
     * Determines the query type description for logging based on expressions and flags.
     * Adds decorative formatting to the base query type for better log readability.
     */
    private String getQueryTypeDescription(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            boolean isCountQuery) {

        String baseType = getQueryType(
                isCountQuery,
                predicateExpression != null,
                projectionExpression != null);

        // Add decorative formatting for combined queries in logs
        return "COMBINED".equals(baseType) ? "COMBINED(WHERE+SELECT)" : baseType;
    }

    /**
     * Analyzes multiple where() predicates and combines them with AND.
     * Phase 2.5: Handles multiple where() chaining.
     *
     * IMPORTANT: Renumbers CapturedVariable indices before combining to ensure correct ordering.
     * Each lambda has indices starting from 0. When combined, indices must be sequential:
     * - Lambda 1: CapturedVariable(0) stays as index 0
     * - Lambda 2: CapturedVariable(0) becomes index 1 (offset by lambda 1 count)
     * - Lambda 3: CapturedVariable(0) becomes index 2 (offset by lambda 1+2 count)
     */
    private LambdaAnalysisResult analyzeMultiplePredicates(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        var predicateLambdas = callSite.predicateLambdas();
        List<LambdaExpression> predicateExpressions = new ArrayList<>();
        int indexOffset = 0;

        for (var lambdaPair : predicateLambdas) {
            LambdaExpression expr = bytecodeAnalyzer.analyze(
                    classBytes,
                    lambdaPair.methodName(),
                    lambdaPair.descriptor());

            if (expr == null) {
                log.warnf("Could not analyze predicate lambda %s at: %s", lambdaPair.methodName(), callSiteId);
                return null;
            }

            // Renumber this predicate's CapturedVariable indices by the offset
            // to ensure sequential indices across all predicates
            int capturedCount = countCapturedVariables(expr);
            LambdaExpression renumberedExpr = renumberCapturedVariables(expr, indexOffset);

            predicateExpressions.add(renumberedExpr);
            indexOffset += capturedCount;
        }

        int totalCapturedVarCount = indexOffset; // Total from all predicates

        LambdaExpression predicateExpression = combinePredicatesWithAnd(predicateExpressions);
        log.debugf("Combined %d predicates with AND at %s (total %d captured variables)",
                Integer.valueOf(predicateExpressions.size()), callSiteId, Integer.valueOf(totalCapturedVarCount));

        // Check if there's also a select
        LambdaExpression projectionExpression = null;
        if (callSite.projectionLambdaMethodName() != null) {
            projectionExpression = bytecodeAnalyzer.analyze(
                    classBytes,
                    callSite.projectionLambdaMethodName(),
                    callSite.projectionLambdaMethodDescriptor());

            if (projectionExpression == null) {
                log.warnf("Could not analyze projection lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(projectionExpression);
        }

        return new LambdaAnalysisResult(predicateExpression, projectionExpression, totalCapturedVarCount);
    }

    /**
     * Analyzes combined where() + select() query (single where).
     * Phase 2.2: Handles combined queries.
     */
    private LambdaAnalysisResult analyzeCombinedQuery(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        // Analyze predicate lambda (WHERE clause)
        LambdaExpression predicateExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.predicateLambdaMethodName(),
                callSite.predicateLambdaMethodDescriptor());

        if (predicateExpression == null) {
            log.warnf("Could not analyze predicate lambda at: %s", callSiteId);
            return null;
        }

        // Analyze projection lambda (SELECT clause)
        LambdaExpression projectionExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.projectionLambdaMethodName(),
                callSite.projectionLambdaMethodDescriptor());

        if (projectionExpression == null) {
            log.warnf("Could not analyze projection lambda at: %s", callSiteId);
            return null;
        }

        log.debugf("Analyzed combined query at %s: WHERE=%s, SELECT=%s",
                callSiteId, predicateExpression, projectionExpression);

        int totalCapturedVarCount = countCapturedVariables(predicateExpression) +
                                   countCapturedVariables(projectionExpression);

        return new LambdaAnalysisResult(predicateExpression, projectionExpression, totalCapturedVarCount);
    }

    /**
     * Analyzes single lambda (where-only or select-only).
     * Phase 2.1 or Phase 1: Single lambda support.
     */
    private LambdaAnalysisResult analyzeSingleLambda(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId,
            boolean isProjectionQuery) {

        LambdaExpression lambdaExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.lambdaMethodName(),
                callSite.lambdaMethodDescriptor());

        if (lambdaExpression == null) {
            log.warnf("Could not analyze lambda expression at: %s", callSiteId);
            return null;
        }

        log.debugf("Analyzed lambda at %s: %s", callSiteId, lambdaExpression);

        // Assign to appropriate expression based on query type
        LambdaExpression predicateExpression = isProjectionQuery ? null : lambdaExpression;
        LambdaExpression projectionExpression = isProjectionQuery ? lambdaExpression : null;
        int totalCapturedVarCount = countCapturedVariables(lambdaExpression);

        return new LambdaAnalysisResult(predicateExpression, projectionExpression, totalCapturedVarCount);
    }

    /**
     * Combines multiple predicates with AND operation.
     * Phase 2.5: Supports multiple where() chaining.
     */
    private LambdaExpression combinePredicatesWithAnd(List<LambdaExpression> predicates) {
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("Cannot combine empty predicate list");
        }

        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        // Chain predicates with AND: (p1 AND p2 AND p3 AND ...)
        LambdaExpression combined = predicates.get(0);
        for (int i = 1; i < predicates.size(); i++) {
            combined = new LambdaExpression.BinaryOp(
                    combined,
                    LambdaExpression.BinaryOp.Operator.AND,
                    predicates.get(i));
        }

        return combined;
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
        } else if (expression instanceof LambdaExpression.ConstructorCall constructorCall) {
            // Phase 2.4: Handle DTO constructor calls
            for (LambdaExpression arg : constructorCall.arguments()) {
                collectCapturedVariableIndices(arg, capturedIndices);
            }
        }
    }

    /**
     * Renumbers captured variable indices in lambda expression tree by adding offset.
     * This ensures sequential indices when combining multiple lambdas.
     * <p>
     * Example: For second predicate with offset=1:
     * - CapturedVariable(0) becomes CapturedVariable(1)
     * - CapturedVariable(1) becomes CapturedVariable(2)
     * <p>
     * Recursively walks the entire AST tree to renumber all CapturedVariable nodes.
     *
     * @param expression the lambda expression AST
     * @param offset the offset to add to each captured variable index
     * @return new expression tree with renumbered indices
     */
    private LambdaExpression renumberCapturedVariables(LambdaExpression expression, int offset) {
        if (expression == null || offset == 0) {
            return expression;
        }

        if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
            return new LambdaExpression.CapturedVariable(capturedVar.index() + offset, capturedVar.type());
        } else if (expression instanceof LambdaExpression.BinaryOp binOp) {
            return new LambdaExpression.BinaryOp(
                    renumberCapturedVariables(binOp.left(), offset),
                    binOp.operator(),
                    renumberCapturedVariables(binOp.right(), offset));
        } else if (expression instanceof LambdaExpression.UnaryOp unaryOp) {
            return new LambdaExpression.UnaryOp(
                    unaryOp.operator(),
                    renumberCapturedVariables(unaryOp.operand(), offset));
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            LambdaExpression newTarget = renumberCapturedVariables(methodCall.target(), offset);
            List<LambdaExpression> newArgs = new ArrayList<>();
            for (LambdaExpression arg : methodCall.arguments()) {
                newArgs.add(renumberCapturedVariables(arg, offset));
            }
            return new LambdaExpression.MethodCall(newTarget, methodCall.methodName(), newArgs, methodCall.returnType());
        } else if (expression instanceof LambdaExpression.ConstructorCall constructorCall) {
            List<LambdaExpression> newArgs = new ArrayList<>();
            for (LambdaExpression arg : constructorCall.arguments()) {
                newArgs.add(renumberCapturedVariables(arg, offset));
            }
            return new LambdaExpression.ConstructorCall(constructorCall.className(), newArgs, constructorCall.resultType());
        } else if (expression instanceof LambdaExpression.Cast cast) {
            return new LambdaExpression.Cast(renumberCapturedVariables(cast.expression(), offset), cast.targetType());
        } else if (expression instanceof LambdaExpression.InstanceOf instanceOf) {
            return new LambdaExpression.InstanceOf(renumberCapturedVariables(instanceOf.expression(), offset), instanceOf.targetType());
        } else if (expression instanceof LambdaExpression.Conditional conditional) {
            return new LambdaExpression.Conditional(
                    renumberCapturedVariables(conditional.condition(), offset),
                    renumberCapturedVariables(conditional.trueValue(), offset),
                    renumberCapturedVariables(conditional.falseValue(), offset));
        } else {
            // These expression types don't contain captured variables, return as-is:
            // FieldAccess, Constant, Parameter, NullLiteral
            return expression;
        }
    }
}
