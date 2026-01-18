package io.quarkiverse.qubit.deployment.devui;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import io.quarkiverse.qubit.deployment.util.ClassNameUtils;

import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_CONTAINS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_ENDS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS_IGNORE_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_GET_DAY_OF_MONTH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_GET_HOUR;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_GET_MINUTE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_GET_MONTH_VALUE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_GET_SECOND;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_GET_YEAR;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_IS_EMPTY;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_LENGTH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_STARTS_WITH;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_SUBSTRING;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TO_LOWER_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TO_UPPER_CASE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_TRIM;
import static io.quarkiverse.qubit.runtime.QubitConstants.STRING_CONCAT;

import java.util.stream.Collectors;

/** Generates pseudo-JPQL strings from LambdaExpression AST for DevUI display. */
public final class JpqlGenerator {

    private static final String ENTITY_ALIAS = "e";

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
            jpql.append("SELECT COUNT(").append(ENTITY_ALIAS).append(")");
        } else if (projectionExpression != null) {
            jpql.append("SELECT ").append(expressionToJpql(projectionExpression));
        } else {
            jpql.append("SELECT ").append(ENTITY_ALIAS);
        }

        // FROM clause - extract simple name from fully qualified class name
        String entityName = ClassNameUtils.extractSimpleName(entityClassName);
        jpql.append(" FROM ").append(entityName).append(" ").append(ENTITY_ALIAS);

        // WHERE clause
        if (predicateExpression != null) {
            jpql.append(" WHERE ").append(expressionToJpql(predicateExpression));
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
            jpql.append("SELECT COUNT(").append(ENTITY_ALIAS).append(")");
        } else if (projectionExpression != null) {
            jpql.append("SELECT ").append(expressionToJpqlBiEntity(projectionExpression, joinAlias));
        } else {
            jpql.append("SELECT ").append(ENTITY_ALIAS);
        }

        // FROM clause
        String entityName = ClassNameUtils.extractSimpleName(entityClassName);
        jpql.append(" FROM ").append(entityName).append(" ").append(ENTITY_ALIAS);

        // JOIN clause
        String joinType = isLeftJoin ? "LEFT JOIN" : "JOIN";
        String joinPath = extractJoinPath(joinRelationshipExpression);
        jpql.append(" ").append(joinType).append(" ").append(ENTITY_ALIAS).append(".").append(joinPath)
            .append(" ").append(joinAlias);

        // WHERE clause
        if (predicateExpression != null) {
            jpql.append(" WHERE ").append(expressionToJpqlBiEntity(predicateExpression, joinAlias));
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
                        ? ENTITY_ALIAS : joinAlias;
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
                : expressionToJpqlBiEntity(methodCall.arguments().get(0), joinAlias);

        return switch (methodName) {
            case METHOD_EQUALS -> target + " = " + firstArg;
            case METHOD_EQUALS_IGNORE_CASE -> "UPPER(" + target + ") = UPPER(" + firstArg + ")";
            case METHOD_STARTS_WITH -> target + " LIKE CONCAT(" + firstArg + ", '%')";
            case METHOD_ENDS_WITH -> target + " LIKE CONCAT('%', " + firstArg + ")";
            case METHOD_CONTAINS -> target + " LIKE CONCAT('%', " + firstArg + ", '%')";
            case METHOD_IS_EMPTY -> "LENGTH(" + target + ") = 0";
            case METHOD_TO_LOWER_CASE -> "LOWER(" + target + ")";
            case METHOD_TO_UPPER_CASE -> "UPPER(" + target + ")";
            case METHOD_TRIM -> "TRIM(" + target + ")";
            case METHOD_LENGTH -> "LENGTH(" + target + ")";
            // Date/Time methods
            case METHOD_GET_YEAR -> "YEAR(" + target + ")";
            case METHOD_GET_MONTH_VALUE -> "MONTH(" + target + ")";
            case METHOD_GET_DAY_OF_MONTH -> "DAY(" + target + ")";
            case METHOD_GET_HOUR -> "HOUR(" + target + ")";
            case METHOD_GET_MINUTE -> "MINUTE(" + target + ")";
            case METHOD_GET_SECOND -> "SECOND(" + target + ")";
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
            case Parameter param -> ENTITY_ALIAS;
            case NullLiteral ignored -> "NULL";
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
            case GroupKeyReference keyRef -> "KEY";
            case GroupAggregation groupAgg -> groupAggregationToJpql(groupAgg);
            case GroupParameter ignored -> "GROUP";

            // Subquery expressions
            case ScalarSubquery scalarSub -> scalarSubqueryToJpql(scalarSub);
            case ExistsSubquery existsSub -> existsSubqueryToJpql(existsSub);
            case InSubquery inSub -> inSubqueryToJpql(inSub);
            case CorrelatedVariable correlated -> "OUTER." + expressionToJpql(correlated.fieldExpression());
            case SubqueryBuilderReference ignored -> "(SUBQUERY)";
        };
    }

    /** Gets first argument as JPQL, or "?" if none. */
    private static String firstArgOrPlaceholder(MethodCall methodCall) {
        return methodCall.arguments().isEmpty() ? "?" :
                expressionToJpql(methodCall.arguments().get(0));
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
        Object value = constant.value();
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String strValue) {
            // Escape single quotes for JPQL display (double them per SQL/JPQL standard)
            String escaped = strValue.replace("'", "''");
            return "'" + escaped + "'";
        }
        if (value instanceof Boolean) {
            return value.toString().toUpperCase();
        }
        if (value instanceof Enum<?> enumVal) {
            return "'" + enumVal.name() + "'";
        }
        return String.valueOf(value);
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
                yield "UPPER(" + target + ") = UPPER(" + arg + ")";
            }
            case METHOD_STARTS_WITH -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + " LIKE CONCAT(" + arg + ", '%')";
            }
            case METHOD_ENDS_WITH -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + " LIKE CONCAT('%', " + arg + ")";
            }
            case METHOD_CONTAINS -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield target + " LIKE CONCAT('%', " + arg + ", '%')";
            }
            case METHOD_IS_EMPTY -> "LENGTH(" + target + ") = 0";
            case "isBlank" -> "TRIM(" + target + ") = ''";
            case METHOD_TO_LOWER_CASE -> "LOWER(" + target + ")";
            case METHOD_TO_UPPER_CASE -> "UPPER(" + target + ")";
            case METHOD_TRIM -> "TRIM(" + target + ")";
            case METHOD_LENGTH -> "LENGTH(" + target + ")";
            case METHOD_SUBSTRING -> {
                if (methodCall.arguments().size() >= 2) {
                    String start = expressionToJpql(methodCall.arguments().get(0));
                    String len = expressionToJpql(methodCall.arguments().get(1));
                    yield "SUBSTRING(" + target + ", " + start + ", " + len + ")";
                } else if (methodCall.arguments().size() == 1) {
                    String start = expressionToJpql(methodCall.arguments().get(0));
                    yield "SUBSTRING(" + target + ", " + start + ")";
                }
                yield "SUBSTRING(" + target + ")";
            }
            case STRING_CONCAT -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield "CONCAT(" + target + ", " + arg + ")";
            }
            // Math methods
            case "abs" -> "ABS(" + target + ")";
            case "sqrt" -> "SQRT(" + target + ")";
            case "mod" -> {
                String arg = firstArgOrPlaceholder(methodCall);
                yield "MOD(" + target + ", " + arg + ")";
            }
            // Date/Time methods
            case METHOD_GET_YEAR, "year" -> "YEAR(" + target + ")";
            case "getMonth", METHOD_GET_MONTH_VALUE, "month" -> "MONTH(" + target + ")";
            case METHOD_GET_DAY_OF_MONTH, "day" -> "DAY(" + target + ")";
            case METHOD_GET_HOUR, "hour" -> "HOUR(" + target + ")";
            case METHOD_GET_MINUTE, "minute" -> "MINUTE(" + target + ")";
            case METHOD_GET_SECOND, "second" -> "SECOND(" + target + ")";
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
        String field = groupAgg.fieldExpression() != null ?
                expressionToJpql(groupAgg.fieldExpression()) : ENTITY_ALIAS;

        return switch (groupAgg.aggregationType()) {
            case COUNT -> "COUNT(" + field + ")";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case AVG -> "AVG(" + field + ")";
            case SUM_INTEGER, SUM_LONG, SUM_DOUBLE -> "SUM(" + field + ")";
            case MIN -> "MIN(" + field + ")";
            case MAX -> "MAX(" + field + ")";
        };
    }

    private static String scalarSubqueryToJpql(ScalarSubquery scalarSub) {
        String entityName = scalarSub.entityClass().getSimpleName();
        String field = scalarSub.fieldExpression() != null ?
                expressionToJpql(scalarSub.fieldExpression()) : "s";

        String aggFunc = switch (scalarSub.aggregationType()) {
            case AVG -> "AVG";
            case SUM -> "SUM";
            case MIN -> "MIN";
            case MAX -> "MAX";
            case COUNT -> "COUNT";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("(SELECT ").append(aggFunc).append("(").append(field).append(")");
        sb.append(" FROM ").append(entityName).append(" s");
        if (scalarSub.predicate() != null) {
            sb.append(" WHERE ").append(expressionToJpql(scalarSub.predicate()));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String existsSubqueryToJpql(ExistsSubquery existsSub) {
        String entityName = existsSub.entityClass().getSimpleName();
        String op = existsSub.negated() ? "NOT EXISTS" : "EXISTS";

        StringBuilder sb = new StringBuilder();
        sb.append(op).append(" (SELECT s FROM ").append(entityName).append(" s");
        sb.append(" WHERE ").append(expressionToJpql(existsSub.predicate()));
        sb.append(")");
        return sb.toString();
    }

    private static String inSubqueryToJpql(InSubquery inSub) {
        String field = expressionToJpql(inSub.field());
        String entityName = inSub.entityClass().getSimpleName();
        String selectExpr = expressionToJpql(inSub.selectExpression());
        String op = inSub.negated() ? "NOT IN" : "IN";

        StringBuilder sb = new StringBuilder();
        sb.append(field).append(" ").append(op).append(" (SELECT ").append(selectExpr);
        sb.append(" FROM ").append(entityName).append(" s");
        if (inSub.predicate() != null) {
            sb.append(" WHERE ").append(expressionToJpql(inSub.predicate()));
        }
        sb.append(")");
        return sb.toString();
    }

}
