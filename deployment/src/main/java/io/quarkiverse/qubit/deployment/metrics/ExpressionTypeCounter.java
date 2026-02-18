package io.quarkiverse.qubit.deployment.metrics;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Counts expression types in a LambdaExpression AST for build metrics.
 * <p>
 * Walks the AST recursively and categorizes each node into expression types
 * defined in {@link BuildMetricsCollector}.
 */
public final class ExpressionTypeCounter {

    /** Temporal types that map to EXPR_TEMPORAL. */
    private static final Set<Class<?>> TEMPORAL_TYPES = Set.of(
            LocalDate.class, LocalDateTime.class, LocalTime.class,
            Instant.class, OffsetDateTime.class, ZonedDateTime.class,
            java.util.Date.class, java.sql.Date.class, java.sql.Timestamp.class);

    /** String methods that map to EXPR_STRING. */
    private static final Set<String> STRING_METHODS = Set.of(
            "equals", "equalsIgnoreCase", "contains", "startsWith", "endsWith",
            "isEmpty", "isBlank", "toLowerCase", "toUpperCase", "trim", "length",
            "substring", "concat", "replace", "matches");

    private ExpressionTypeCounter() {
        // Utility class
    }

    /** Counts expression types in the given AST and records them to the metrics collector. */
    public static void countAndRecord(LambdaExpression expression, BuildMetricsCollector collector) {
        if (expression == null || collector == null) {
            return;
        }
        Map<String, Integer> counts = count(expression);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                collector.recordExpressionType(entry.getKey());
            }
        }
    }

    /** Counts expression types in the given AST, returning a map of type to count. */
    public static Map<String, Integer> count(LambdaExpression expression) {
        Map<String, Integer> counts = new HashMap<>();
        if (expression != null) {
            countRecursive(expression, counts);
        }
        return counts;
    }

    private static void countRecursive(LambdaExpression expr, Map<String, Integer> counts) {
        switch (expr) {
            case BinaryOp op -> {
                // Categorize by operator type
                String exprType = categorizeBinaryOp(op.operator());
                increment(counts, exprType);
                // Check for BigDecimal operands
                if (isBigDecimalOperation(op)) {
                    increment(counts, BuildMetricsCollector.EXPR_BIG_DECIMAL);
                }
                // Recurse into children
                countRecursive(op.left(), counts);
                countRecursive(op.right(), counts);
            }

            case UnaryOp op -> {
                increment(counts, BuildMetricsCollector.EXPR_BOOLEAN);
                countRecursive(op.operand(), counts);
            }

            case FieldAccess _ -> increment(counts, BuildMetricsCollector.EXPR_FIELD_ACCESS);

            case BiEntityFieldAccess _ -> increment(counts, BuildMetricsCollector.EXPR_FIELD_ACCESS);

            case PathExpression path -> {
                increment(counts, BuildMetricsCollector.EXPR_FIELD_ACCESS);
                // Count each segment as a field access if path has multiple segments
                if (path.segments().size() > 1) {
                    for (int i = 1; i < path.segments().size(); i++) {
                        increment(counts, BuildMetricsCollector.EXPR_FIELD_ACCESS);
                    }
                }
            }

            case BiEntityPathExpression path -> {
                increment(counts, BuildMetricsCollector.EXPR_FIELD_ACCESS);
                if (path.segments().size() > 1) {
                    for (int i = 1; i < path.segments().size(); i++) {
                        increment(counts, BuildMetricsCollector.EXPR_FIELD_ACCESS);
                    }
                }
            }

            case MethodCall call -> {
                increment(counts, BuildMetricsCollector.EXPR_METHOD_CALL);
                // Categorize by method semantics
                if (STRING_METHODS.contains(call.methodName())) {
                    increment(counts, BuildMetricsCollector.EXPR_STRING);
                } else if (isTemporalMethod(call)) {
                    increment(counts, BuildMetricsCollector.EXPR_TEMPORAL);
                }
                // Recurse into target and arguments
                if (call.target() != null) {
                    countRecursive(call.target(), counts);
                }
                for (LambdaExpression arg : call.arguments()) {
                    countRecursive(arg, counts);
                }
            }

            case Constant c -> {
                // Check for temporal or BigDecimal constants
                if (c.value() != null) {
                    if (TEMPORAL_TYPES.contains(c.type())) {
                        increment(counts, BuildMetricsCollector.EXPR_TEMPORAL);
                    } else if (c.type() == BigDecimal.class) {
                        increment(counts, BuildMetricsCollector.EXPR_BIG_DECIMAL);
                    }
                }
            }

            case Cast cast -> countRecursive(cast.expression(), counts);

            case InstanceOf io -> countRecursive(io.expression(), counts);

            case Conditional cond -> {
                countRecursive(cond.condition(), counts);
                countRecursive(cond.trueValue(), counts);
                countRecursive(cond.falseValue(), counts);
            }

            case ConstructorCall ctor -> {
                for (LambdaExpression arg : ctor.arguments()) {
                    countRecursive(arg, counts);
                }
            }

            case ArrayCreation arr -> {
                for (LambdaExpression elem : arr.elements()) {
                    countRecursive(elem, counts);
                }
            }

            case InExpression in -> {
                countRecursive(in.field(), counts);
                countRecursive(in.collection(), counts);
            }

            case MemberOfExpression memberOf -> {
                countRecursive(memberOf.value(), counts);
                countRecursive(memberOf.collectionField(), counts);
            }

            case GroupKeyReference keyRef -> {
                if (keyRef.keyExpression() != null) {
                    countRecursive(keyRef.keyExpression(), counts);
                }
            }

            case GroupAggregation agg -> {
                if (agg.fieldExpression() != null) {
                    countRecursive(agg.fieldExpression(), counts);
                }
            }

            case ScalarSubquery sub -> {
                increment(counts, BuildMetricsCollector.EXPR_SUBQUERY);
                if (sub.fieldExpression() != null) {
                    countRecursive(sub.fieldExpression(), counts);
                }
                if (sub.predicate() != null) {
                    countRecursive(sub.predicate(), counts);
                }
            }

            case ExistsSubquery sub -> {
                increment(counts, BuildMetricsCollector.EXPR_SUBQUERY);
                countRecursive(sub.predicate(), counts);
            }

            case InSubquery sub -> {
                increment(counts, BuildMetricsCollector.EXPR_SUBQUERY);
                countRecursive(sub.field(), counts);
                countRecursive(sub.selectExpression(), counts);
                if (sub.predicate() != null) {
                    countRecursive(sub.predicate(), counts);
                }
            }

            case SubqueryBuilderReference ref -> {
                increment(counts, BuildMetricsCollector.EXPR_SUBQUERY);
                if (ref.predicate() != null) {
                    countRecursive(ref.predicate(), counts);
                }
            }

            case CorrelatedVariable cv -> {
                countRecursive(cv.fieldExpression(), counts);
            }

            case MathFunction math -> {
                countRecursive(math.operand(), counts);
                if (math.secondOperand() != null) {
                    countRecursive(math.secondOperand(), counts);
                }
            }

            case TreatExpression treat -> countRecursive(treat.inner(), counts);

            case FoldedMethodCall folded -> {
                for (LambdaExpression arg : folded.arguments()) {
                    countRecursive(arg, counts);
                }
            }

            // Leaf nodes that don't contribute to expression type counts
            case Parameter _,CapturedVariable _,NullLiteral _,BiEntityParameter _,GroupParameter _ -> {
                // No counting for these leaf nodes
            }
        }
    }

    private static String categorizeBinaryOp(BinaryOp.Operator op) {
        return switch (op) {
            case EQ, NE, LT, LE, GT, GE -> BuildMetricsCollector.EXPR_COMPARISON;
            case AND, OR -> BuildMetricsCollector.EXPR_BOOLEAN;
            case ADD, SUB, MUL, DIV, MOD -> BuildMetricsCollector.EXPR_ARITHMETIC;
        };
    }

    private static boolean isBigDecimalOperation(BinaryOp op) {
        return isBigDecimalExpression(op.left()) || isBigDecimalExpression(op.right());
    }

    private static boolean isBigDecimalExpression(LambdaExpression expr) {
        return switch (expr) {
            case Constant c -> c.type() == BigDecimal.class;
            case FieldAccess f -> f.fieldType() == BigDecimal.class;
            case PathExpression p -> p.resultType() == BigDecimal.class;
            case MethodCall m -> m.returnType() == BigDecimal.class;
            default -> false;
        };
    }

    private static boolean isTemporalMethod(MethodCall call) {
        // Check if target is a temporal type
        if (call.target() != null) {
            return switch (call.target()) {
                case FieldAccess f -> TEMPORAL_TYPES.contains(f.fieldType());
                case PathExpression p -> TEMPORAL_TYPES.contains(p.resultType());
                default -> false;
            };
        }
        return false;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }
}
