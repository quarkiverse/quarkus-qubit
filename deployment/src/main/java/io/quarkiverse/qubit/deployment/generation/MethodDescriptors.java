package io.quarkiverse.qubit.deployment.generation;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaFunction;

import io.quarkus.gizmo2.desc.MethodDesc;

/** Cached {@link MethodDesc} constants for JPA Criteria API and JDK methods (Gizmo 2). */
public final class MethodDescriptors {

    private MethodDescriptors() {
    }

    public static final MethodDesc INTEGER_INT_VALUE = MethodDesc.of(Integer.class, "intValue", int.class);
    public static final MethodDesc BOOLEAN_BOOLEAN_VALUE = MethodDesc.of(Boolean.class, "booleanValue", boolean.class);
    public static final MethodDesc LONG_LONG_VALUE = MethodDesc.of(Long.class, "longValue", long.class);

    public static final MethodDesc CLASS_FOR_NAME = MethodDesc.of(Class.class, "forName", Class.class, String.class);

    public static final MethodDesc PATH_GET = MethodDesc.of(Path.class, "get", Path.class, String.class);

    public static final MethodDesc CRITERIA_QUERY_SUBQUERY = MethodDesc.of(CriteriaQuery.class, "subquery", Subquery.class,
            Class.class);
    public static final MethodDesc SUBQUERY_FROM = MethodDesc.of(Subquery.class, "from", Root.class, Class.class);
    public static final MethodDesc SUBQUERY_SELECT = MethodDesc.of(Subquery.class, "select", Subquery.class, Expression.class);
    public static final MethodDesc SUBQUERY_WHERE = MethodDesc.of(Subquery.class, "where", Subquery.class, Predicate[].class);

    public static final MethodDesc CB_EQUAL = MethodDesc.of(CriteriaBuilder.class, "equal", Predicate.class, Expression.class,
            Object.class);
    public static final MethodDesc CB_EQUAL_EXPR = MethodDesc.of(CriteriaBuilder.class, "equal", Predicate.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_NOT_EQUAL = MethodDesc.of(CriteriaBuilder.class, "notEqual", Predicate.class,
            Expression.class, Object.class);
    public static final MethodDesc CB_GREATER_THAN = MethodDesc.of(CriteriaBuilder.class, "greaterThan", Predicate.class,
            Expression.class, Comparable.class);
    public static final MethodDesc CB_GREATER_THAN_OR_EQUAL = MethodDesc.of(CriteriaBuilder.class, "greaterThanOrEqualTo",
            Predicate.class, Expression.class, Comparable.class);
    public static final MethodDesc CB_LESS_THAN = MethodDesc.of(CriteriaBuilder.class, "lessThan", Predicate.class,
            Expression.class, Comparable.class);
    public static final MethodDesc CB_LESS_THAN_OR_EQUAL = MethodDesc.of(CriteriaBuilder.class, "lessThanOrEqualTo",
            Predicate.class, Expression.class, Comparable.class);
    public static final MethodDesc CB_NOT_EQUAL_EXPR = MethodDesc.of(CriteriaBuilder.class, "notEqual", Predicate.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_GREATER_THAN_EXPR = MethodDesc.of(CriteriaBuilder.class, "greaterThan", Predicate.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_GREATER_THAN_OR_EQUAL_EXPR = MethodDesc.of(CriteriaBuilder.class, "greaterThanOrEqualTo",
            Predicate.class, Expression.class, Expression.class);
    public static final MethodDesc CB_LESS_THAN_EXPR = MethodDesc.of(CriteriaBuilder.class, "lessThan", Predicate.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_LESS_THAN_OR_EQUAL_EXPR = MethodDesc.of(CriteriaBuilder.class, "lessThanOrEqualTo",
            Predicate.class, Expression.class, Expression.class);

    /** cb.between(Expression, Expression, Expression) -> Predicate (JPA 2.0) */
    public static final MethodDesc CB_BETWEEN_EXPR = MethodDesc.of(CriteriaBuilder.class, "between", Predicate.class,
            Expression.class, Expression.class, Expression.class);

    /** cb.between(Expression, Comparable, Comparable) -> Predicate (JPA 2.0) */
    public static final MethodDesc CB_BETWEEN = MethodDesc.of(CriteriaBuilder.class, "between", Predicate.class,
            Expression.class, Comparable.class, Comparable.class);

    public static final MethodDesc CB_COUNT = MethodDesc.of(CriteriaBuilder.class, "count", Expression.class, Expression.class);
    public static final MethodDesc CB_AVG = MethodDesc.of(CriteriaBuilder.class, "avg", Expression.class, Expression.class);
    public static final MethodDesc CB_SUM = MethodDesc.of(CriteriaBuilder.class, "sum", Expression.class, Expression.class);
    public static final MethodDesc CB_SUM_BINARY = MethodDesc.of(CriteriaBuilder.class, "sum", Expression.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_MIN = MethodDesc.of(CriteriaBuilder.class, "min", Expression.class, Expression.class);
    public static final MethodDesc CB_MAX = MethodDesc.of(CriteriaBuilder.class, "max", Expression.class, Expression.class);
    public static final MethodDesc CB_COUNT_DISTINCT = MethodDesc.of(CriteriaBuilder.class, "countDistinct", Expression.class,
            Expression.class);
    public static final MethodDesc CB_SUM_AS_LONG = MethodDesc.of(CriteriaBuilder.class, "sumAsLong", Expression.class,
            Expression.class);
    public static final MethodDesc CB_SUM_AS_DOUBLE = MethodDesc.of(CriteriaBuilder.class, "sumAsDouble", Expression.class,
            Expression.class);

    public static final MethodDesc CB_NOT = MethodDesc.of(CriteriaBuilder.class, "not", Predicate.class, Expression.class);
    public static final MethodDesc CB_EXISTS = MethodDesc.of(CriteriaBuilder.class, "exists", Predicate.class, Subquery.class);
    public static final MethodDesc CB_IS_TRUE = MethodDesc.of(CriteriaBuilder.class, "isTrue", Predicate.class,
            Expression.class);
    public static final MethodDesc CB_IS_FALSE = MethodDesc.of(CriteriaBuilder.class, "isFalse", Predicate.class,
            Expression.class);
    public static final MethodDesc CB_IS_NULL = MethodDesc.of(CriteriaBuilder.class, "isNull", Predicate.class,
            Expression.class);
    public static final MethodDesc CB_IS_NOT_NULL = MethodDesc.of(CriteriaBuilder.class, "isNotNull", Predicate.class,
            Expression.class);
    public static final MethodDesc CB_AND = MethodDesc.of(CriteriaBuilder.class, "and", Predicate.class, Predicate[].class);
    public static final MethodDesc CB_OR = MethodDesc.of(CriteriaBuilder.class, "or", Predicate.class, Predicate[].class);

    public static final MethodDesc EXPRESSION_IN = MethodDesc.of(Expression.class, "in", Predicate.class, Expression.class);

    public static final MethodDesc CB_LOWER = MethodDesc.of(CriteriaBuilder.class, "lower", Expression.class, Expression.class);
    public static final MethodDesc CB_UPPER = MethodDesc.of(CriteriaBuilder.class, "upper", Expression.class, Expression.class);
    public static final MethodDesc CB_TRIM = MethodDesc.of(CriteriaBuilder.class, "trim", Expression.class, Expression.class);
    public static final MethodDesc CB_LENGTH = MethodDesc.of(CriteriaBuilder.class, "length", Expression.class,
            Expression.class);
    public static final MethodDesc CB_LIKE = MethodDesc.of(CriteriaBuilder.class, "like", Predicate.class, Expression.class,
            Expression.class);
    public static final MethodDesc CB_LIKE_STRING = MethodDesc.of(CriteriaBuilder.class, "like", Predicate.class,
            Expression.class, String.class);
    public static final MethodDesc CB_CONCAT_EXPR_EXPR = MethodDesc.of(CriteriaBuilder.class, "concat", Expression.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_CONCAT_STR_EXPR = MethodDesc.of(CriteriaBuilder.class, "concat", Expression.class,
            String.class, Expression.class);
    public static final MethodDesc CB_CONCAT_EXPR_STR = MethodDesc.of(CriteriaBuilder.class, "concat", Expression.class,
            Expression.class, String.class);
    public static final MethodDesc CB_SUBSTRING_2 = MethodDesc.of(CriteriaBuilder.class, "substring", Expression.class,
            Expression.class, Expression.class);
    public static final MethodDesc CB_SUBSTRING_3 = MethodDesc.of(CriteriaBuilder.class, "substring", Expression.class,
            Expression.class, Expression.class, Expression.class);

    /** cb.locate(Expression<String>, Expression<String>) -> Expression<Integer> (JPA 2.0) */
    public static final MethodDesc CB_LOCATE_2 = MethodDesc.of(CriteriaBuilder.class, "locate", Expression.class,
            Expression.class, Expression.class);

    /** cb.locate(Expression<String>, Expression<String>, Expression<Integer>) -> Expression<Integer> (JPA 2.0) */
    public static final MethodDesc CB_LOCATE_3 = MethodDesc.of(CriteriaBuilder.class, "locate", Expression.class,
            Expression.class, Expression.class, Expression.class);

    public static final MethodDesc CB_FUNCTION = MethodDesc.of(CriteriaBuilder.class, "function", Expression.class,
            String.class, Class.class, Expression[].class);

    public static final MethodDesc CB_PROD = MethodDesc.of(CriteriaBuilder.class, "prod", Expression.class, Expression.class,
            Expression.class);
    public static final MethodDesc CB_QUOT = MethodDesc.of(CriteriaBuilder.class, "quot", Expression.class, Expression.class,
            Expression.class);
    public static final MethodDesc CB_DIFF = MethodDesc.of(CriteriaBuilder.class, "diff", Expression.class, Expression.class,
            Expression.class);
    public static final MethodDesc CB_MOD = MethodDesc.of(CriteriaBuilder.class, "mod", Expression.class, Expression.class,
            Expression.class);
    public static final MethodDesc CB_LITERAL = MethodDesc.of(CriteriaBuilder.class, "literal", Expression.class, Object.class);

    /** cb.nullLiteral(Class) -> Expression (JPA 2.0) */
    public static final MethodDesc CB_NULL_LITERAL = MethodDesc.of(CriteriaBuilder.class, "nullLiteral", Expression.class,
            Class.class);

    public static final MethodDesc CB_IS_MEMBER = MethodDesc.of(CriteriaBuilder.class, "isMember", Predicate.class,
            Object.class, Expression.class);
    public static final MethodDesc CB_IS_NOT_MEMBER = MethodDesc.of(CriteriaBuilder.class, "isNotMember", Predicate.class,
            Object.class, Expression.class);
    public static final MethodDesc EXPRESSION_IN_COLLECTION = MethodDesc.of(Expression.class, "in", Predicate.class,
            Collection.class);

    public static final MethodDesc CB_TUPLE = MethodDesc.of(CriteriaBuilder.class, "tuple", CompoundSelection.class,
            Selection[].class);
    public static final MethodDesc CB_CONSTRUCT = MethodDesc.of(CriteriaBuilder.class, "construct", CompoundSelection.class,
            Class.class, Selection[].class);

    public static final MethodDesc HCB_YEAR = MethodDesc.of(HibernateCriteriaBuilder.class, "year", JpaFunction.class,
            Expression.class);
    public static final MethodDesc HCB_MONTH = MethodDesc.of(HibernateCriteriaBuilder.class, "month", JpaFunction.class,
            Expression.class);
    public static final MethodDesc HCB_DAY = MethodDesc.of(HibernateCriteriaBuilder.class, "day", JpaFunction.class,
            Expression.class);
    public static final MethodDesc HCB_HOUR = MethodDesc.of(HibernateCriteriaBuilder.class, "hour", JpaFunction.class,
            Expression.class);
    public static final MethodDesc HCB_MINUTE = MethodDesc.of(HibernateCriteriaBuilder.class, "minute", JpaFunction.class,
            Expression.class);
    public static final MethodDesc HCB_SECOND = MethodDesc.of(HibernateCriteriaBuilder.class, "second", JpaFunction.class,
            Expression.class);

    // ─── Math Functions (JPA 2.0 + 3.1) ─────────────────────────────────────

    /** cb.abs(Expression) -> Expression (JPA 2.0) */
    public static final MethodDesc CB_ABS = MethodDesc.of(CriteriaBuilder.class, "abs", Expression.class, Expression.class);

    /** cb.neg(Expression) -> Expression (JPA 2.0) */
    public static final MethodDesc CB_NEG = MethodDesc.of(CriteriaBuilder.class, "neg", Expression.class, Expression.class);

    /** cb.sqrt(Expression) -> Expression<Double> (JPA 2.0) */
    public static final MethodDesc CB_SQRT = MethodDesc.of(CriteriaBuilder.class, "sqrt", Expression.class, Expression.class);

    /** cb.sign(Expression) -> Expression<Integer> (JPA 3.1) */
    public static final MethodDesc CB_SIGN = MethodDesc.of(CriteriaBuilder.class, "sign", Expression.class, Expression.class);

    /** cb.ceiling(Expression) -> Expression (JPA 3.1) */
    public static final MethodDesc CB_CEILING = MethodDesc.of(CriteriaBuilder.class, "ceiling", Expression.class,
            Expression.class);

    /** cb.floor(Expression) -> Expression (JPA 3.1) */
    public static final MethodDesc CB_FLOOR = MethodDesc.of(CriteriaBuilder.class, "floor", Expression.class,
            Expression.class);

    /** cb.exp(Expression) -> Expression<Double> (JPA 3.1) */
    public static final MethodDesc CB_EXP = MethodDesc.of(CriteriaBuilder.class, "exp", Expression.class, Expression.class);

    /** cb.ln(Expression) -> Expression<Double> (JPA 3.1) */
    public static final MethodDesc CB_LN = MethodDesc.of(CriteriaBuilder.class, "ln", Expression.class, Expression.class);

    /** cb.power(Expression, Expression) -> Expression<Double> (JPA 3.1) */
    public static final MethodDesc CB_POWER = MethodDesc.of(CriteriaBuilder.class, "power", Expression.class,
            Expression.class, Expression.class);

    /** cb.power(Expression, Number) -> Expression<Double> (JPA 3.1) */
    public static final MethodDesc CB_POWER_NUMBER = MethodDesc.of(CriteriaBuilder.class, "power", Expression.class,
            Expression.class, Number.class);

    /** cb.round(Expression, Integer) -> Expression (JPA 3.1) */
    public static final MethodDesc CB_ROUND = MethodDesc.of(CriteriaBuilder.class, "round", Expression.class,
            Expression.class, Integer.class);

    public static final MethodDesc STRING_CONCAT = MethodDesc.of(String.class, "concat", String.class, String.class);

    public static final MethodDesc EM_GET_CRITERIA_BUILDER = MethodDesc.of(EntityManager.class, "getCriteriaBuilder",
            CriteriaBuilder.class);
    public static final MethodDesc EM_CREATE_QUERY = MethodDesc.of(EntityManager.class, "createQuery", TypedQuery.class,
            CriteriaQuery.class);

    public static final MethodDesc CB_CREATE_QUERY = MethodDesc.of(CriteriaBuilder.class, "createQuery", CriteriaQuery.class,
            Class.class);

    public static final MethodDesc CQ_FROM = MethodDesc.of(CriteriaQuery.class, "from", Root.class, Class.class);
    public static final MethodDesc CQ_SELECT = MethodDesc.of(CriteriaQuery.class, "select", CriteriaQuery.class,
            Selection.class);
    public static final MethodDesc CQ_WHERE = MethodDesc.of(CriteriaQuery.class, "where", CriteriaQuery.class,
            Predicate[].class);
    public static final MethodDesc CQ_ORDER_BY = MethodDesc.of(CriteriaQuery.class, "orderBy", CriteriaQuery.class,
            Order[].class);
    public static final MethodDesc CQ_DISTINCT = MethodDesc.of(CriteriaQuery.class, "distinct", CriteriaQuery.class,
            boolean.class);
    public static final MethodDesc CQ_GROUP_BY = MethodDesc.of(CriteriaQuery.class, "groupBy", CriteriaQuery.class,
            Expression[].class);
    public static final MethodDesc CQ_HAVING = MethodDesc.of(CriteriaQuery.class, "having", CriteriaQuery.class,
            Predicate[].class);

    /** CriteriaBuilder.selectCase() -> Case<Object> */
    public static final MethodDesc CB_SELECT_CASE = MethodDesc.of(CriteriaBuilder.class, "selectCase",
            CriteriaBuilder.Case.class);
    /** Case.when(Expression<Boolean> condition, Expression<R> result) -> Case<R> */
    public static final MethodDesc CASE_WHEN_EXPR = MethodDesc.of(CriteriaBuilder.Case.class, "when",
            CriteriaBuilder.Case.class, Expression.class, Expression.class);
    /** Case.when(Expression<Boolean> condition, R result) -> Case<R> */
    public static final MethodDesc CASE_WHEN_VALUE = MethodDesc.of(CriteriaBuilder.Case.class, "when",
            CriteriaBuilder.Case.class, Expression.class, Object.class);
    /** Case.otherwise(Expression<R> result) -> Expression<R> */
    public static final MethodDesc CASE_OTHERWISE_EXPR = MethodDesc.of(CriteriaBuilder.Case.class, "otherwise",
            Expression.class, Expression.class);
    /** Case.otherwise(R result) -> Expression<R> */
    public static final MethodDesc CASE_OTHERWISE_VALUE = MethodDesc.of(CriteriaBuilder.Case.class, "otherwise",
            Expression.class, Object.class);

    /** cb.nullif(Expression, Expression) -> Expression (JPA 2.0) */
    public static final MethodDesc CB_NULLIF_EXPR = MethodDesc.of(CriteriaBuilder.class, "nullif", Expression.class,
            Expression.class, Expression.class);

    /** cb.nullif(Expression, Object) -> Expression (JPA 2.0) */
    public static final MethodDesc CB_NULLIF = MethodDesc.of(CriteriaBuilder.class, "nullif", Expression.class,
            Expression.class, Object.class);

    public static final MethodDesc CB_ASC = MethodDesc.of(CriteriaBuilder.class, "asc", Order.class, Expression.class);
    public static final MethodDesc CB_DESC = MethodDesc.of(CriteriaBuilder.class, "desc", Order.class, Expression.class);

    public static final MethodDesc TQ_GET_RESULT_LIST = MethodDesc.of(TypedQuery.class, "getResultList", List.class);
    public static final MethodDesc TQ_GET_SINGLE_RESULT = MethodDesc.of(TypedQuery.class, "getSingleResult", Object.class);
    public static final MethodDesc TQ_SET_FIRST_RESULT = MethodDesc.of(TypedQuery.class, "setFirstResult", TypedQuery.class,
            int.class);
    public static final MethodDesc TQ_SET_MAX_RESULTS = MethodDesc.of(TypedQuery.class, "setMaxResults", TypedQuery.class,
            int.class);

    public static final MethodDesc FROM_JOIN = MethodDesc.of(From.class, "join", Join.class, String.class, JoinType.class);

    public static final MethodDesc LIST_SIZE = MethodDesc.of(List.class, "size", int.class);
    public static final MethodDesc INTEGER_VALUE_OF = MethodDesc.of(Integer.class, "valueOf", Integer.class, int.class);
    public static final MethodDesc LONG_VALUE_OF = MethodDesc.of(Long.class, "valueOf", Long.class, long.class);
    public static final MethodDesc INTEGER_LONG_VALUE = MethodDesc.of(Integer.class, "longValue", long.class);

    /** Creates a MethodDesc for methods not covered by predefined constants. */
    public static MethodDesc md(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        return MethodDesc.of(clazz, methodName, returnType, params);
    }
}
