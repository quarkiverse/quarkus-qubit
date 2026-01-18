package io.quarkiverse.qubit.deployment.devui;

import static io.quarkiverse.qubit.runtime.QubitConstants.LAMBDA_SUFFIX_MARKER;
import static io.quarkiverse.qubit.runtime.QubitConstants.QUERY_ID_SEPARATOR;

import java.util.List;

import io.quarkiverse.qubit.deployment.util.ClassNameUtils;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkiverse.qubit.deployment.QubitProcessor.QueryTransformationBuildItem;

/** DevUI processor for Qubit extension. */
public class QubitDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void createPages(
            List<QueryTransformationBuildItem> queryTransformations,
            BuildProducer<CardPageBuildItem> cardPages) {

        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:database")
                .title("Lambda Queries")
                .componentLink("qwc-qubit-queries.js")
                .staticLabel(String.valueOf(queryTransformations.size())));

        card.addBuildTimeData("queries", createQueryDataList(queryTransformations));

        cardPages.produce(card);
    }

    private List<QueryData> createQueryDataList(List<QueryTransformationBuildItem> transformations) {
        return transformations.stream()
                .map(this::toQueryData)
                .toList();
    }

    private QueryData toQueryData(QueryTransformationBuildItem transformation) {
        String entityClassName = transformation.getEntityClassName();
        String queryId = transformation.getQueryId();

        // Generate JPQL representation (only in dev mode, which is guaranteed by @BuildStep annotation)
        String jpql;
        if (transformation.isJoinQuery()) {
            // Use join-specific JPQL generator with JOIN clause
            jpql = JpqlGenerator.generateJoinJpql(
                    entityClassName,
                    transformation.getJoinRelationshipExpression(),
                    transformation.getPredicateExpression(),
                    transformation.getProjectionExpression(),
                    false,  // isLeftJoin - default to INNER JOIN for now
                    transformation.isCountQuery());
        } else {
            jpql = JpqlGenerator.generateJpql(
                    entityClassName,
                    transformation.getPredicateExpression(),
                    transformation.getProjectionExpression(),
                    transformation.isCountQuery());
        }

        // Generate Java source code representation
        // For join queries, use bi-entity generator for predicate/projection
        // For group queries, use group generator for projection (select in group context)
        String predicateSource;
        String projectionSource;
        if (transformation.isJoinQuery()) {
            // Use bi-entity generator with entity variable names (e.g., "(p, j) -> ...")
            String firstParam = "p";  // Primary entity
            String secondParam = "j"; // Joined entity
            predicateSource = JavaSourceGenerator.generateBiEntityJavaSource(
                    transformation.getPredicateExpression(), firstParam, secondParam);
            projectionSource = JavaSourceGenerator.generateBiEntityJavaSource(
                    transformation.getProjectionExpression(), firstParam, secondParam);
        } else if (transformation.isGroupQuery()) {
            // For group queries: predicate is regular (pre-grouping WHERE), projection uses Group parameter
            predicateSource = JavaSourceGenerator.generateJavaSource(transformation.getPredicateExpression());
            projectionSource = JavaSourceGenerator.generateGroupJavaSource(transformation.getProjectionExpression());
        } else {
            predicateSource = JavaSourceGenerator.generateJavaSource(transformation.getPredicateExpression());
            projectionSource = JavaSourceGenerator.generateJavaSource(transformation.getProjectionExpression());
        }

        // Generate source for additional expressions
        String sortSource = JavaSourceGenerator.generateJavaSource(transformation.getSortExpression());
        String aggregationSource = JavaSourceGenerator.generateJavaSource(transformation.getAggregationExpression());
        String groupByKeySource = JavaSourceGenerator.generateJavaSource(transformation.getGroupByKeyExpression());
        // Use group-specific generator for having and group select (parameter is Group, not entity)
        String havingSource = JavaSourceGenerator.generateGroupJavaSource(transformation.getHavingExpression());
        String joinRelationshipSource = JavaSourceGenerator.generateJavaSource(transformation.getJoinRelationshipExpression());

        return new QueryData(
                queryId,
                ClassNameUtils.extractSimpleName(entityClassName),
                entityClassName,
                transformation.getGeneratedClassName(),
                getQueryType(transformation),
                transformation.getCapturedVarCount(),
                jpql,
                predicateSource,
                projectionSource,
                extractMethodName(queryId),
                sortSource,
                transformation.isSortDescending(),
                transformation.getAggregationType(),
                aggregationSource,
                groupByKeySource,
                havingSource,
                joinRelationshipSource,
                transformation.getTerminalMethodName(),
                transformation.hasDistinct(),
                transformation.isSelectKey(),
                transformation.getSkipValue(),
                transformation.getLimitValue());
    }

    private String getQueryType(QueryTransformationBuildItem transformation) {
        if (transformation.isGroupQuery()) {
            return transformation.isCountQuery() ? "Group Count" : "Group List";
        }
        if (transformation.isJoinQuery()) {
            if (transformation.isCountQuery()) {
                return "Join Count";
            }
            if (transformation.isSelectJoined()) {
                return "Select Joined";
            }
            if (transformation.isJoinProjection()) {
                return "Join Projection";
            }
            return "Join List";
        }
        if (transformation.isAggregationQuery()) {
            return "Aggregation";
        }
        if (transformation.isCountQuery()) {
            return "Count";
        }
        return "List";
    }

    /** Extracts method name from query ID (format: com.example.MyClass#myMethod[$lambda$N]). */
    private String extractMethodName(String queryId) {
        if (queryId == null || queryId.isEmpty()) {
            return "";
        }
        int hashIndex = queryId.indexOf(QUERY_ID_SEPARATOR);
        if (hashIndex < 0) {
            return "";
        }
        String methodPart = queryId.substring(hashIndex + 1);
        // Remove lambda suffix like $lambda$1
        int dollarIndex = methodPart.indexOf(LAMBDA_SUFFIX_MARKER);
        if (dollarIndex > 0) {
            return methodPart.substring(0, dollarIndex);
        }
        return methodPart;
    }

    /** Query information DTO for DevUI display. */
    public record QueryData(
            String queryId,
            String entityName,
            String entityClassName,
            String generatedClassName,
            String queryType,
            int capturedVariables,
            String jpql,
            String predicateLambda,
            String projectionLambda,
            String callSiteMethod,
            String sortLambda,
            boolean sortDescending,
            String aggregationType,
            String aggregationLambda,
            String groupByKeyLambda,
            String havingLambda,
            String joinRelationshipLambda,
            String terminalMethodName,
            boolean hasDistinct,
            boolean isSelectKey,
            Integer skipValue,
            Integer limitValue) {
    }
}
