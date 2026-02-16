package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_GROUP_BY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_HAVING;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_SELECT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.INTEGER_LONG_VALUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.INTEGER_VALUE_OF;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.LIST_SIZE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.LONG_VALUE_OF;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_RESULT_LIST;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_SINGLE_RESULT;

import java.util.List;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator.QuerySetup;
import io.quarkiverse.qubit.deployment.generation.join.StandardClauseApplier;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Generates GROUP BY query bytecode for JPA Criteria API queries.
 *
 * <p>
 * Handles GROUP BY list queries (with optional WHERE, HAVING, SELECT, ORDER BY)
 * and GROUP BY count queries. Extracted from {@link QueryExecutorClassGenerator}
 * to separate group-specific logic.
 */
final class GroupQueryGenerator {

    /** Context for GROUP BY query generation with WHERE, GROUP BY, and HAVING. */
    record GroupQueryContext(
            BlockCreator bc,
            Expr em,
            Expr entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            Expr capturedValues) {
    }

    /** Extended GROUP BY context adding select, sort, pagination, and distinct. */
    record GroupListContext(
            GroupQueryContext base,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            Expr offset,
            Expr limit,
            Expr distinct) {
        // Delegate accessors for convenience
        BlockCreator bc() {
            return base.bc();
        }

        Expr em() {
            return base.em();
        }

        Expr entityClass() {
            return base.entityClass();
        }

        LambdaExpression havingExpression() {
            return base.havingExpression();
        }

        Expr capturedValues() {
            return base.capturedValues();
        }
    }

    private final CriteriaExpressionGenerator expressionGenerator;
    private final StandardClauseApplier clauseApplier;
    private final QueryExecutorClassGenerator parent;

    GroupQueryGenerator(
            CriteriaExpressionGenerator expressionGenerator,
            StandardClauseApplier clauseApplier,
            QueryExecutorClassGenerator parent) {
        this.expressionGenerator = expressionGenerator;
        this.clauseApplier = clauseApplier;
        this.parent = parent;
    }

    /** Generates GROUP BY query with optional WHERE, HAVING, SELECT, and ORDER BY. */
    Expr generateGroupQueryBody(GroupListContext ctx) {
        BlockCreator bc = ctx.bc();

        // Setup query and apply common GROUP BY logic
        QuerySetup setup = parent.setupQueryForObject(bc, ctx.em(), ctx.entityClass());
        Expr cb = setup.cb();
        Expr query = setup.query();
        Expr root = setup.root();

        // Apply common WHERE and GROUP BY setup
        // Returns LocalVar for Gizmo2 scoping (used across HAVING, SELECT, ORDER BY)
        LocalVar groupKeyExpr = applyGroupQuerySetup(ctx.base(), setup);

        // Apply HAVING predicate if present
        if (ctx.havingExpression() != null) {
            Expr havingPredicate = expressionGenerator.generateGroupPredicate(
                    bc, ctx.havingExpression(), cb, root, groupKeyExpr, ctx.capturedValues());
            applyHavingPredicate(bc, query, havingPredicate);
        }

        // Apply SELECT projection if present
        if (ctx.groupSelectExpression() != null) {
            Expr selection = expressionGenerator.generateGroupSelectExpression(
                    bc, ctx.groupSelectExpression(), cb, root, groupKeyExpr, ctx.capturedValues());
            bc.invokeInterface(CQ_SELECT, query, selection);
        } else {
            // Default: SELECT the grouping key
            bc.invokeInterface(CQ_SELECT, query, groupKeyExpr);
        }

        // Apply ORDER BY for group queries
        applyGroupOrderBy(bc, query, root, groupKeyExpr, cb, ctx.groupSortExpressions(), ctx.capturedValues());

        // Apply DISTINCT if requested
        clauseApplier.applyDistinct(bc, query, ctx.distinct());

        // Create TypedQuery
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        LocalVar typedQuery = bc.localVar("typedQuery", bc.invokeInterface(EM_CREATE_QUERY, ctx.em(), query));

        // Apply pagination
        clauseApplier.applyPagination(bc, typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return bc.invokeInterface(TQ_GET_RESULT_LIST, typedQuery);
    }

    /** Applies WHERE predicate and generates GROUP BY key expression. */
    private Expr applyWhereAndGenerateGroupKey(GroupQueryContext ctx, QuerySetup setup) {
        BlockCreator bc = ctx.bc();

        // Apply pre-grouping WHERE predicate if present (with subquery support)
        if (ctx.predicateExpression() != null) {
            Expr predicate = expressionGenerator.generatePredicateWithSubqueries(
                    bc, ctx.predicateExpression(), setup.cb(), setup.query(), setup.root(), ctx.capturedValues());
            clauseApplier.applyWherePredicate(bc, setup.query(), predicate);
        }

        // Generate and return GROUP BY key expression
        return expressionGenerator.generateExpression(
                bc, ctx.groupByKeyExpression(), setup.cb(), setup.root(), ctx.capturedValues());
    }

    /** Applies WHERE, GROUP BY key, and GROUP BY clause setup. Returns LocalVar for Gizmo2 scoping. */
    private LocalVar applyGroupQuerySetup(GroupQueryContext ctx, QuerySetup setup) {
        // Apply WHERE and generate group key
        Expr groupKeyExpr = applyWhereAndGenerateGroupKey(ctx, setup);

        // Store in LocalVar for Gizmo2 scoping - this value is used across multiple operations
        // (HAVING predicate, SELECT expression, ORDER BY, and the GROUP BY array itself)
        LocalVar groupKeyVar = ctx.bc().localVar("groupKey", groupKeyExpr);

        // Apply GROUP BY
        LocalVar groupByArray = ctx.bc().localVar("groupByArray", ctx.bc().newEmptyArray(Expression.class, 1));
        ctx.bc().set(groupByArray.elem(0), groupKeyVar);
        ctx.bc().invokeInterface(CQ_GROUP_BY, setup.query(), groupByArray);

        return groupKeyVar;
    }

    /** Generates GROUP BY COUNT using COUNT(DISTINCT) or runtime result counting for HAVING. */
    Expr generateGroupCountQueryBody(GroupQueryContext ctx) {
        if (ctx.havingExpression() != null) {
            return generateGroupCountWithHaving(ctx);
        } else {
            return generateGroupCountWithoutHaving(ctx);
        }
    }

    /** Generates GROUP COUNT with HAVING (counts results at runtime). */
    private Expr generateGroupCountWithHaving(GroupQueryContext ctx) {
        BlockCreator bc = ctx.bc();

        // With HAVING: Create query for Object (group key type may vary)
        QuerySetup setup = parent.setupQueryForObject(bc, ctx.em(), ctx.entityClass());

        // Apply WHERE + GROUP BY setup (returns LocalVar for Gizmo2 scoping)
        LocalVar groupKeyExpr = applyGroupQuerySetup(ctx, setup);

        // Apply HAVING predicate
        Expr havingPredicate = expressionGenerator.generateGroupPredicate(
                bc, ctx.havingExpression(), setup.cb(), setup.root(), groupKeyExpr, ctx.capturedValues());
        applyHavingPredicate(bc, setup.query(), havingPredicate);

        // SELECT groupKey (we'll count results at runtime)
        bc.invokeInterface(CQ_SELECT, setup.query(), groupKeyExpr);

        // Create TypedQuery and get result list
        Expr typedQuery = bc.invokeInterface(EM_CREATE_QUERY, ctx.em(), setup.query());
        Expr resultList = bc.invokeInterface(TQ_GET_RESULT_LIST, typedQuery);

        // Return result list size as Long
        Expr size = bc.invokeInterface(LIST_SIZE, resultList);
        Expr sizeLong = bc.invokeVirtual(
                INTEGER_LONG_VALUE,
                bc.invokeStatic(INTEGER_VALUE_OF, size));
        return bc.invokeStatic(LONG_VALUE_OF, sizeLong);
    }

    /** Generates GROUP COUNT without HAVING using COUNT(DISTINCT groupKey). */
    private Expr generateGroupCountWithoutHaving(GroupQueryContext ctx) {
        BlockCreator bc = ctx.bc();

        // Without HAVING: Create query for Long (count result)
        QuerySetup setup = parent.setupQueryForLong(bc, ctx.em(), ctx.entityClass());

        // Apply WHERE and generate group key (no GROUP BY needed for COUNT DISTINCT)
        Expr groupKeyExpr = applyWhereAndGenerateGroupKey(ctx, setup);

        // Simple COUNT(DISTINCT groupKey)
        Expr countExpr = bc.invokeInterface(CB_COUNT_DISTINCT, setup.cb(), groupKeyExpr);
        bc.invokeInterface(CQ_SELECT, setup.query(), countExpr);

        // Create TypedQuery and return getSingleResult()
        Expr typedQuery = bc.invokeInterface(EM_CREATE_QUERY, ctx.em(), setup.query());
        return bc.invokeInterface(TQ_GET_SINGLE_RESULT, typedQuery);
    }

    /**
     * Applies HAVING clause predicate to CriteriaQuery.
     */
    private void applyHavingPredicate(BlockCreator bc, Expr query, Expr predicate) {
        if (predicate != null) {
            Expr predicateArray = GizmoHelper.createElementArray(bc, Predicate.class, predicate);
            bc.invokeInterface(CQ_HAVING, query, predicateArray);
        }
    }

    /**
     * Applies ORDER BY clause for GROUP BY queries.
     */
    private void applyGroupOrderBy(
            BlockCreator bc,
            Expr query,
            Expr root,
            Expr groupKeyExpr,
            Expr cb,
            List<?> sortExpressions,
            Expr capturedValues) {

        GizmoHelper.buildOrderByClause(bc, query, cb, sortExpressions,
                sortExpr -> expressionGenerator.generateGroupSortExpression(
                        bc, sortExpr.keyExtractor(), cb, root, groupKeyExpr, capturedValues));
    }
}
