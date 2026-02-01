package io.quarkiverse.qubit.deployment.devui;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import io.quarkiverse.qubit.deployment.util.ClassNameUtils;
import io.quarkiverse.qubit.deployment.util.TypeConverter;

import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;

import java.util.stream.Collectors;

/** Generates Java-like source code from LambdaExpression AST for DevUI display. */
public final class JavaSourceGenerator {

    private static final String DEFAULT_PARAM = "p";
    private static final String GROUP_PARAM = "g";
    private static final String DOT_EQUALS_PREFIX = ".equals(";
    private static final String SUBQUERY_PREFIX = "subquery(";

    private JavaSourceGenerator() {
        // Utility class
    }

    /** Generates lambda expression string (e.g., "p -> p.age > 18"). */
    public static String generateJavaSource(LambdaExpression expression) {
        if (expression == null) {
            return null;
        }
        return DEFAULT_PARAM + " -> " + expressionToJava(expression, DEFAULT_PARAM);
    }

    /** Generates bi-entity lambda expression string for join queries. */
    public static String generateBiEntityJavaSource(LambdaExpression expression,
                                                     String firstParam,
                                                     String secondParam) {
        if (expression == null) {
            return null;
        }
        return "(" + firstParam + ", " + secondParam + ") -> " +
               biEntityExpressionToJava(expression, firstParam, secondParam);
    }

    /** Generates group lambda expression string (e.g., "g -> g.count() > 5"). */
    public static String generateGroupJavaSource(LambdaExpression expression) {
        if (expression == null) {
            return null;
        }
        return GROUP_PARAM + " -> " + expressionToJava(expression, GROUP_PARAM);
    }

    /** Generates expression body without lambda arrow. */
    public static String expressionBodyToJava(LambdaExpression expression) {
        if (expression == null) {
            return null;
        }
        return expressionToJava(expression, DEFAULT_PARAM);
    }

    /** Converts LambdaExpression to Java string. */
    private static String expressionToJava(LambdaExpression expr, String param) {
        if (expr == null) {
            return "?";
        }

        return switch (expr) {
            case BinaryOp binaryOp -> binaryOpToJava(binaryOp, param);
            case UnaryOp unaryOp -> unaryOpToJava(unaryOp, param);
            case FieldAccess fieldAccess -> param + "." + fieldAccess.fieldName();
            case PathExpression pathExpr -> pathExpressionToJava(pathExpr, param);
            case Constant constant -> constantToJava(constant);
            case CapturedVariable captured -> captured.displayName();
            case Parameter _ -> param;
            case NullLiteral _ -> "null";
            case MethodCall methodCall -> methodCallToJava(methodCall, param);
            case InExpression inExpr -> inExpressionToJava(inExpr, param);
            case MemberOfExpression memberOf -> memberOfToJava(memberOf, param);
            case ConstructorCall ctorCall -> constructorCallToJava(ctorCall, param);
            case ArrayCreation arrayCreation -> arrayCreationToJava(arrayCreation, param);
            case Cast cast -> castToJava(cast, param);
            case InstanceOf instanceOf -> instanceOfToJava(instanceOf, param);
            case Conditional conditional -> conditionalToJava(conditional, param);

            // Bi-entity expressions (use default param names when in single-entity context)
            case BiEntityFieldAccess biField -> biEntityFieldToJava(biField, "e1", "e2");
            case BiEntityPathExpression biPath -> biEntityPathToJava(biPath, "e1", "e2");
            case BiEntityParameter biParam -> biEntityParamToJava(biParam, "e1", "e2");

            // Group expressions
            case GroupKeyReference _ -> "g.key()";
            case GroupAggregation groupAgg -> groupAggregationToJava(groupAgg);
            case GroupParameter _ -> "g";

            // Subquery expressions
            case ScalarSubquery scalarSub -> scalarSubqueryToJava(scalarSub);
            case ExistsSubquery existsSub -> existsSubqueryToJava(existsSub);
            case InSubquery inSub -> inSubqueryToJava(inSub, param);
            case CorrelatedVariable correlated -> param + "." + expressionToJava(correlated.fieldExpression(), param);
            case SubqueryBuilderReference _ -> "subquery(...)";
        };
    }

    /** Converts bi-entity LambdaExpression to Java string. */
    private static String biEntityExpressionToJava(LambdaExpression expr,
                                                    String firstParam,
                                                    String secondParam) {
        if (expr == null) {
            return "?";
        }

        return switch (expr) {
            case BinaryOp binaryOp -> {
                String left = biEntityExpressionToJava(binaryOp.left(), firstParam, secondParam);
                String right = biEntityExpressionToJava(binaryOp.right(), firstParam, secondParam);
                String op = binaryOp.operator().symbol();
                if (isLogicalOperation(binaryOp)) {
                    yield "(" + left + " " + op + " " + right + ")";
                }
                // Convert string equality/inequality to .equals() format
                String stringEquality = formatStringEquality(binaryOp, left, right);
                if (stringEquality != null) {
                    yield stringEquality;
                }
                yield left + " " + op + " " + right;
            }
            case UnaryOp(var operator, var operand) -> operator.symbol() +
                    biEntityExpressionToJava(operand, firstParam, secondParam);
            case BiEntityFieldAccess biField -> biEntityFieldToJava(biField, firstParam, secondParam);
            case BiEntityPathExpression biPath -> biEntityPathToJava(biPath, firstParam, secondParam);
            case BiEntityParameter biParam -> biEntityParamToJava(biParam, firstParam, secondParam);
            case MethodCall methodCall -> biEntityMethodCallToJava(methodCall, firstParam, secondParam);
            case Constant constant -> constantToJava(constant);
            case CapturedVariable captured -> captured.displayName();
            case NullLiteral _ -> "null";
            default -> expressionToJava(expr, firstParam);
        };
    }

    private static String binaryOpToJava(BinaryOp binaryOp, String param) {
        // Simplify boolean field comparisons: "p.active == true" -> "p.active"
        String booleanSimplified = simplifyBooleanComparison(binaryOp, param);
        if (booleanSimplified != null) {
            return booleanSimplified;
        }

        String left = expressionToJava(binaryOp.left(), param);
        String right = expressionToJava(binaryOp.right(), param);
        String op = binaryOp.operator().symbol();

        // Add parentheses for logical operators
        if (isLogicalOperation(binaryOp)) {
            return "(" + left + " " + op + " " + right + ")";
        }

        // Convert string equality/inequality to .equals() format
        String stringEquality = formatStringEquality(binaryOp, left, right);
        if (stringEquality != null) {
            return stringEquality;
        }

        return left + " " + op + " " + right;
    }

    /** Simplifies "p.active == true" to "p.active"; returns null if not a boolean comparison. */
    private static String simplifyBooleanComparison(BinaryOp binaryOp, String param) {
        // Only handle EQ and NE operators
        if (binaryOp.operator() != BinaryOp.Operator.EQ && binaryOp.operator() != BinaryOp.Operator.NE) {
            return null;
        }

        // Check if comparing a boolean field/path to a constant boolean
        LambdaExpression fieldExpr = null;
        Boolean boolValue = null;

        if (isBooleanFieldOrPath(binaryOp.left()) && isBooleanConstant(binaryOp.right())) {
            fieldExpr = binaryOp.left();
            boolValue = getBooleanValue(binaryOp.right());
        } else if (isBooleanFieldOrPath(binaryOp.right()) && isBooleanConstant(binaryOp.left())) {
            fieldExpr = binaryOp.right();
            boolValue = getBooleanValue(binaryOp.left());
        }

        if (fieldExpr == null || boolValue == null) {
            return null;
        }

        String fieldStr = expressionToJava(fieldExpr, param);

        // EQ true -> field, EQ false -> !field
        // NE true -> !field, NE false -> field
        boolean needsNegation = (binaryOp.operator() == BinaryOp.Operator.EQ) != boolValue;
        return needsNegation ? "!" + fieldStr : fieldStr;
    }

    /** Checks if expression is a boolean field or path. */
    private static boolean isBooleanFieldOrPath(LambdaExpression expr) {
        return switch (expr) {
            case FieldAccess field -> TypeConverter.isBooleanType(field.fieldType());
            case PathExpression path -> TypeConverter.isBooleanType(path.resultType());
            default -> false;
        };
    }

    /** Checks if expression is a boolean constant. */
    private static boolean isBooleanConstant(LambdaExpression expr) {
        return expr instanceof Constant constant &&
               (constant == Constant.TRUE || constant == Constant.FALSE ||
                Boolean.TRUE.equals(constant.value()) || Boolean.FALSE.equals(constant.value()));
    }

    /** Gets boolean value from constant expression. */
    private static Boolean getBooleanValue(LambdaExpression expr) {
        if (expr instanceof Constant constant) {
            if (constant == Constant.TRUE) return true;
            if (constant == Constant.FALSE) return false;
            if (constant.value() instanceof Boolean b) return b;
        }
        return null;
    }

    /** Checks if expression is a String constant. */
    private static boolean isStringConstant(LambdaExpression expr) {
        return expr instanceof Constant constant && constant.value() instanceof String;
    }

    /** Formats string equality as .equals() call; returns null if not a string comparison. */
    private static String formatStringEquality(BinaryOp binaryOp, String left, String right) {
        if (binaryOp.operator() == BinaryOp.Operator.EQ) {
            if (isStringConstant(binaryOp.right())) {
                return left + DOT_EQUALS_PREFIX + right + ")";
            }
            if (isStringConstant(binaryOp.left())) {
                return right + DOT_EQUALS_PREFIX + left + ")";
            }
        }
        if (binaryOp.operator() == BinaryOp.Operator.NE) {
            if (isStringConstant(binaryOp.right())) {
                return "!" + left + DOT_EQUALS_PREFIX + right + ")";
            }
            if (isStringConstant(binaryOp.left())) {
                return "!" + right + DOT_EQUALS_PREFIX + left + ")";
            }
        }
        return null;
    }

    private static String unaryOpToJava(UnaryOp unaryOp, String param) {
        String operand = expressionToJava(unaryOp.operand(), param);
        return unaryOp.operator().symbol() + operand;
    }

    private static String pathExpressionToJava(PathExpression pathExpr, String param) {
        return param + "." + pathExpr.toPath();
    }

    private static String constantToJava(Constant constant) {
        return switch (constant.value()) {
            case null -> "null";
            case String s -> "\"" + escapeString(s) + "\"";
            case Character c -> "'" + c + "'";
            case Long l -> l + "L";
            case Float f -> f + "f";
            case Double d -> d + "d";
            case Enum<?> e -> e.getClass().getSimpleName() + "." + e.name();
            default -> String.valueOf(constant.value());
        };
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String methodCallToJava(MethodCall methodCall, String param) {
        String target = methodCall.target() != null ?
                expressionToJava(methodCall.target(), param) : "";
        String args = methodCall.arguments().stream()
                .map(arg -> expressionToJava(arg, param))
                .collect(Collectors.joining(", "));
        return target + "." + methodCall.methodName() + "(" + args + ")";
    }

    private static String biEntityMethodCallToJava(MethodCall methodCall,
                                                    String firstParam,
                                                    String secondParam) {
        String target = methodCall.target() != null ?
                biEntityExpressionToJava(methodCall.target(), firstParam, secondParam) : "";
        String args = methodCall.arguments().stream()
                .map(arg -> biEntityExpressionToJava(arg, firstParam, secondParam))
                .collect(Collectors.joining(", "));
        return target + "." + methodCall.methodName() + "(" + args + ")";
    }

    private static String inExpressionToJava(InExpression inExpr, String param) {
        String collection = expressionToJava(inExpr.collection(), param);
        String field = expressionToJava(inExpr.field(), param);
        String expr = collection + ".contains(" + field + ")";
        return inExpr.negated() ? "!" + expr : expr;
    }

    private static String memberOfToJava(MemberOfExpression memberOf, String param) {
        String collection = expressionToJava(memberOf.collectionField(), param);
        String value = expressionToJava(memberOf.value(), param);
        String expr = collection + ".contains(" + value + ")";
        return memberOf.negated() ? "!" + expr : expr;
    }

    private static String constructorCallToJava(ConstructorCall ctorCall, String param) {
        String simpleName = ClassNameUtils.extractSimpleNameFromInternal(ctorCall.className());

        String args = ctorCall.arguments().stream()
                .map(arg -> expressionToJava(arg, param))
                .collect(Collectors.joining(", "));

        return "new " + simpleName + "(" + args + ")";
    }

    private static String arrayCreationToJava(ArrayCreation arrayCreation, String param) {
        String elements = arrayCreation.elements().stream()
                .map(elem -> expressionToJava(elem, param))
                .collect(Collectors.joining(", "));
        return "new Object[]{" + elements + "}";
    }

    private static String castToJava(Cast cast, String param) {
        String typeName = cast.targetType().getSimpleName();
        String expr = expressionToJava(cast.expression(), param);
        return "((" + typeName + ") " + expr + ")";
    }

    private static String instanceOfToJava(InstanceOf instanceOf, String param) {
        String expr = expressionToJava(instanceOf.expression(), param);
        String typeName = instanceOf.targetType().getSimpleName();
        return expr + " instanceof " + typeName;
    }

    private static String conditionalToJava(Conditional conditional, String param) {
        String condition = expressionToJava(conditional.condition(), param);
        String trueVal = expressionToJava(conditional.trueValue(), param);
        String falseVal = expressionToJava(conditional.falseValue(), param);
        return "(" + condition + " ? " + trueVal + " : " + falseVal + ")";
    }

    private static String biEntityFieldToJava(BiEntityFieldAccess biField,
                                               String firstParam,
                                               String secondParam) {
        String alias = biField.entityPosition().selectAlias(firstParam, secondParam);
        return alias + "." + biField.fieldName();
    }

    private static String biEntityPathToJava(BiEntityPathExpression biPath,
                                              String firstParam,
                                              String secondParam) {
        String alias = biPath.entityPosition().selectAlias(firstParam, secondParam);
        return alias + "." + biPath.toPath();
    }

    private static String biEntityParamToJava(BiEntityParameter biParam,
                                               String firstParam,
                                               String secondParam) {
        return biParam.position().selectAlias(firstParam, secondParam);
    }

    private static String groupAggregationToJava(GroupAggregation groupAgg) {
        String field = groupAgg.fieldExpression() != null ?
                expressionToJava(groupAgg.fieldExpression(), "e") : "";

        return switch (groupAgg.aggregationType()) {
            case COUNT -> "g.count()";
            case COUNT_DISTINCT -> "g.countDistinct(" + field + ")";
            case AVG -> "g.avg(" + field + ")";
            case SUM_INTEGER -> "g.sumInteger(" + field + ")";
            case SUM_LONG -> "g.sumLong(" + field + ")";
            case SUM_DOUBLE -> "g.sumDouble(" + field + ")";
            case MIN -> "g.min(" + field + ")";
            case MAX -> "g.max(" + field + ")";
        };
    }

    private static String scalarSubqueryToJava(ScalarSubquery scalarSub) {
        // Handle placeholder case: use entityClassName if available, otherwise entityClass
        String entityName = scalarSub.entityClassName() != null
                ? ClassNameUtils.extractSimpleName(scalarSub.entityClassName())
                : scalarSub.entityClass().getSimpleName();
        String field = scalarSub.fieldExpression() != null ?
                expressionToJava(scalarSub.fieldExpression(), "s") : "";

        String aggMethod = switch (scalarSub.aggregationType()) {
            case AVG -> "avg";
            case SUM -> "sum";
            case MIN -> "min";
            case MAX -> "max";
            case COUNT -> "count";
        };

        StringBuilder sb = new StringBuilder();
        sb.append(SUBQUERY_PREFIX).append(entityName).append(".class)");
        if (scalarSub.predicate() != null) {
            sb.append(".where(s -> ").append(expressionToJava(scalarSub.predicate(), "s")).append(")");
        }
        if (scalarSub.fieldExpression() != null) {
            sb.append(".").append(aggMethod).append("(s -> ").append(field).append(")");
        } else {
            sb.append(".").append(aggMethod).append("()");
        }
        return sb.toString();
    }

    private static String existsSubqueryToJava(ExistsSubquery existsSub) {
        // Handle placeholder case: use entityClassName if available, otherwise entityClass
        String entityName = existsSub.entityClassName() != null
                ? ClassNameUtils.extractSimpleName(existsSub.entityClassName())
                : existsSub.entityClass().getSimpleName();
        String method = existsSub.negated() ? "notExists" : "exists";
        String predicate = expressionToJava(existsSub.predicate(), "s");

        return SUBQUERY_PREFIX + entityName + ".class)." + method + "(s -> " + predicate + ")";
    }

    private static String inSubqueryToJava(InSubquery inSub, String param) {
        String field = expressionToJava(inSub.field(), param);
        // Handle placeholder case: use entityClassName if available, otherwise entityClass
        String entityName = inSub.entityClassName() != null
                ? ClassNameUtils.extractSimpleName(inSub.entityClassName())
                : inSub.entityClass().getSimpleName();
        String selectExpr = expressionToJava(inSub.selectExpression(), "s");
        String method = inSub.negated() ? "notIn" : "in";

        StringBuilder sb = new StringBuilder();
        sb.append(SUBQUERY_PREFIX).append(entityName).append(".class)");
        sb.append(".").append(method).append("(").append(field);
        sb.append(", s -> ").append(selectExpr);
        if (inSub.predicate() != null) {
            sb.append(", s -> ").append(expressionToJava(inSub.predicate(), "s"));
        }
        sb.append(")");
        return sb.toString();
    }
}
