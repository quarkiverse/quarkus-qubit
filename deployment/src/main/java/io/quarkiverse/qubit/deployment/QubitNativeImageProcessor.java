package io.quarkiverse.qubit.deployment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
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
            String methodName = parts[1];
            // parts[2] is lineNumber (not used for native config)
            // parts[3] is lambdaMethodName (not used for native config)

            // Validate extracted components
            if (declaringClass.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "Empty declaringClass in queryId: '%s'. This indicates a bug in build-time query analysis.",
                        queryId));
            }
            if (methodName.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "Empty methodName in queryId: '%s'. This indicates a bug in build-time query analysis.",
                        queryId));
            }

            // Determine interface type based on query characteristics
            String interfaceType = determineInterfaceType(transformation);

            // Register ALL lambdas - writeReplace needed for call site ID extraction
            lambdaReflections.produce(new LambdaReflectionBuildItem(
                    declaringClass, methodName, interfaceType, transformation.getCapturedVarCount()));

            Log.debugf("Qubit: Registered lambda reflection for %s.%s (%s, %d captured vars)",
                    declaringClass, methodName, interfaceType, transformation.getCapturedVarCount());
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

    /** Registers runtime classes for native reflection. */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void registerRuntimeClassesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.internal.CapturedVariableExtractor")
                .methods().fields().build());

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.FieldNamingStrategy$JavacStrategy",
                "io.quarkiverse.qubit.FieldNamingStrategy$EclipseStrategy",
                "io.quarkiverse.qubit.FieldNamingStrategy$GraalVMStrategy",
                "io.quarkiverse.qubit.FieldNamingStrategy$IndexBasedStrategy")
                .constructors().methods().build());

        // Uses getDeclaredMethod/getDeclaredFields for lambda extraction
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils")
                .methods().fields().build());

        // CDI bean lookup via Arc.container().instance()
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.internal.QueryExecutorRegistry")
                .constructors().methods().fields().build());

        // Group interface used in group lambda parameter
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.Group")
                .methods().build());

        // Return type for projection queries
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.internal.ImmutableResultStream")
                .constructors().methods().build());

        // Stream implementation classes
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.internal.QubitStreamImpl",
                "io.quarkiverse.qubit.runtime.internal.JoinStreamImpl",
                "io.quarkiverse.qubit.runtime.internal.GroupStreamImpl")
                .constructors().methods().build());
    }

    /**
     * Generates reachability-metadata.json. writeReplace() for all (Issue #14),
     * allDeclaredFields only for lambdas with captured variables.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void generateLambdaReflectionConfig(
            List<LambdaReflectionBuildItem> lambdaReflections,
            BuildProducer<GeneratedResourceBuildItem> generatedResource) {

        if (lambdaReflections.isEmpty()) {
            Log.debug("Qubit: No lambdas found, skipping reachability-metadata.json generation");
            return;
        }

        // Track which (declaringClass, interfaceType) pairs need fields vs just methods
        // Key: "declaringClass:interfaceType", Value: true if needs fields (captured vars)
        java.util.Map<String, Boolean> lambdaEntries = new java.util.LinkedHashMap<>();
        for (LambdaReflectionBuildItem item : lambdaReflections) {
            String key = item.getDeclaringClass() + ":" + item.getInterfaceType();
            // If any lambda with this key has captured vars, mark as needing fields
            Boolean needsFields = lambdaEntries.getOrDefault(key, Boolean.FALSE);
            if (item.getCapturedVarCount() > 0) {
                needsFields = Boolean.TRUE;
            }
            lambdaEntries.put(key, needsFields);
        }

        StringBuilder json = new StringBuilder();

        // GraalVM 25+ reachability-metadata.json format with "reflection" wrapper
        json.append("{\n");
        json.append("  \"reflection\": [\n");

        boolean first = true;
        for (java.util.Map.Entry<String, Boolean> entry : lambdaEntries.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String declaringClass = parts[0];
            String interfaceType = parts[1];
            boolean needsFields = entry.getValue();

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
            json.append("      ]");

            // allDeclaredFields needed only for lambdas with captured variables
            if (needsFields) {
                json.append(",\n      \"allDeclaredFields\": true\n");
            } else {
                json.append("\n");
            }

            json.append("    }");
        }

        json.append("\n  ]\n");
        json.append("}");

        Log.infof("Qubit: Generating native image lambda reflection config for %d lambda type(s)", lambdaEntries.size());
        generatedResource.produce(new GeneratedResourceBuildItem(
                QUBIT_REACHABILITY_METADATA_PATH,
                json.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
