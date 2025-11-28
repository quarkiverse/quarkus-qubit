package io.quarkus.qusaq.deployment.generation.builders;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.ExistsSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.FieldAccess;
import io.quarkus.qusaq.deployment.LambdaExpression.InSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.PathExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.PathSegment;
import io.quarkus.qusaq.deployment.LambdaExpression.ScalarSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.SubqueryAggregationType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.jboss.logging.Logger;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.OR;
import static io.quarkus.qusaq.runtime.QusaqConstants.CB_NOT;
import static io.quarkus.qusaq.runtime.QusaqConstants.PATH_GET;

/**
 * Generates JPA Criteria API bytecode for subquery expressions.
 *
 * <p>Iteration 8: Subqueries - handles ScalarSubquery, ExistsSubquery, and InSubquery.
 *
 * <p>Architecture:
 * <ul>
 *   <li>ScalarSubquery → JPA Subquery with aggregation function</li>
 *   <li>ExistsSubquery → cb.exists(subquery) or cb.not(cb.exists(subquery))</li>
 *   <li>InSubquery → path.in(subquery) or cb.not(path.in(subquery))</li>
 * </ul>
 *
 * <p>Example generated code for ScalarSubquery:
 * <pre>
 * // p.salary > subquery(Person.class).avg(q -> q.salary)
 * Subquery&lt;Double&gt; avgSub = query.subquery(Double.class);
 * Root&lt;Person&gt; subRoot = avgSub.from(Person.class);
 * avgSub.select(cb.avg(subRoot.get("salary")));
 * // Used in: cb.greaterThan(root.get("salary"), avgSub)
 * </pre>
 */
public class SubqueryExpressionBuilder {

    private static final Logger log = Logger.getLogger(SubqueryExpressionBuilder.class);

    /**
     * Loads entity class for JPA FROM clause.
     * Handles both direct class references and placeholder class names.
     *
     * @param method the method creator for bytecode generation
     * @param entityClass the entity class (may be Object.class for placeholders)
     * @param entityClassName optional entity class name (for placeholders)
     * @return ResultHandle for the entity class
     */
    private ResultHandle loadEntityClass(MethodCreator method, Class<?> entityClass, String entityClassName) {
        if (entityClassName != null) {
            // Placeholder case: Load class by name at runtime
            // Generates: Class.forName("io.quarkus.qusaq.it.Person")
            ResultHandle classNameHandle = method.load(entityClassName);
            return method.invokeStaticMethod(
                MethodDescriptor.ofMethod(Class.class, "forName", Class.class, String.class),
                classNameHandle
            );
        } else {
            // Normal case: Direct class reference
            // Generates: Person.class
            return method.loadClass(entityClass);
        }
    }

    /**
     * Generates JPA scalar aggregation subquery.
     *
     * <p>Creates a subquery that returns a single aggregated value (AVG, SUM, MIN, MAX, COUNT).
     *
     * @param method the method creator for bytecode generation
     * @param scalar the scalar subquery expression
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle (needed to create subqueries)
     * @param outerRoot the outer query's root handle (for correlated subqueries)
     * @param capturedValues the captured variables array handle
     * @return the JPA Subquery handle
     */
    public ResultHandle buildScalarSubquery(
            MethodCreator method,
            ScalarSubquery scalar,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Determine the subquery result type based on aggregation
        Class<?> resultType = getAggregationResultType(scalar.aggregationType());

        // Create subquery: query.subquery(resultType)
        ResultHandle resultTypeHandle = method.loadClass(resultType);
        ResultHandle subquery = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class),
                query, resultTypeHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle entityClassHandle = loadEntityClass(method, scalar.entityClass(), scalar.entityClassName());
        ResultHandle subRoot = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class),
                subquery, entityClassHandle);

        // Generate the select expression with aggregation
        ResultHandle aggregation = generateAggregation(method, scalar, cb, subRoot);

        // Set the select: subquery.select(aggregation)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class),
                subquery, aggregation);

        // Generate the where clause if there's a predicate
        if (scalar.hasPredicate()) {
            ResultHandle wherePredicate = generateSubqueryPredicate(
                    method, scalar.predicate(), cb, subRoot, outerRoot, capturedValues);
            if (wherePredicate != null) {
                applySubqueryWhere(method, subquery, wherePredicate);
            }
        }

        return subquery;
    }

    /**
     * Generates JPA EXISTS subquery predicate.
     *
     * <p>Creates cb.exists(subquery) or cb.not(cb.exists(subquery)).
     *
     * @param method the method creator for bytecode generation
     * @param exists the EXISTS subquery expression
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle
     * @param outerRoot the outer query's root handle (for correlation)
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle
     */
    public ResultHandle buildExistsSubquery(
            MethodCreator method,
            ExistsSubquery exists,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Create subquery: query.subquery(entityClass)
        ResultHandle entityClassHandle = loadEntityClass(method, exists.entityClass(), exists.entityClassName());
        ResultHandle subquery = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class),
                query, entityClassHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle subRoot = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class),
                subquery, entityClassHandle);

        // Select the subquery root (EXISTS just needs any selection)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class),
                subquery, subRoot);

        // Generate the where clause from the predicate
        ResultHandle wherePredicate = generateSubqueryPredicate(
                method, exists.predicate(), cb, subRoot, outerRoot, capturedValues);
        if (wherePredicate != null) {
            applySubqueryWhere(method, subquery, wherePredicate);
        }

        // Generate cb.exists(subquery)
        ResultHandle existsPredicate = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaBuilder.class, "exists", Predicate.class, Subquery.class),
                cb, subquery);

        // If negated, wrap with cb.not()
        if (exists.negated()) {
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class),
                    cb, existsPredicate);
        }

        return existsPredicate;
    }

    /**
     * Generates JPA IN subquery predicate.
     *
     * <p>Creates path.in(subquery) or cb.not(path.in(subquery)).
     *
     * @param method the method creator for bytecode generation
     * @param inSubquery the IN subquery expression
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle
     * @param outerRoot the outer query's root handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle
     */
    public ResultHandle buildInSubquery(
            MethodCreator method,
            InSubquery inSubquery,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Generate the left-hand field expression from the outer query
        ResultHandle fieldPath = generateFieldPath(method, inSubquery.field(), outerRoot);

        // Determine the result type from the select expression
        Class<?> selectType = inferExpressionType(inSubquery.selectExpression());

        // Create subquery: query.subquery(selectType)
        ResultHandle selectTypeHandle = method.loadClass(selectType);
        ResultHandle subquery = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class),
                query, selectTypeHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle entityClassHandle = loadEntityClass(method, inSubquery.entityClass(), inSubquery.entityClassName());
        ResultHandle subRoot = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class),
                subquery, entityClassHandle);

        // Generate the select expression
        ResultHandle selectExpr = generateFieldPath(method, inSubquery.selectExpression(), subRoot);

        // Set the select: subquery.select(selectExpr)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class),
                subquery, selectExpr);

        // Generate the where clause if there's a predicate
        if (inSubquery.hasPredicate()) {
            ResultHandle wherePredicate = generateSubqueryPredicate(
                    method, inSubquery.predicate(), cb, subRoot, outerRoot, capturedValues);
            if (wherePredicate != null) {
                applySubqueryWhere(method, subquery, wherePredicate);
            }
        }

        // Generate fieldPath.in(subquery)
        ResultHandle inPredicate = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Expression.class, "in", Predicate.class, Expression.class),
                fieldPath, subquery);

        // If negated, wrap with cb.not()
        if (inSubquery.negated()) {
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class),
                    cb, inPredicate);
        }

        return inPredicate;
    }

    /**
     * Returns the JPA result type for an aggregation.
     */
    private Class<?> getAggregationResultType(SubqueryAggregationType aggType) {
        return switch (aggType) {
            case AVG -> Double.class;
            case SUM -> Number.class;  // Could be Integer, Long, Double, etc.
            case MIN, MAX -> Comparable.class;
            case COUNT -> Long.class;
        };
    }

    /**
     * Generates the aggregation function call for a scalar subquery.
     */
    private ResultHandle generateAggregation(
            MethodCreator method,
            ScalarSubquery scalar,
            ResultHandle cb,
            ResultHandle subRoot) {

        // For COUNT, we don't need a field expression
        if (scalar.isCount()) {
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "count", Expression.class, Expression.class),
                    cb, subRoot);
        }

        // Generate the field path from the field expression
        ResultHandle fieldPath = generateFieldPath(method, scalar.fieldExpression(), subRoot);

        // Generate the appropriate aggregation
        return switch (scalar.aggregationType()) {
            case AVG -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "avg", Expression.class, Expression.class),
                    cb, fieldPath);
            case SUM -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "sum", Expression.class, Expression.class),
                    cb, fieldPath);
            case MIN -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "min", Expression.class, Expression.class),
                    cb, fieldPath);
            case MAX -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "max", Expression.class, Expression.class),
                    cb, fieldPath);
            case COUNT -> throw new IllegalStateException("COUNT should be handled above");
        };
    }

    /**
     * Generates a field path expression.
     *
     * @param method the method creator for bytecode generation
     * @param expr the expression to generate a path for (must be FieldAccess or PathExpression)
     * @param root the root handle to build the path from
     * @return ResultHandle for the generated path expression
     * @throws IllegalArgumentException if expr is null or an unsupported expression type
     */
    private ResultHandle generateFieldPath(MethodCreator method, LambdaExpression expr, ResultHandle root) {
        if (expr == null) {
            throw new IllegalArgumentException("Field path expression cannot be null");
        }
        if (expr instanceof FieldAccess field) {
            ResultHandle fieldName = method.load(field.fieldName());
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(Path.class, PATH_GET, Path.class, String.class),
                    root, fieldName);
        } else if (expr instanceof PathExpression pathExpr) {
            ResultHandle currentPath = root;
            for (PathSegment segment : pathExpr.segments()) {
                ResultHandle fieldName = method.load(segment.fieldName());
                currentPath = method.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(Path.class, PATH_GET, Path.class, String.class),
                        currentPath, fieldName);
            }
            return currentPath;
        }
        throw new IllegalArgumentException(
                "Unsupported expression type for field path generation: " + expr.getClass().getSimpleName()
                        + ". Expected FieldAccess or PathExpression.");
    }

    /**
     * Generates a predicate for the subquery's WHERE clause.
     *
     * <p>This handles both simple predicates (using only subquery root) and
     * correlated predicates (referencing the outer query's root).
     */
    private ResultHandle generateSubqueryPredicate(
            MethodCreator method,
            LambdaExpression predicate,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (predicate == null) {
            return null;
        }

        // Handle BinaryOp predicates (most common case)
        if (predicate instanceof LambdaExpression.BinaryOp binOp) {
            return generateBinaryOpPredicate(method, binOp, cb, subRoot, outerRoot, capturedValues);
        }

        // Handle FieldAccess as boolean
        if (predicate instanceof FieldAccess field) {
            ResultHandle path = generateFieldPath(method, field, subRoot);
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "isTrue", Predicate.class, Expression.class),
                    cb, path);
        }

        return null;
    }

    /**
     * Generates a binary operation predicate for subquery WHERE clause.
     */
    private ResultHandle generateBinaryOpPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Handle logical operators (AND/OR) differently - they need predicates
        if (binOp.operator() == AND ||binOp.operator() == OR) {
            // Recursively generate predicates for both sides
            ResultHandle leftPredicate = generateSubqueryPredicate(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
            ResultHandle rightPredicate = generateSubqueryPredicate(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

            ResultHandle predicateArray = method.newArray(Predicate.class, 2);
            method.writeArrayValue(predicateArray, 0, leftPredicate);
            method.writeArrayValue(predicateArray, 1, rightPredicate);

            String methodName = binOp.operator() == AND ? "and" : "or";
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, methodName, Predicate.class, Predicate[].class),
                    cb, predicateArray);
        }

        // For comparison operators, generate expressions
        ResultHandle left = generateSubqueryExpression(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        ResultHandle right = generateSubqueryExpression(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate comparison
        return switch (binOp.operator()) {
            case EQ -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "equal", Predicate.class, Expression.class, Object.class),
                    cb, left, right);
            case NE -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "notEqual", Predicate.class, Expression.class, Object.class),
                    cb, left, right);
            case GT -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThan", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            case GE -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThanOrEqualTo", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            case LT -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThan", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            case LE -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThanOrEqualTo", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            default -> null;
        };
    }

    /**
     * Generates an expression for use in subquery predicates.
     *
     * <p>This handles both subquery-local expressions and correlated references
     * to the outer query.
     *
     * @param method the method creator for bytecode generation
     * @param expr the expression to generate
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle (for correlated subqueries)
     * @param capturedValues the captured values array handle
     * @return ResultHandle for the generated expression, or null if expr is null
     */
    private ResultHandle generateSubqueryExpression(
            MethodCreator method,
            LambdaExpression expr,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (expr == null) {
            return null;
        }

        if (expr instanceof FieldAccess field) {
            // Default to subquery root for simple field access
            return generateFieldPath(method, field, subRoot);
        } else if (expr instanceof PathExpression pathExpr) {
            return generateFieldPath(method, pathExpr, subRoot);
        } else if (expr instanceof LambdaExpression.CorrelatedVariable correlated) {
            // Correlated reference - use the outer root
            return generateFieldPath(method, correlated.fieldExpression(), outerRoot);
        } else if (expr instanceof LambdaExpression.Constant constant) {
            return generateConstant(method, constant);
        } else if (expr instanceof LambdaExpression.CapturedVariable capturedVar) {
            ResultHandle index = method.load(capturedVar.index());
            ResultHandle value = method.readArrayValue(capturedValues, index);
            return method.checkCast(value, Object.class);
        }

        log.warnf("Unhandled expression type in generateSubqueryExpression: %s. "
                + "This may indicate a missing case handler or an unexpected AST structure.",
                expr.getClass().getSimpleName());
        return null;
    }

    /**
     * Generates a constant value.
     */
    private ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        Object value = constant.value();
        if (value == null) {
            return method.loadNull();
        } else if (value instanceof String s) {
            return method.load(s);
        } else if (value instanceof Integer i) {
            return method.load(i);
        } else if (value instanceof Long l) {
            return method.load(l);
        } else if (value instanceof Boolean b) {
            return method.load(b);
        } else if (value instanceof Double d) {
            return method.load(d);
        } else if (value instanceof Float f) {
            return method.load(f);
        }
        return method.loadNull();
    }

    /**
     * Infers the type of an expression for subquery result type.
     */
    private Class<?> inferExpressionType(LambdaExpression expr) {
        if (expr instanceof FieldAccess field) {
            return field.fieldType();
        } else if (expr instanceof PathExpression pathExpr) {
            return pathExpr.resultType();
        }
        return Object.class;
    }

    /**
     * Applies a WHERE predicate to a subquery using the correct JPA API.
     * <p>
     * JPA's Subquery.where() method takes Predicate... (varargs), so we need
     * to create an array for the invocation.
     */
    private void applySubqueryWhere(MethodCreator method, ResultHandle subquery, ResultHandle predicate) {
        // Create a Predicate[] array with a single element
        ResultHandle predicateArray = method.newArray(Predicate.class, 1);
        method.writeArrayValue(predicateArray, 0, predicate);

        // Call subquery.where(predicateArray)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "where", Subquery.class, Predicate[].class),
                subquery, predicateArray);
    }
}
