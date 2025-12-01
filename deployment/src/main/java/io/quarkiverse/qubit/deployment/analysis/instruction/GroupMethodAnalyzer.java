package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregation;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupKeyReference;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupParameter;
import io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.function.Function;

import static io.quarkiverse.qubit.runtime.QubitConstants.*;

/**
 * Analyzes Group interface method calls for GROUP BY queries.
 *
 * <p>This class handles:
 * <ul>
 *   <li>{@code g.key()} → GroupKeyReference</li>
 *   <li>{@code g.count()} → GroupAggregation(COUNT)</li>
 *   <li>{@code g.countDistinct(field)} → GroupAggregation(COUNT_DISTINCT, field)</li>
 *   <li>{@code g.avg(field)} → GroupAggregation(AVG, field)</li>
 *   <li>{@code g.min(field)} → GroupAggregation(MIN, field)</li>
 *   <li>{@code g.max(field)} → GroupAggregation(MAX, field)</li>
 *   <li>{@code g.sumInteger(field)} → GroupAggregation(SUM_INTEGER, field)</li>
 *   <li>{@code g.sumLong(field)} → GroupAggregation(SUM_LONG, field)</li>
 *   <li>{@code g.sumDouble(field)} → GroupAggregation(SUM_DOUBLE, field)</li>
 * </ul>
 *
 * <p>Iteration 8: Extracted from MethodInvocationHandler to reduce class size
 * and improve maintainability (addresses ARCH-001, MAINT-002).
 *
 * @see MethodInvocationHandler
 * @see GroupAggregation
 */
public class GroupMethodAnalyzer {

    private static final Logger log = Logger.getLogger(GroupMethodAnalyzer.class);

    /**
     * Checks if the instruction is a Group interface method call.
     *
     * @param methodInsn the method instruction to check
     * @return true if this is a Group interface method call
     */
    public boolean isGroupMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(GROUP_INTERNAL_NAME);
    }

    /**
     * Handles Group interface method calls for GROUP BY queries.
     *
     * @param ctx the analysis context
     * @param methodInsn the method instruction
     */
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
            default -> log.debugf("Unhandled Group method: %s", methodName);
        }
    }

    /**
     * Handles g.key() - returns the grouping key.
     */
    private void handleGroupKey(AnalysisContext ctx) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        if (target instanceof GroupParameter) {
            // For now, we create a placeholder GroupKeyReference
            // The actual key expression will be resolved at code generation time
            ctx.push(new GroupKeyReference(null, Object.class));
        } else {
            log.warnf("Unexpected target for g.key(): %s", target);
        }
    }

    /**
     * Handles g.count() - counts entities in the group.
     */
    private void handleGroupCount(AnalysisContext ctx) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        if (target instanceof GroupParameter) {
            ctx.push(GroupAggregation.count());
        } else {
            log.warnf("Unexpected target for g.count(): %s", target);
        }
    }

    /**
     * Handles g.countDistinct(field) - counts distinct values.
     */
    private void handleGroupCountDistinct(AnalysisContext ctx) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression fieldArg = ctx.pop();  // The field extractor (analyzed nested lambda)
        LambdaExpression target = ctx.pop();     // The Group parameter

        if (target instanceof GroupParameter) {
            ctx.push(GroupAggregation.countDistinct(fieldArg));
        } else {
            log.warnf("Unexpected target for g.countDistinct(): %s", target);
        }
    }

    /**
     * Handles g.avg/sumInteger/sumLong/sumDouble(field) - aggregations that return fixed types.
     */
    private void handleGroupAggregationWithField(
            AnalysisContext ctx,
            Function<LambdaExpression, GroupAggregation> aggregationFactory) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression fieldArg = ctx.pop();  // The field extractor
        LambdaExpression target = ctx.pop();     // The Group parameter

        if (target instanceof GroupParameter) {
            ctx.push(aggregationFactory.apply(fieldArg));
        } else {
            log.warnf("Unexpected target for group aggregation: %s", target);
        }
    }

    /**
     * Handles g.min(field) and g.max(field) - aggregations that preserve field type.
     */
    private void handleGroupMinMax(AnalysisContext ctx, boolean isMin) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression fieldArg = ctx.pop();  // The field extractor
        LambdaExpression target = ctx.pop();     // The Group parameter

        if (target instanceof GroupParameter) {
            // Determine result type from field expression
            Class<?> resultType = ExpressionTypeInferrer.inferFieldType(fieldArg);
            if (isMin) {
                ctx.push(GroupAggregation.min(fieldArg, resultType));
            } else {
                ctx.push(GroupAggregation.max(fieldArg, resultType));
            }
        } else {
            log.warnf("Unexpected target for g.%s(): %s", isMin ? "min" : "max", target);
        }
    }
}
