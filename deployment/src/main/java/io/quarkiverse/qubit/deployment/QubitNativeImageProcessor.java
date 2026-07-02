package io.quarkiverse.qubit.deployment;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.stream.JsonGenerator;

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

        // GraalVM 25+ reachability-metadata.json format
        JsonArrayBuilder reflection = Json.createArrayBuilder();
        for (String key : seen) {
            String[] parts = key.split(":");
            reflection.add(Json.createObjectBuilder()
                    .add("type", Json.createObjectBuilder()
                            .add("lambda", Json.createObjectBuilder()
                                    .add("declaringClass", parts[0])
                                    .add("interfaces", Json.createArrayBuilder().add(parts[1]))))
                    .add("methods", Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add("name", "writeReplace")
                                    .add("parameterTypes", Json.createArrayBuilder()))));
        }

        var writerFactory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
        var sw = new StringWriter();
        try (var writer = writerFactory.createWriter(sw)) {
            writer.write(Json.createObjectBuilder().add("reflection", reflection).build());
        }

        Log.infof("Qubit: Generating native image lambda reflection config for %d lambda type(s)", seen.size());
        generatedResource.produce(new GeneratedResourceBuildItem(
                QUBIT_REACHABILITY_METADATA_PATH,
                sw.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
