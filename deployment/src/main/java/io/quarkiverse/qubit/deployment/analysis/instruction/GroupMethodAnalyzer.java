package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregation;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupKeyReference;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupParameter;
import io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContext.PopPairResult;
import io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.function.Function;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

/** Analyzes Group interface method calls: key(), count(), avg(), min(), max(), sum*(). */
public class GroupMethodAnalyzer {

    public boolean isGroupMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(GROUP_INTERNAL_NAME);
    }

    public void handleGroupMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        String methodName = methodInsn.name;

        switch (methodName) {
            case METHOD_KEY -> handleGroupKey(ctx);
            case METHOD_COUNT -> handleGroupCount(ctx);
            case METHOD_COUNT_DISTINCT -> handleGroupCountDistinct(ctx);
            case METHOD_AVG -> handleGroupAggregationWithField(ctx, GroupAggregation::avg);
            case METHOD_MIN -> handleGroupMinMax(ctx, true);
            case METHOD_MAX -> handleGroupMinMax(ctx, false);
            case METHOD_SUM_INTEGER -> handleGroupAggregationWithField(ctx, GroupAggregation::sumInteger);
            case METHOD_SUM_LONG -> handleGroupAggregationWithField(ctx, GroupAggregation::sumLong);
            case METHOD_SUM_DOUBLE -> handleGroupAggregationWithField(ctx, GroupAggregation::sumDouble);
            default -> Log.debugf("Unhandled Group method: %s", methodName);
        }
    }

    private void handleGroupKey(AnalysisContext ctx) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        if (target instanceof GroupParameter) {
            // Placeholder; actual key expression resolved at code generation time
            ctx.push(new GroupKeyReference(null, Object.class));
        } else {
            Log.debugf("Unexpected target for g.key(): %s", target);
        }
    }

    private void handleGroupCount(AnalysisContext ctx) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        if (target instanceof GroupParameter) {
            ctx.push(GroupAggregation.count());
        } else {
            Log.debugf("Unexpected target for g.count(): %s", target);
        }
    }

    private void handleGroupCountDistinct(AnalysisContext ctx) {
        PopPairResult pair = ctx.popPair();
        if (pair == null) {
            return;
        }

        LambdaExpression fieldArg = pair.right();  // The field extractor (was on top)
        LambdaExpression target = pair.left();     // The Group parameter

        if (target instanceof GroupParameter) {
            ctx.push(GroupAggregation.countDistinct(fieldArg));
        } else {
            Log.debugf("Unexpected target for g.countDistinct(): %s", target);
        }
    }

    private void handleGroupAggregationWithField(
            AnalysisContext ctx,
            Function<LambdaExpression, GroupAggregation> aggregationFactory) {
        PopPairResult pair = ctx.popPair();
        if (pair == null) {
            return;
        }

        LambdaExpression fieldArg = pair.right();
        LambdaExpression target = pair.left();

        if (target instanceof GroupParameter) {
            ctx.push(aggregationFactory.apply(fieldArg));
        } else {
            Log.debugf("Unexpected target for group aggregation: %s", target);
        }
    }

    /** Preserves field type for min/max aggregations. */
    private void handleGroupMinMax(AnalysisContext ctx, boolean isMin) {
        PopPairResult pair = ctx.popPair();
        if (pair == null) {
            return;
        }

        LambdaExpression fieldArg = pair.right();  // The field extractor (was on top)
        LambdaExpression target = pair.left();     // The Group parameter

        if (target instanceof GroupParameter) {
            // Determine result type from field expression
            Class<?> resultType = ExpressionTypeInferrer.inferFieldType(fieldArg);
            if (isMin) {
                ctx.push(GroupAggregation.min(fieldArg, resultType));
            } else {
                ctx.push(GroupAggregation.max(fieldArg, resultType));
            }
        } else {
            Log.debugf("Unexpected target for g.%s(): %s", isMin ? "min" : "max", target);
        }
    }
}
