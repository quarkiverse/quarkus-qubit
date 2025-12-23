package io.quarkiverse.qubit.deployment.generation;

import java.util.Collection;

import io.quarkus.gizmo.MethodDescriptor;
import jakarta.persistence.criteria.*;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaFunction;

/**
 * Cached {@link MethodDescriptor} constants for common JPA Criteria API and JDK methods.
 *
 * <p>Constants are organized by category:
 * <ul>
 *   <li><strong>Boxed Type Unboxing</strong> - Integer.intValue(), Boolean.booleanValue(), etc.</li>
 *   <li><strong>Class Operations</strong> - Class.forName()</li>
 *   <li><strong>Path Navigation</strong> - Path.get()</li>
 *   <li><strong>Subquery Operations</strong> - CriteriaQuery.subquery(), Subquery.from(), etc.</li>
 *   <li><strong>CriteriaBuilder Comparison</strong> - equal, notEqual, greaterThan, etc.</li>
 *   <li><strong>CriteriaBuilder Aggregation</strong> - count, avg, sum, min, max</li>
 *   <li><strong>CriteriaBuilder Logic</strong> - and, or, not, exists, isTrue</li>
 *   <li><strong>Expression Operations</strong> - Expression.in()</li>
 * </ul>
 *
 * @see io.quarkus.gizmo.MethodDescriptor
 */
public final class MethodDescriptors {

    private MethodDescriptors() {
        // Utility class - prevent instantiation
    }

    // =========================================================================
    // Boxed Type Unboxing Methods
    // =========================================================================

    /**
     * {@code Integer.intValue()} - unbox Integer to int.
     */
    public static final MethodDescriptor INTEGER_INT_VALUE =
            MethodDescriptor.ofMethod(Integer.class, "intValue", int.class);

    /**
     * {@code Boolean.booleanValue()} - unbox Boolean to boolean.
     */
    public static final MethodDescriptor BOOLEAN_BOOLEAN_VALUE =
            MethodDescriptor.ofMethod(Boolean.class, "booleanValue", boolean.class);

    /**
     * {@code Long.longValue()} - unbox Long to long.
     */
    public static final MethodDescriptor LONG_LONG_VALUE =
            MethodDescriptor.ofMethod(Long.class, "longValue", long.class);

    // =========================================================================
    // Class Operations
    // =========================================================================

    /**
     * {@code Class.forName(String)} - load class by name at runtime.
     */
    public static final MethodDescriptor CLASS_FOR_NAME =
            MethodDescriptor.ofMethod(Class.class, "forName", Class.class, String.class);

    // =========================================================================
    // Path Navigation Methods
    // =========================================================================

    /**
     * {@code Path.get(String)} - navigate to a field path.
     */
    public static final MethodDescriptor PATH_GET =
            MethodDescriptor.ofMethod(Path.class, "get", Path.class, String.class);

    // =========================================================================
    // Subquery Operations
    // =========================================================================

    /**
     * {@code CriteriaQuery.subquery(Class)} - create a subquery.
     */
    public static final MethodDescriptor CRITERIA_QUERY_SUBQUERY =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class);

    /**
     * {@code Subquery.from(Class)} - create the FROM clause of a subquery.
     */
    public static final MethodDescriptor SUBQUERY_FROM =
            MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class);

    /**
     * {@code Subquery.select(Expression)} - set the SELECT clause of a subquery.
     */
    public static final MethodDescriptor SUBQUERY_SELECT =
            MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class);

    /**
     * {@code Subquery.where(Predicate...)} - set the WHERE clause of a subquery.
     */
    public static final MethodDescriptor SUBQUERY_WHERE =
            MethodDescriptor.ofMethod(Subquery.class, "where", Subquery.class, Predicate[].class);

    // =========================================================================
    // CriteriaBuilder Comparison Methods
    // =========================================================================

    /**
     * {@code CriteriaBuilder.equal(Expression, Object)} - equality comparison.
     */
    public static final MethodDescriptor CB_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "equal", Predicate.class, Expression.class, Object.class);

    /**
     * {@code CriteriaBuilder.notEqual(Expression, Object)} - inequality comparison.
     */
    public static final MethodDescriptor CB_NOT_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "notEqual", Predicate.class, Expression.class, Object.class);

    /**
     * {@code CriteriaBuilder.greaterThan(Expression, Comparable)} - greater than comparison.
     */
    public static final MethodDescriptor CB_GREATER_THAN =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThan", Predicate.class, Expression.class, Comparable.class);

    /**
     * {@code CriteriaBuilder.greaterThanOrEqualTo(Expression, Comparable)} - greater than or equal comparison.
     */
    public static final MethodDescriptor CB_GREATER_THAN_OR_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThanOrEqualTo", Predicate.class, Expression.class, Comparable.class);

    /**
     * {@code CriteriaBuilder.lessThan(Expression, Comparable)} - less than comparison.
     */
    public static final MethodDescriptor CB_LESS_THAN =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThan", Predicate.class, Expression.class, Comparable.class);

    /**
     * {@code CriteriaBuilder.lessThanOrEqualTo(Expression, Comparable)} - less than or equal comparison.
     */
    public static final MethodDescriptor CB_LESS_THAN_OR_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThanOrEqualTo", Predicate.class, Expression.class, Comparable.class);

    // =========================================================================
    // CriteriaBuilder Aggregation Methods
    // =========================================================================

    /**
     * {@code CriteriaBuilder.count(Expression)} - count aggregation.
     */
    public static final MethodDescriptor CB_COUNT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "count", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.avg(Expression)} - average aggregation.
     */
    public static final MethodDescriptor CB_AVG =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "avg", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.sum(Expression)} - sum aggregation.
     */
    public static final MethodDescriptor CB_SUM =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "sum", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.sum(Expression, Expression)} - binary arithmetic addition.
     */
    public static final MethodDescriptor CB_SUM_BINARY =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "sum", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.min(Expression)} - minimum aggregation.
     */
    public static final MethodDescriptor CB_MIN =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "min", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.max(Expression)} - maximum aggregation.
     */
    public static final MethodDescriptor CB_MAX =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "max", Expression.class, Expression.class);

    // =========================================================================
    // CriteriaBuilder Logic Methods
    // =========================================================================

    /**
     * {@code CriteriaBuilder.not(Expression)} - logical NOT.
     */
    public static final MethodDescriptor CB_NOT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "not", Predicate.class, Expression.class);

    /**
     * {@code CriteriaBuilder.exists(Subquery)} - EXISTS predicate.
     */
    public static final MethodDescriptor CB_EXISTS =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "exists", Predicate.class, Subquery.class);

    /**
     * {@code CriteriaBuilder.isTrue(Expression)} - boolean IS TRUE.
     */
    public static final MethodDescriptor CB_IS_TRUE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isTrue", Predicate.class, Expression.class);

    /**
     * {@code CriteriaBuilder.isFalse(Expression)} - boolean IS FALSE.
     */
    public static final MethodDescriptor CB_IS_FALSE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isFalse", Predicate.class, Expression.class);

    /**
     * {@code CriteriaBuilder.isNull(Expression)} - null check.
     */
    public static final MethodDescriptor CB_IS_NULL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isNull", Predicate.class, Expression.class);

    /**
     * {@code CriteriaBuilder.isNotNull(Expression)} - not null check.
     */
    public static final MethodDescriptor CB_IS_NOT_NULL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isNotNull", Predicate.class, Expression.class);

    /**
     * {@code CriteriaBuilder.and(Predicate...)} - logical AND with varargs.
     */
    public static final MethodDescriptor CB_AND =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "and", Predicate.class, Predicate[].class);

    /**
     * {@code CriteriaBuilder.or(Predicate...)} - logical OR with varargs.
     */
    public static final MethodDescriptor CB_OR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "or", Predicate.class, Predicate[].class);

    // =========================================================================
    // Expression Methods
    // =========================================================================

    /**
     * {@code Expression.in(Expression)} - IN predicate with subquery.
     */
    public static final MethodDescriptor EXPRESSION_IN =
            MethodDescriptor.ofMethod(Expression.class, "in", Predicate.class, Expression.class);

    // =========================================================================
    // CriteriaBuilder String Methods (used in StringExpressionBuilder)
    // =========================================================================

    /**
     * {@code CriteriaBuilder.lower(Expression)} - lowercase transformation.
     */
    public static final MethodDescriptor CB_LOWER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lower", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.upper(Expression)} - uppercase transformation.
     */
    public static final MethodDescriptor CB_UPPER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "upper", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.trim(Expression)} - trim whitespace.
     */
    public static final MethodDescriptor CB_TRIM =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "trim", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.length(Expression)} - string length.
     */
    public static final MethodDescriptor CB_LENGTH =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "length", Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.like(Expression, Expression)} - LIKE pattern matching.
     */
    public static final MethodDescriptor CB_LIKE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "like", Predicate.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.concat(Expression, Expression)} - string concatenation with two expressions.
     */
    public static final MethodDescriptor CB_CONCAT_EXPR_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "concat", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.concat(String, Expression)} - string concatenation with prefix.
     */
    public static final MethodDescriptor CB_CONCAT_STR_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "concat", Expression.class, String.class, Expression.class);

    /**
     * {@code CriteriaBuilder.concat(Expression, String)} - string concatenation with suffix.
     */
    public static final MethodDescriptor CB_CONCAT_EXPR_STR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "concat", Expression.class, Expression.class, String.class);

    /**
     * {@code CriteriaBuilder.substring(Expression, Expression)} - substring from start position.
     */
    public static final MethodDescriptor CB_SUBSTRING_2 =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "substring", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.substring(Expression, Expression, Expression)} - substring with start and length.
     */
    public static final MethodDescriptor CB_SUBSTRING_3 =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "substring", Expression.class, Expression.class, Expression.class, Expression.class);

    // =========================================================================
    // CriteriaBuilder Temporal Methods (used in TemporalExpressionBuilder)
    // =========================================================================

    /**
     * {@code CriteriaBuilder.function(String, Class, Expression...)} - SQL function call.
     */
    public static final MethodDescriptor CB_FUNCTION =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "function", Expression.class, String.class, Class.class, Expression[].class);

    // =========================================================================
    // CriteriaBuilder Arithmetic Methods
    // =========================================================================

    /**
     * {@code CriteriaBuilder.prod(Expression, Expression)} - multiplication.
     */
    public static final MethodDescriptor CB_PROD =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "prod", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.quot(Expression, Expression)} - division.
     */
    public static final MethodDescriptor CB_QUOT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "quot", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.diff(Expression, Expression)} - subtraction.
     */
    public static final MethodDescriptor CB_DIFF =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "diff", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.mod(Expression, Expression)} - modulo.
     */
    public static final MethodDescriptor CB_MOD =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "mod", Expression.class, Expression.class, Expression.class);

    /**
     * {@code CriteriaBuilder.literal(Object)} - create a literal expression.
     */
    public static final MethodDescriptor CB_LITERAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "literal", Expression.class, Object.class);

    // =========================================================================
    // CriteriaBuilder Collection Methods
    // =========================================================================

    /**
     * {@code CriteriaBuilder.isMember(Object, Expression)} - collection membership.
     */
    public static final MethodDescriptor CB_IS_MEMBER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isMember", Predicate.class, Object.class, Expression.class);

    /**
     * {@code CriteriaBuilder.isNotMember(Object, Expression)} - collection non-membership.
     */
    public static final MethodDescriptor CB_IS_NOT_MEMBER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isNotMember", Predicate.class, Object.class, Expression.class);

    /**
     * {@code Expression.in(Collection)} - IN predicate with collection.
     */
    public static final MethodDescriptor EXPRESSION_IN_COLLECTION =
            MethodDescriptor.ofMethod(Expression.class, "in", Predicate.class, Collection.class);

    // =========================================================================
    // CriteriaQuery Methods
    // =========================================================================

    /**
     * {@code CriteriaBuilder.tuple(Selection...)} - create tuple selection.
     */
    public static final MethodDescriptor CB_TUPLE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "tuple", CompoundSelection.class, Selection[].class);

    /**
     * {@code CriteriaBuilder.construct(Class, Selection...)} - create constructor expression.
     */
    public static final MethodDescriptor CB_CONSTRUCT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "construct", CompoundSelection.class, Class.class, Selection[].class);

    // =========================================================================
    // HibernateCriteriaBuilder Temporal Extraction Methods (Database-Agnostic)
    // =========================================================================
    // These methods are provided by HibernateCriteriaBuilder and generate
    // database-agnostic SQL. For example:
    // - PostgreSQL: EXTRACT(YEAR FROM ...)
    // - MySQL: YEAR(...)
    // - H2: EXTRACT(YEAR FROM ...)

    /**
     * {@code HibernateCriteriaBuilder.year(Expression)} - extract year from temporal expression.
     * Returns JpaFunction&lt;Integer&gt;.
     */
    public static final MethodDescriptor HCB_YEAR =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "year", JpaFunction.class, Expression.class);

    /**
     * {@code HibernateCriteriaBuilder.month(Expression)} - extract month from temporal expression.
     * Returns JpaFunction&lt;Integer&gt;.
     */
    public static final MethodDescriptor HCB_MONTH =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "month", JpaFunction.class, Expression.class);

    /**
     * {@code HibernateCriteriaBuilder.day(Expression)} - extract day from temporal expression.
     * Returns JpaFunction&lt;Integer&gt;.
     */
    public static final MethodDescriptor HCB_DAY =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "day", JpaFunction.class, Expression.class);

    /**
     * {@code HibernateCriteriaBuilder.hour(Expression)} - extract hour from temporal expression.
     * Returns JpaFunction&lt;Integer&gt;.
     */
    public static final MethodDescriptor HCB_HOUR =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "hour", JpaFunction.class, Expression.class);

    /**
     * {@code HibernateCriteriaBuilder.minute(Expression)} - extract minute from temporal expression.
     * Returns JpaFunction&lt;Integer&gt;.
     */
    public static final MethodDescriptor HCB_MINUTE =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "minute", JpaFunction.class, Expression.class);

    /**
     * {@code HibernateCriteriaBuilder.second(Expression)} - extract second from temporal expression.
     * Returns JpaFunction&lt;Float&gt;.
     */
    public static final MethodDescriptor HCB_SECOND =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "second", JpaFunction.class, Expression.class);
}
