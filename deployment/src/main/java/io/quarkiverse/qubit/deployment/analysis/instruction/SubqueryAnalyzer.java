package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.tree.MethodInsnNode;

import io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryBuilderReference;
import io.quarkiverse.qubit.deployment.common.ClassLoaderHelper;
import io.quarkiverse.qubit.deployment.common.EntityClassInfo;
import io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkus.logging.Log;

/** Analyzes subquery bytecode: Subqueries.subquery() factory and SubqueryBuilder.* methods. */
public class SubqueryAnalyzer {

    public boolean isSubqueriesMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(SUBQUERIES_INTERNAL_NAME);
    }

    public boolean isSubqueryBuilderMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(SUBQUERY_BUILDER_INTERNAL_NAME);
    }

    /** Creates SubqueryBuilderReference for subsequent builder method calls. */
    public void handleSubqueriesFactoryMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (!METHOD_SUBQUERY.equals(methodInsn.name)) {
            Log.debugf("Unexpected Subqueries method: %s", methodInsn.name);
            return;
        }

        // Pop the entity class from stack
        LambdaExpression classExpr = ctx.pop();
        EntityClassInfo entityInfo = ClassLoaderHelper.extractEntityClassInfo(classExpr);

        // Push SubqueryBuilderReference onto stack
        ctx.push(new SubqueryBuilderReference(entityInfo.clazz(), entityInfo.className()));
        Log.debugf("Created SubqueryBuilderReference for %s", entityInfo.getEffectiveClassName());
    }

    public void handleSubqueryBuilderMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        String methodName = methodInsn.name;
        int argCount = DescriptorParser.countMethodArguments(methodInsn.desc);

        // Pop arguments from stack (but keep them for processing)
        List<LambdaExpression> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            if (!ctx.isStackEmpty()) {
                args.add(0, ctx.pop()); // Add at beginning to maintain order
            }
        }

        // Pop the SubqueryBuilderReference (the target of the method call)
        if (ctx.isStackEmpty()) {
            Log.debugf("Stack empty when expecting SubqueryBuilderReference for %s", methodName);
            return;
        }

        LambdaExpression builderRef = ctx.pop();
        if (!(builderRef instanceof SubqueryBuilderReference subqueryBuilder)) {
            Log.debugf("Expected SubqueryBuilderReference but got %s for %s",
                    builderRef.getClass().getSimpleName(), methodName);
            // Push everything back and return
            ctx.push(builderRef);
            for (LambdaExpression arg : args) {
                ctx.push(arg);
            }
            return;
        }

        Class<?> entityClass = subqueryBuilder.entityClass();
        String entityClassName = subqueryBuilder.entityClassName();
        LambdaExpression predicate = subqueryBuilder.predicate();

        // Handle different SubqueryBuilder methods
        switch (methodName) {
            case METHOD_WHERE -> handleBuilderWhere(ctx, subqueryBuilder, args);
            case SUBQUERY_AVG -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args,
                    SubqueryAggregationType.AVG, Double.class);
            case SUBQUERY_SUM -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args,
                    SubqueryAggregationType.SUM, Number.class);
            case SUBQUERY_MIN -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args,
                    SubqueryAggregationType.MIN, Comparable.class);
            case SUBQUERY_MAX -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args,
                    SubqueryAggregationType.MAX, Comparable.class);
            case SUBQUERY_COUNT -> handleBuilderCountSubquery(ctx, entityClass, entityClassName, predicate, args);
            case SUBQUERY_EXISTS -> handleBuilderExistsSubquery(ctx, entityClass, entityClassName, args, false);
            case SUBQUERY_NOT_EXISTS -> handleBuilderExistsSubquery(ctx, entityClass, entityClassName, args, true);
            case SUBQUERY_IN -> handleBuilderInSubquery(ctx, entityClass, entityClassName, predicate, args, false);
            case SUBQUERY_NOT_IN -> handleBuilderInSubquery(ctx, entityClass, entityClassName, predicate, args, true);
            default -> Log.debugf("Unhandled SubqueryBuilder method: %s", methodName);
        }
    }

    private void handleBuilderWhere(AnalysisContext ctx, SubqueryBuilderReference currentBuilder, List<LambdaExpression> args) {
        if (args.size() != 1) {
            Log.debugf("Expected 1 argument for SubqueryBuilder.where, got %d", args.size());
            return;
        }

        LambdaExpression newPredicate = args.getFirst();
        SubqueryBuilderReference updatedBuilder = currentBuilder.withPredicate(newPredicate);
        ctx.push(updatedBuilder);
    }

    private void handleBuilderScalarSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
            LambdaExpression predicate, List<LambdaExpression> args,
            SubqueryAggregationType aggregationType, Class<?> defaultResultType) {
        if (args.size() != 1) {
            Log.debugf("Expected 1 argument for SubqueryBuilder.%s, got %d", aggregationType, args.size());
            return;
        }

        LambdaExpression selector = args.getFirst();
        // AVG always returns Double, otherwise infer from selector
        Class<?> resultType = (aggregationType == SubqueryAggregationType.AVG)
                ? Double.class
                : ExpressionTypeInferrer.inferFieldType(selector, defaultResultType);

        ctx.push(new ScalarSubquery(aggregationType, entityClass, entityClassName, selector, predicate, resultType));
    }

    /** Combines builder and argument predicates with AND. */
    private void handleBuilderCountSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
            LambdaExpression builderPredicate, List<LambdaExpression> args) {
        LambdaExpression argPredicate = args.isEmpty() ? null : args.getFirst();
        LambdaExpression finalPredicate = CapturedVariableHelper.combinePredicatesWithAnd(builderPredicate, argPredicate);
        ctx.push(new ScalarSubquery(SubqueryAggregationType.COUNT, entityClass, entityClassName, null, finalPredicate,
                Long.class));
    }

    private void handleBuilderExistsSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
            List<LambdaExpression> args, boolean negated) {
        if (args.size() != 1) {
            Log.debugf("Expected 1 argument for SubqueryBuilder.%s, got %d", negated ? "notExists" : "exists", args.size());
            return;
        }

        LambdaExpression predicate = args.getFirst();
        ctx.push(new ExistsSubquery(entityClass, entityClassName, predicate, negated));
    }

    /** Combines builder and argument predicates with AND. */
    private void handleBuilderInSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
            LambdaExpression builderPredicate, List<LambdaExpression> args, boolean negated) {
        if (args.size() < 2 || args.size() > 3) {
            Log.debugf("Expected 2-3 arguments for SubqueryBuilder.%s, got %d", negated ? "notIn" : "in", args.size());
            return;
        }

        LambdaExpression field = args.getFirst();
        LambdaExpression selector = args.get(1);
        LambdaExpression argPredicate = args.size() == 3 ? args.get(2) : null;

        // Combine predicates if both exist
        LambdaExpression finalPredicate = CapturedVariableHelper.combinePredicatesWithAnd(builderPredicate, argPredicate);

        ctx.push(new InSubquery(field, entityClass, entityClassName, selector, finalPredicate, negated));
    }
}
