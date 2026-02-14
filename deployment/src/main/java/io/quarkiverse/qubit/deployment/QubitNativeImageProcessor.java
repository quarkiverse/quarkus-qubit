package io.quarkiverse.qubit.deployment;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import io.quarkus.logging.Log;

/**
 * Generates GraalVM native image metadata for lambda reflection.
 * Uses GraalVM 25+ reachability-metadata.json format. Earlier versions don't support lambda reflection.
 */
@SuppressWarnings("deprecation") // NativeOrNativeSourcesBuild is deprecated but still recommended
public class QubitNativeImageProcessor {

    private static final String QUBIT_REACHABILITY_METADATA_PATH = "META-INF/native-image/io.quarkiverse.qubit/quarkus-qubit/reachability-metadata.json";

    private static final String QUERY_SPEC_CLASS = "io.quarkiverse.qubit.QuerySpec";
    private static final String BI_QUERY_SPEC_CLASS = "io.quarkiverse.qubit.BiQuerySpec";
    private static final String GROUP_QUERY_SPEC_CLASS = "io.quarkiverse.qubit.GroupQuerySpec";

    /** Format: "ownerClassName:methodName:lineNumber:lambdaMethodName" */
    private static final int QUERY_ID_MIN_PARTS = 4;

    /**
     * Collects lambda reflection info. ALL lambdas need writeReplace() for call site ID (Issue #14).
     * Fail-fast on invalid queryId (Issue #16) - prevents cryptic native runtime errors.
     */
    @BuildStep
    void collectLambdaReflectionInfo(
            List<QubitProcessor.QueryTransformationBuildItem> transformations,
            BuildProducer<LambdaReflectionBuildItem> lambdaReflections) {

        for (QubitProcessor.QueryTransformationBuildItem transformation : transformations) {
            // Parse queryId format: "ownerClassName:methodName:lineNumber:lambdaMethodName"
            String queryId = transformation.getQueryId();
            String[] parts = queryId.split(":");

            // Validate queryId format strictly - fail build on invalid format
            // Silent skip would cause missing native image metadata, leading to
            // cryptic runtime failures in native mode
            if (parts.length < QUERY_ID_MIN_PARTS) {
                throw new IllegalStateException(String.format(
                        "Invalid queryId format: '%s' (expected %d parts separated by ':' but got %d).%n" +
                                "Expected format: 'ownerClassName:methodName:lineNumber:lambdaMethodName'.%n" +
                                "This indicates a bug in build-time query analysis.%n" +
                                "Please report this issue with the full stack trace.",
                        queryId, QUERY_ID_MIN_PARTS, parts.length));
            }

            String declaringClass = parts[0];

            // Validate declaring class
            if (declaringClass.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "Empty declaringClass in queryId: '%s'. This indicates a bug in build-time query analysis.",
                        queryId));
            }

            // Determine interface type based on query characteristics
            String interfaceType = determineInterfaceType(transformation);

            // Register ALL lambdas - writeReplace needed for call site ID extraction
            lambdaReflections.produce(new LambdaReflectionBuildItem(declaringClass, interfaceType));

            Log.debugf("Qubit: Registered lambda reflection for %s (%s)", declaringClass, interfaceType);
        }
    }

    /** Determines lambda interface type: BiQuerySpec, GroupQuerySpec, or QuerySpec. */
    private String determineInterfaceType(QubitProcessor.QueryTransformationBuildItem transformation) {
        if (transformation.isJoinQuery()) {
            return BI_QUERY_SPEC_CLASS;
        }
        if (transformation.isGroupQuery()) {
            return GROUP_QUERY_SPEC_CLASS;
        }
        return QUERY_SPEC_CLASS;
    }

    /**
     * Generates reachability-metadata.json. writeReplace() needed for all lambdas (Issue #14).
     * Field reflection is no longer needed — captured variables are extracted via
     * {@code SerializedLambda.getCapturedArg()} instead of compiler-specific field names.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void generateLambdaReflectionConfig(
            List<LambdaReflectionBuildItem> lambdaReflections,
            BuildProducer<GeneratedResourceBuildItem> generatedResource) {

        if (lambdaReflections.isEmpty()) {
            Log.debug("Qubit: No lambdas found, skipping reachability-metadata.json generation");
            return;
        }

        // Deduplicate by (declaringClass, interfaceType)
        Set<String> seen = new LinkedHashSet<>();
        for (LambdaReflectionBuildItem item : lambdaReflections) {
            seen.add(item.getDeclaringClass() + ":" + item.getInterfaceType());
        }

        StringBuilder json = new StringBuilder();

        // GraalVM 25+ reachability-metadata.json format with "reflection" wrapper
        json.append("{\n");
        json.append("  \"reflection\": [\n");

        boolean first = true;
        for (String key : seen) {
            String[] parts = key.split(":");
            String declaringClass = parts[0];
            String interfaceType = parts[1];

            if (!first) {
                json.append(",\n");
            }
            first = false;

            // Generate lambda reflection entry using GraalVM 25+ syntax
            json.append("    {\n");
            json.append("      \"type\": {\n");
            json.append("        \"lambda\": {\n");
            json.append("          \"declaringClass\": \"").append(declaringClass).append("\",\n");
            json.append("          \"interfaces\": [\"").append(interfaceType).append("\"]\n");
            json.append("        }\n");
            json.append("      },\n");

            // writeReplace method needed for SerializedLambda extraction (Issue #14)
            json.append("      \"methods\": [\n");
            json.append("        { \"name\": \"writeReplace\", \"parameterTypes\": [] }\n");
            json.append("      ]\n");

            json.append("    }");
        }

        json.append("\n  ]\n");
        json.append("}");

        Log.infof("Qubit: Generating native image lambda reflection config for %d lambda type(s)", seen.size());
        generatedResource.produce(new GeneratedResourceBuildItem(
                QUBIT_REACHABILITY_METADATA_PATH,
                json.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
