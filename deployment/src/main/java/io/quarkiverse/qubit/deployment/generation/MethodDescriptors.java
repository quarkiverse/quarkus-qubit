package io.quarkiverse.qubit.deployment.generation;

import java.util.Collection;
import java.util.List;

import io.quarkus.gizmo.MethodDescriptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaFunction;

/** Cached MethodDescriptor constants for JPA Criteria API and JDK methods. */
public final class MethodDescriptors {

    private MethodDescriptors() {}

    // ========== Boxed Type Unboxing ==========

    public static final MethodDescriptor INTEGER_INT_VALUE =
            MethodDescriptor.ofMethod(Integer.class, "intValue", int.class);
    public static final MethodDescriptor BOOLEAN_BOOLEAN_VALUE =
            MethodDescriptor.ofMethod(Boolean.class, "booleanValue", boolean.class);
    public static final MethodDescriptor LONG_LONG_VALUE =
            MethodDescriptor.ofMethod(Long.class, "longValue", long.class);

    // ========== Class Operations ==========

    public static final MethodDescriptor CLASS_FOR_NAME =
            MethodDescriptor.ofMethod(Class.class, "forName", Class.class, String.class);

    // ========== Path Navigation ==========

    public static final MethodDescriptor PATH_GET =
            MethodDescriptor.ofMethod(Path.class, "get", Path.class, String.class);

    // ========== Subquery Operations ==========

    public static final MethodDescriptor CRITERIA_QUERY_SUBQUERY =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class);
    public static final MethodDescriptor SUBQUERY_FROM =
            MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class);
    public static final MethodDescriptor SUBQUERY_SELECT =
            MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class);
    public static final MethodDescriptor SUBQUERY_WHERE =
            MethodDescriptor.ofMethod(Subquery.class, "where", Subquery.class, Predicate[].class);

    // ========== CriteriaBuilder Comparison ==========

    public static final MethodDescriptor CB_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "equal", Predicate.class, Expression.class, Object.class);
    public static final MethodDescriptor CB_EQUAL_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "equal", Predicate.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_NOT_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "notEqual", Predicate.class, Expression.class, Object.class);
    public static final MethodDescriptor CB_GREATER_THAN =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThan", Predicate.class, Expression.class, Comparable.class);
    public static final MethodDescriptor CB_GREATER_THAN_OR_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThanOrEqualTo", Predicate.class, Expression.class, Comparable.class);
    public static final MethodDescriptor CB_LESS_THAN =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThan", Predicate.class, Expression.class, Comparable.class);
    public static final MethodDescriptor CB_LESS_THAN_OR_EQUAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThanOrEqualTo", Predicate.class, Expression.class, Comparable.class);
    public static final MethodDescriptor CB_NOT_EQUAL_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "notEqual", Predicate.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_GREATER_THAN_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThan", Predicate.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_GREATER_THAN_OR_EQUAL_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThanOrEqualTo", Predicate.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_LESS_THAN_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThan", Predicate.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_LESS_THAN_OR_EQUAL_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThanOrEqualTo", Predicate.class, Expression.class, Expression.class);

    // ========== CriteriaBuilder Aggregation ==========

    public static final MethodDescriptor CB_COUNT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "count", Expression.class, Expression.class);
    public static final MethodDescriptor CB_AVG =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "avg", Expression.class, Expression.class);

    public static final MethodDescriptor CB_SUM =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "sum", Expression.class, Expression.class);
    public static final MethodDescriptor CB_SUM_BINARY =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "sum", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_MIN =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "min", Expression.class, Expression.class);
    public static final MethodDescriptor CB_MAX =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "max", Expression.class, Expression.class);
    public static final MethodDescriptor CB_COUNT_DISTINCT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "countDistinct", Expression.class, Expression.class);
    public static final MethodDescriptor CB_SUM_AS_LONG =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "sumAsLong", Expression.class, Expression.class);
    public static final MethodDescriptor CB_SUM_AS_DOUBLE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "sumAsDouble", Expression.class, Expression.class);

    // ========== CriteriaBuilder Logic ==========

    public static final MethodDescriptor CB_NOT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "not", Predicate.class, Expression.class);
    public static final MethodDescriptor CB_EXISTS =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "exists", Predicate.class, Subquery.class);
    public static final MethodDescriptor CB_IS_TRUE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isTrue", Predicate.class, Expression.class);
    public static final MethodDescriptor CB_IS_FALSE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isFalse", Predicate.class, Expression.class);
    public static final MethodDescriptor CB_IS_NULL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isNull", Predicate.class, Expression.class);
    public static final MethodDescriptor CB_IS_NOT_NULL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isNotNull", Predicate.class, Expression.class);
    public static final MethodDescriptor CB_AND =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "and", Predicate.class, Predicate[].class);
    public static final MethodDescriptor CB_OR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "or", Predicate.class, Predicate[].class);

    // ========== Expression ==========

    public static final MethodDescriptor EXPRESSION_IN =
            MethodDescriptor.ofMethod(Expression.class, "in", Predicate.class, Expression.class);

    // ========== CriteriaBuilder String ==========

    public static final MethodDescriptor CB_LOWER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "lower", Expression.class, Expression.class);
    public static final MethodDescriptor CB_UPPER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "upper", Expression.class, Expression.class);
    public static final MethodDescriptor CB_TRIM =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "trim", Expression.class, Expression.class);
    public static final MethodDescriptor CB_LENGTH =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "length", Expression.class, Expression.class);
    public static final MethodDescriptor CB_LIKE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "like", Predicate.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_LIKE_STRING =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "like", Predicate.class, Expression.class, String.class);
    public static final MethodDescriptor CB_CONCAT_EXPR_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "concat", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_CONCAT_STR_EXPR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "concat", Expression.class, String.class, Expression.class);
    public static final MethodDescriptor CB_CONCAT_EXPR_STR =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "concat", Expression.class, Expression.class, String.class);
    public static final MethodDescriptor CB_SUBSTRING_2 =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "substring", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_SUBSTRING_3 =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "substring", Expression.class, Expression.class, Expression.class, Expression.class);

    // ========== CriteriaBuilder Temporal ==========

    public static final MethodDescriptor CB_FUNCTION =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "function", Expression.class, String.class, Class.class, Expression[].class);

    // ========== CriteriaBuilder Arithmetic ==========

    public static final MethodDescriptor CB_PROD =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "prod", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_QUOT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "quot", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_DIFF =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "diff", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_MOD =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "mod", Expression.class, Expression.class, Expression.class);
    public static final MethodDescriptor CB_LITERAL =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "literal", Expression.class, Object.class);

    // ========== CriteriaBuilder Collection ==========

    public static final MethodDescriptor CB_IS_MEMBER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isMember", Predicate.class, Object.class, Expression.class);
    public static final MethodDescriptor CB_IS_NOT_MEMBER =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "isNotMember", Predicate.class, Object.class, Expression.class);
    public static final MethodDescriptor EXPRESSION_IN_COLLECTION =
            MethodDescriptor.ofMethod(Expression.class, "in", Predicate.class, Collection.class);

    // ========== CriteriaQuery Selection ==========

    public static final MethodDescriptor CB_TUPLE =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "tuple", CompoundSelection.class, Selection[].class);
    public static final MethodDescriptor CB_CONSTRUCT =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "construct", CompoundSelection.class, Class.class, Selection[].class);

    // ========== HibernateCriteriaBuilder Temporal (Database-Agnostic) ==========

    public static final MethodDescriptor HCB_YEAR =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "year", JpaFunction.class, Expression.class);
    public static final MethodDescriptor HCB_MONTH =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "month", JpaFunction.class, Expression.class);
    public static final MethodDescriptor HCB_DAY =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "day", JpaFunction.class, Expression.class);
    public static final MethodDescriptor HCB_HOUR =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "hour", JpaFunction.class, Expression.class);
    public static final MethodDescriptor HCB_MINUTE =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "minute", JpaFunction.class, Expression.class);
    public static final MethodDescriptor HCB_SECOND =
            MethodDescriptor.ofMethod(HibernateCriteriaBuilder.class, "second", JpaFunction.class, Expression.class);

    // ========== JDK String ==========

    public static final MethodDescriptor STRING_CONCAT =
            MethodDescriptor.ofMethod(String.class, "concat", String.class, String.class);

    // ========== EntityManager Query ==========

    public static final MethodDescriptor EM_GET_CRITERIA_BUILDER =
            MethodDescriptor.ofMethod(EntityManager.class, "getCriteriaBuilder", CriteriaBuilder.class);
    public static final MethodDescriptor EM_CREATE_QUERY =
            MethodDescriptor.ofMethod(EntityManager.class, "createQuery", TypedQuery.class, CriteriaQuery.class);

    // ========== CriteriaBuilder Query ==========

    public static final MethodDescriptor CB_CREATE_QUERY =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "createQuery", CriteriaQuery.class, Class.class);

    // ========== CriteriaQuery ==========

    public static final MethodDescriptor CQ_FROM =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "from", Root.class, Class.class);
    public static final MethodDescriptor CQ_SELECT =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "select", CriteriaQuery.class, Selection.class);
    public static final MethodDescriptor CQ_WHERE =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "where", CriteriaQuery.class, Predicate[].class);
    public static final MethodDescriptor CQ_ORDER_BY =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "orderBy", CriteriaQuery.class, Order[].class);
    public static final MethodDescriptor CQ_DISTINCT =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "distinct", CriteriaQuery.class, boolean.class);
    public static final MethodDescriptor CQ_GROUP_BY =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "groupBy", CriteriaQuery.class, Expression[].class);
    public static final MethodDescriptor CQ_HAVING =
            MethodDescriptor.ofMethod(CriteriaQuery.class, "having", CriteriaQuery.class, Predicate[].class);

    // ========== CriteriaBuilder Ordering ==========

    public static final MethodDescriptor CB_ASC =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "asc", Order.class, Expression.class);
    public static final MethodDescriptor CB_DESC =
            MethodDescriptor.ofMethod(CriteriaBuilder.class, "desc", Order.class, Expression.class);

    // ========== TypedQuery ==========

    public static final MethodDescriptor TQ_GET_RESULT_LIST =
            MethodDescriptor.ofMethod(TypedQuery.class, "getResultList", List.class);
    public static final MethodDescriptor TQ_GET_SINGLE_RESULT =
            MethodDescriptor.ofMethod(TypedQuery.class, "getSingleResult", Object.class);
    public static final MethodDescriptor TQ_SET_FIRST_RESULT =
            MethodDescriptor.ofMethod(TypedQuery.class, "setFirstResult", TypedQuery.class, int.class);
    public static final MethodDescriptor TQ_SET_MAX_RESULTS =
            MethodDescriptor.ofMethod(TypedQuery.class, "setMaxResults", TypedQuery.class, int.class);

    // ========== Join ==========

    public static final MethodDescriptor FROM_JOIN =
            MethodDescriptor.ofMethod(From.class, "join", Join.class, String.class, JoinType.class);

    // ========== JDK Utility ==========

    public static final MethodDescriptor LIST_SIZE =
            MethodDescriptor.ofMethod(List.class, "size", int.class);
    public static final MethodDescriptor INTEGER_VALUE_OF =
            MethodDescriptor.ofMethod(Integer.class, "valueOf", Integer.class, int.class);
    public static final MethodDescriptor LONG_VALUE_OF =
            MethodDescriptor.ofMethod(Long.class, "valueOf", Long.class, long.class);
    public static final MethodDescriptor INTEGER_LONG_VALUE =
            MethodDescriptor.ofMethod(Integer.class, "longValue", long.class);

    // ========== Factory Method ==========

    /** Creates a MethodDescriptor for methods not covered by predefined constants. */
    public static MethodDescriptor md(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        return MethodDescriptor.ofMethod(clazz, methodName, returnType, params);
    }
}
