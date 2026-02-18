package io.quarkiverse.qubit.deployment.devui;

import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_CONTAINS;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_ENDS_WITH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_EQUALS_IGNORE_CASE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_INDEX_OF;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GET_DAY_OF_MONTH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GET_HOUR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GET_MINUTE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GET_MONTH_VALUE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GET_SECOND;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_GET_YEAR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_EMPTY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_LENGTH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_STARTS_WITH;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUBSTRING;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_TO_LOWER_CASE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_TO_UPPER_CASE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_TRIM;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.STRING_CONCAT;

import java.util.stream.Collectors;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import io.quarkiverse.qubit.deployment.util.ClassNameUtils;

/** Generates pseudo-JPQL strings from LambdaExpression AST for DevUI display. */
public final class JpqlGenerator {

    private static final String ENTITY_ALIAS = "e";

    // JPQL clause keywords
    private static final String JPQL_SELECT = "SELECT ";
    private static final String JPQL_FROM = " FROM ";
    private static final String JPQL_WHERE = " WHERE ";

    // JPQL function prefixes
    private static final String JPQL_COUNT = "COUNT(";
    private static final String JPQL_UPPER = "UPPER(";
    private static final String JPQL_LOWER = "LOWER(";
    private static final String JPQL_LENGTH = "LENGTH(";
    private static final String JPQL_TRIM = "TRIM(";
    private static final String JPQL_SUBSTRING = "SUBSTRING(";
    private static final String JPQL_LOCATE = "LOCATE(";
    private static final String JPQL_MINUS_ONE = ") - 1";

    // Date/Time function prefixes
    private static final String JPQL_YEAR = "YEAR(";
    private static final String JPQL_MONTH = "MONTH(";
    private static final String JPQL_DAY = "DAY(";
    private static final String JPQL_HOUR = "HOUR(";
    private static final String JPQL_MINUTE = "MINUTE(";
    private static final String JPQL_SECOND = "SECOND(";

    // JPQL LIKE pattern fragments
    private static final String LIKE_CONCAT_PREFIX = " LIKE CONCAT('%', ";
    private static final String LIKE_SUFFIX_PERCENT = ", '%')";

    // Aggregation function prefixes
    private static final String JPQL_AVG = "AVG(";
    private static final String JPQL_SUM = "SUM(";
    private static final String JPQL_MIN = "MIN(";
    private static final String JPQL_MAX = "MAX(";
    private static final String JPQL_CONCAT = "CONCAT(";

    private JpqlGenerator() {
        // Utility class
    }

    /** Generates a complete JPQL-like query string from expressions. */
    public static String generateJpql(
            String entityClassName,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            boolean isCountQuery) {

        StringBuilder jpql = new StringBuilder();

        // SELECT clause
        if (isCountQuery) {
            jpql.append(JPQL_SELECT).append(JPQL_COUNT).append(ENTITY_ALIAS).append(")");
        } else if (projectionExpression != null) {
            jpql.append(JPQL_SELECT).append(expressionToJpql(projectionExpression));
        } else {
            jpql.append(JPQL_SELECT).append(ENTITY_ALIAS);
        }

        // FROM clause - extract simple name from fully qualified class name
        String entityName = ClassNameUtils.extractSimpleName(entityClassName);
        jpql.append(JPQL_FROM).append(entityName).append(" ").append(ENTITY_ALIAS);

        // WHERE clause
        if (predicateExpression != null) {
            jpql.append(JPQL_WHERE).append(expressionToJpql(predicateExpression));
        }

        return jpql.toString();
    }

    /** Generates a JPQL-like query string with JOIN clause. */
    public static String generateJoinJpql(
            String entityClassName,
            LambdaExpression joinRelationshipExpression,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            boolean isLeftJoin,
            boolean isCountQuery) {

        StringBuilder jpql = new StringBuilder();
        String joinAlias = "j";

        // SELECT clause
        if (isCountQuery) {
            jpql.append(JPQL_SELECT).append(JPQL_COUNT).append(ENTITY_ALIAS).append(")");
        } else if (projectionExpression != null) {
            jpql.append(JPQL_SELECT).append(expressionToJpqlBiEntity(projectionExpression, joinAlias));
        } else {
            jpql.append(JPQL_SELECT).append(ENTITY_ALIAS);
        }

        // FROM clause
        String entityName = ClassNameUtils.extractSimpleName(entityClassName);
        jpql.append(JPQL_FROM).append(entityName).append(" ").append(ENTITY_ALIAS);

        // JOIN clause
        String joinType = isLeftJoin ? "LEFT JOIN" : "JOIN";
        String joinPath = extractJoinPath(joinRelationshipExpression);
        jpql.append(" ").append(joinType).append(" ").append(ENTITY_ALIAS).append(".").append(joinPath)
                .append(" ").append(joinAlias);

        // WHERE clause
        if (predicateExpression != null) {
            jpql.append(JPQL_WHERE).append(expressionToJpqlBiEntity(predicateExpression, joinAlias));
        }

        return jpql.toString();
    }

    /** Extracts join path from relationship expression (e.g., "orderItems"). */
    private static String extractJoinPath(LambdaExpression expr) {
        if (expr == null) {
            return "?";
        }
        return switch (expr) {
            case FieldAccess fieldAccess -> fieldAccess.fieldName();
            case PathExpression pathExpr -> pathExpr.toPath();
            default -> expressionToJpql(expr);
        };
    }

    /** Converts bi-entity expression to JPQL with entity position aliases. */
    private static String expressionToJpqlBiEntity(LambdaExpression expr, String joinAlias) {
        if (expr == null) {
            return "?";
        }
        return switch (expr) {
            case FieldAccess fieldAccess -> ENTITY_ALIAS + "." + fieldAccess.fieldName();
            case BiEntityFieldAccess biField ->
                (biField.entityPosition() == LambdaExpression.EntityPosition.FIRST ? ENTITY_ALIAS : joinAlias)
                        + "." + biField.fieldName();
            case BiEntityPathExpression biPath -> {
                String alias = biPath.entityPosition() == LambdaExpression.EntityPosition.FIRST
                        ? ENTITY_ALIAS
                        : joinAlias;
                yield alias + "." + biPath.toPath();
            }
            case BinaryOp binOp -> {
                String left = expressionToJpqlBiEntity(binOp.left(), joinAlias);
                String right = expressionToJpqlBiEntity(binOp.right(), joinAlias);
                String op = operatorToJpql(binOp.operator());
                if (isLogicalOperation(binOp)) {
                    yield "(" + left + " " + op + " " + right + ")";
                }
                yield left + " " + op + " " + right;
            }
            case MethodCall methodCall -> methodCallToJpqlBiEntity(methodCall, joinAlias);
            default -> expressionToJpql(expr);
        };
    }

    /** Converts bi-entity method call to JPQL. */
    private static String methodCallToJpqlBiEntity(MethodCall methodCall, String joinAlias) {
        String target = expressionToJpqlBiEntity(methodCall.target(), joinAlias);
        String methodName = methodCall.methodName();

        // Helper to get first argument with bi-entity awareness
        String firstArg = methodCall.arguments().isEmpty() ? "?"
                : expressionToJpqlBiEntity(methodCall.arguments().getFirst(), joinAlias);

        return switch (methodName) {
            case METHOD_EQUALS -> target + " = " + firstArg;
            case METHOD_EQUALS_IGNORE_CASE -> JPQL_UPPER + target + ") = " + JPQL_UPPER + firstArg + ")";
            case METHOD_STARTS_WITH -> target + " LIKE CONCAT(" + firstArg + LIKE_SUFFIX_PERCENT;
            case METHOD_ENDS_WITH -> target + LIKE_CONCAT_PREFIX + firstArg + ")";
            case METHOD_CONTAINS -> target + LIKE_CONCAT_PREFIX + firstArg + LIKE_SUFFIX_PERCENT;
            case METHOD_IS_EMPTY -> JPQL_LENGTH + target + ") = 0";
            case METHOD_TO_LOWER_CASE -> JPQL_LOWER + target + ")";
            case METHOD_TO_UPPER_CASE -> JPQL_UPPER + target + ")";
            case METHOD_TRIM -> JPQL_TRIM + target + ")";
            case METHOD_LENGTH -> JPQL_LENGTH + target + ")";
            case METHOD_INDEX_OF -> {
                if (methodCall.arguments().size() == 2) {
                    String fromArg = expressionToJpqlBiEntity(methodCall.arguments().get(1), joinAlias);
                    yield JPQL_LOCATE + firstArg + ", " + target + ", " + fromArg + JPQL_MINUS_ONE;
                }
                yield JPQL_LOCATE + firstArg + ", " + target + JPQL_MINUS_ONE;
            }
            // Date/Time methods
            case METHOD_GET_YEAR -> JPQL_YEAR + target + ")";
            case METHOD_GET_MONTH_VALUE -> JPQL_MONTH + target + ")";
            case METHOD_GET_DAY_OF_MONTH -> JPQL_DAY + target + ")";
            case METHOD_GET_HOUR -> JPQL_HOUR + target + ")";
            case METHOD_GET_MINUTE -> JPQL_MINUTE + target + ")";
            case METHOD_GET_SECOND -> JPQL_SECOND + target + ")";
            // Default: show as function call with target
            default -> methodName.toUpperCase() + "(" + target + ")";
        };
    }

    /** Generates WHERE clause predicate as JPQL (without "WHERE" keyword). */
    public static String predicateToJpql(LambdaExpression predicateExpression) {
        if (predicateExpression == null) {
            return null;
        }
        return expressionToJpql(predicateExpression);
    }

    /** Converts LambdaExpression to JPQL string. */
    private static String expressionToJpql(LambdaExpression expr) {
        if (expr == null) {
            return "?";
        }

        return switch (expr) {
            case BinaryOp binaryOp -> binaryOpToJpql(binaryOp);
            case UnaryOp unaryOp -> unaryOpToJpql(unaryOp);
            case FieldAccess fieldAccess -> ENTITY_ALIAS + "." + fieldAccess.fieldName();
            case PathExpression pathExpr -> pathExpressionToJpql(pathExpr);
            case Constant constant -> constantToJpql(constant);
            case CapturedVariable captured -> ":" + captured.displayName();
            case Parameter _ -> ENTITY_ALIAS;
            case NullLiteral _ -> "NULL";
            case MethodCall methodCall -> methodCallToJpql(methodCall);
            case InExpression inExpr -> inExpressionToJpql(inExpr);
            case MemberOfExpression memberOf -> memberOfToJpql(memberOf);
            case ConstructorCall ctorCall -> constructorCallToJpql(ctorCall);
            case ArrayCreation arrayCreation -> arrayCreationToJpql(arrayCreation);
            case Cast cast -> expressionToJpql(cast.expression());
            case InstanceOf instanceOf -> instanceOfToJpql(instanceOf);
            case Conditional conditional -> conditionalToJpql(conditional);

            // Bi-entity expressions (for joins)
            case BiEntityFieldAccess biField -> biEntityFieldToJpql(biField);
            case BiEntityPathExpression biPath -> biEntityPathToJpql(biPath);
            case BiEntityParameter biParam -> biEntityParamAlias(biParam);

            // Group expressions
            case GroupKeyReference _ -> "KEY";
            case GroupAggregation groupAgg -> groupAggregationToJpql(groupAgg);
            case GroupParameter _ -> "GROUP";

            // Subquery expressions
            case ScalarSubquery scalarSub -> scalarSubqueryToJpql(scalarSub);
            case ExistsSubquery existsSub -> existsSubqueryToJpql(existsSub);
            case InSubquery inSub -> inSubqueryToJpql(inSub);
            case CorrelatedVariable correlated -> "OUTER." + expressionToJpql(correlated.fieldExpression());
            case SubqueryBuilderReference _ -> "(SUBQUERY)";
            case MathFunction mathFunc -> mathFunctionToJpql(mathFunc);
        };
    }

    /** Gets first argument as JPQL, or "?" if none. */
    private static String firstArgOrPlaceholder(MethodCall methodCall) {
        return methodCall.arguments().isEmpty() ? "?" : expressionToJpql(methodCall.arguments().getFirst());
    }

    private static String binaryOpToJpql(BinaryOp binaryOp) {
        String left = expressionToJpql(binaryOp.left());
        String right = expressionToJpql(binaryOp.right());
        String op = operatorToJpql(binaryOp.operator());

        // Add parentheses for logical operators to preserve precedence
        if (isLogicalOperation(binaryOp)) {
            return "(" + left + " " + op + " " + right + ")";
        }

        return left + " " + op + " " + right;
    }

    private static String operatorToJpql(BinaryOp.Operator op) {
        return switch (op) {
            case EQ -> "=";
            case NE -> "<>";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case AND -> "AND";
            case OR -> "OR";
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "MOD";
        };
    }

    private static String unaryOpToJpql(UnaryOp unaryOp) {
        String operand = expressionToJpql(unaryOp.operand());
        return switch (unaryOp.operator()) {
            case NOT -> "NOT " + operand;
        };
    }

    private static String pathExpressionToJpql(PathExpression pathExpr) {
        return ENTITY_ALIAS + "." + pathExpr.toPath();
    }

    private static String constantToJpql(Constant constant) {
        return switch (constant.value()) {
            case null -> "NULL";
            case String s -> "'" + s.replace("'", "''") + "'"; // Escape single quotes per SQL/JPQL standard
            case Boolean b -> b.toString().toUpperCase();
            case Enum<?> e -> "'" + e.name() + "'";
            default -> String.valueOf(constant.value());
        };
    }

    private static String methodCallToJpql(MethodCall methodCall) {
        String methodName = methodCall.methodName();
        String target = methodCall.target() != null ? expressionToJpql(methodCall.target()) : "";

        // String methods
        return switch (methodName) {
            case METHOD_EQUALS -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + " = " + arg;
            }
            case METHOD_EQUALS_IGNORE_CASE -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield JPQL_UPPER + target + ") = " + JPQL_UPPER + arg + ")";
            }
            case METHOD_STARTS_WITH -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + " LIKE CONCAT(" + arg + LIKE_SUFFIX_PERCENT;
            }
            case METHOD_ENDS_WITH -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + LIKE_CONCAT_PREFIX + arg + ")";
            }
            case METHOD_CONTAINS -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + LIKE_CONCAT_PREFIX + arg + LIKE_SUFFIX_PERCENT;
            }
            case METHOD_IS_EMPTY -> JPQL_LENGTH + target + ") = 0";
            case "isBlank" -> JPQL_TRIM + target + ") = ''";
            case METHOD_TO_LOWER_CASE -> JPQL_LOWER + target + ")";
            case METHOD_TO_UPPER_CASE -> JPQL_UPPER + target + ")";
            case METHOD_TRIM -> JPQL_TRIM + target + ")";
            case METHOD_LENGTH -> JPQL_LENGTH + target + ")";
            case METHOD_SUBSTRING -> {
                if (methodCall.arguments().size() >= 2) {
                    String start = expressionToJpql(methodCall.arguments().getFirst());
                    String len = expressionToJpql(methodCall.arguments().get(1));
                    yield JPQL_SUBSTRING + target + ", " + start + ", " + len + ")";
                } else if (methodCall.arguments().size() == 1) {
                    String start = expressionToJpql(methodCall.arguments().getFirst());
                    yield JPQL_SUBSTRING + target + ", " + start + ")";
                }
                yield JPQL_SUBSTRING + target + ")";
            }
            case METHOD_INDEX_OF -> {
                String arg = firstArgOrPlaceholder(methodCall);
                if (methodCall.arguments().size() == 2) {
                    String fromArg = expressionToJpql(methodCall.arguments().get(1));
                    yield JPQL_LOCATE + arg + ", " + target + ", " + fromArg + JPQL_MINUS_ONE;
                }
                yield JPQL_LOCATE + arg + ", " + target + JPQL_MINUS_ONE;
            }
            case STRING_CONCAT -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield JPQL_CONCAT + target + ", " + arg + ")";
            }
            // Math methods
            case "abs" -> "ABS(" + target + ")";
            case "sqrt" -> "SQRT(" + target + ")";
            case "mod" -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield "MOD(" + target + ", " + arg + ")";
            }
            // Date/Time methods
            case METHOD_GET_YEAR, "year" -> JPQL_YEAR + target + ")";
            case "getMonth", METHOD_GET_MONTH_VALUE, "month" -> JPQL_MONTH + target + ")";
            case METHOD_GET_DAY_OF_MONTH, "day" -> JPQL_DAY + target + ")";
            case METHOD_GET_HOUR, "hour" -> JPQL_HOUR + target + ")";
            case METHOD_GET_MINUTE, "minute" -> JPQL_MINUTE + target + ")";
            case METHOD_GET_SECOND, "second" -> JPQL_SECOND + target + ")";
            // Collection size
            case "size" -> "SIZE(" + target + ")";
            // Default: show as function call
            default -> methodName.toUpperCase() + "(" + target + ")";
        };
    }

    private static String inExpressionToJpql(InExpression inExpr) {
        String field = expressionToJpql(inExpr.field());
        String collection = expressionToJpql(inExpr.collection());
        String op = inExpr.negated() ? "NOT IN" : "IN";
        return field + " " + op + " " + collection;
    }

    private static String memberOfToJpql(MemberOfExpression memberOf) {
        String value = expressionToJpql(memberOf.value());
        String collection = expressionToJpql(memberOf.collectionField());
        String op = memberOf.negated() ? "NOT MEMBER OF" : "MEMBER OF";
        return value + " " + op + " " + collection;
    }

    private static String constructorCallToJpql(ConstructorCall ctorCall) {
        String simpleName = ClassNameUtils.extractSimpleNameFromInternal(ctorCall.className());

        String args = ctorCall.arguments().stream()
                .map(JpqlGenerator::expressionToJpql)
                .collect(Collectors.joining(", "));

        return "NEW " + simpleName + "(" + args + ")";
    }

    private static String arrayCreationToJpql(ArrayCreation arrayCreation) {
        return arrayCreation.elements().stream()
                .map(JpqlGenerator::expressionToJpql)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String instanceOfToJpql(InstanceOf instanceOf) {
        String expr = expressionToJpql(instanceOf.expression());
        String typeName = instanceOf.targetType().getSimpleName();
        return "TYPE(" + expr + ") = " + typeName;
    }

    private static String conditionalToJpql(Conditional conditional) {
        String condition = expressionToJpql(conditional.condition());
        String trueVal = expressionToJpql(conditional.trueValue());
        String falseVal = expressionToJpql(conditional.falseValue());
        return "CASE WHEN " + condition + " THEN " + trueVal + " ELSE " + falseVal + " END";
    }

    private static String biEntityFieldToJpql(BiEntityFieldAccess biField) {
        String alias = biField.entityPosition().selectAlias("e1", "e2");
        return alias + "." + biField.fieldName();
    }

    private static String biEntityPathToJpql(BiEntityPathExpression biPath) {
        String alias = biPath.entityPosition().selectAlias("e1", "e2");
        return alias + "." + biPath.toPath();
    }

    private static String biEntityParamAlias(BiEntityParameter biParam) {
        return biParam.position().selectAlias("e1", "e2");
    }

    private static String groupAggregationToJpql(GroupAggregation groupAgg) {
        String field = groupAgg.fieldExpression() != null ? expressionToJpql(groupAgg.fieldExpression()) : ENTITY_ALIAS;

        return switch (groupAgg.aggregationType()) {
            case COUNT -> JPQL_COUNT + field + ")";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case AVG -> JPQL_AVG + field + ")";
            case SUM_INTEGER, SUM_LONG, SUM_DOUBLE -> JPQL_SUM + field + ")";
            case MIN -> JPQL_MIN + field + ")";
            case MAX -> JPQL_MAX + field + ")";
        };
    }

    private static String scalarSubqueryToJpql(ScalarSubquery scalarSub) {
        // Handle placeholder case: use entityClassName if available, otherwise entityClass
        String entityName = scalarSub.entityClassName() != null
                ? ClassNameUtils.extractSimpleName(scalarSub.entityClassName())
                : scalarSub.entityClass().getSimpleName();
        String field = scalarSub.fieldExpression() != null ? expressionToJpql(scalarSub.fieldExpression()) : "s";

        String aggFunc = switch (scalarSub.aggregationType()) {
            case AVG -> "AVG";
            case SUM -> "SUM";
            case MIN -> "MIN";
            case MAX -> "MAX";
            case COUNT -> "COUNT";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("(").append(JPQL_SELECT).append(aggFunc).append("(").append(field).append(")");
        sb.append(JPQL_FROM).append(entityName).append(" s");
        if (scalarSub.predicate() != null) {
            sb.append(JPQL_WHERE).append(expressionToJpql(scalarSub.predicate()));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String existsSubqueryToJpql(ExistsSubquery existsSub) {
        // Handle placeholder case: use entityClassName if available, otherwise entityClass
        String entityName = existsSub.entityClassName() != null
                ? ClassNameUtils.extractSimpleName(existsSub.entityClassName())
                : existsSub.entityClass().getSimpleName();
        String op = existsSub.negated() ? "NOT EXISTS" : "EXISTS";

        StringBuilder sb = new StringBuilder();
        sb.append(op).append(" (").append(JPQL_SELECT).append("s").append(JPQL_FROM).append(entityName).append(" s");
        sb.append(JPQL_WHERE).append(expressionToJpql(existsSub.predicate()));
        sb.append(")");
        return sb.toString();
    }

    private static String inSubqueryToJpql(InSubquery inSub) {
        String field = expressionToJpql(inSub.field());
        // Handle placeholder case: use entityClassName if available, otherwise entityClass
        String entityName = inSub.entityClassName() != null
                ? ClassNameUtils.extractSimpleName(inSub.entityClassName())
                : inSub.entityClass().getSimpleName();
        String selectExpr = expressionToJpql(inSub.selectExpression());
        String op = inSub.negated() ? "NOT IN" : "IN";

        StringBuilder sb = new StringBuilder();
        sb.append(field).append(" ").append(op).append(" (").append(JPQL_SELECT).append(selectExpr);
        sb.append(JPQL_FROM).append(entityName).append(" s");
        if (inSub.predicate() != null) {
            sb.append(JPQL_WHERE).append(expressionToJpql(inSub.predicate()));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String mathFunctionToJpql(MathFunction mathFunc) {
        String operand = expressionToJpql(mathFunc.operand());
        return switch (mathFunc.op()) {
            case ABS -> "ABS(" + operand + ")";
            case NEG -> "-(" + operand + ")";
            case SQRT -> "SQRT(" + operand + ")";
            case SIGN -> "SIGN(" + operand + ")";
            case CEILING -> "CEILING(" + operand + ")";
            case FLOOR -> "FLOOR(" + operand + ")";
            case EXP -> "EXP(" + operand + ")";
            case LN -> "LN(" + operand + ")";
            case POWER -> {
                String second = expressionToJpql(mathFunc.secondOperand());
                yield "POWER(" + operand + ", " + second + ")";
            }
            case ROUND -> {
                String second = expressionToJpql(mathFunc.secondOperand());
                yield "ROUND(" + operand + ", " + second + ")";
            }
        };
    }

}
