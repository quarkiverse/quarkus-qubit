package io.quarkiverse.qubit.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import io.quarkus.logging.Log;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quarkus build processor for native image configuration.
 * <p>
 * Generates GraalVM native image metadata for lambda reflection.
 * When lambdas have captured variables, their synthetic classes need
 * reflection configuration to access fields in native mode.
 * <p>
 * Uses the GraalVM 25+ reachability-metadata.json format with lambda reflection syntax:
 * <pre>
 * {
 *   "reflection": [
 *     {
 *       "type": {
 *         "lambda": {
 *           "declaringClass": "com.example.MyClass",
 *           "interfaces": ["io.quarkiverse.qubit.runtime.QuerySpec"]
 *         }
 *       },
 *       "allDeclaredFields": true
 *     }
 *   ]
 * }
 * </pre>
 * <p>
 * <strong>Note:</strong> Lambda reflection in native mode requires GraalVM/Mandrel 25 or later.
 * Earlier versions do not support reflection on lambda-proxy class fields.
 *
 * @see <a href="https://www.graalvm.org/latest/reference-manual/native-image/metadata/">GraalVM Reachability Metadata</a>
 */
@SuppressWarnings("deprecation")  // NativeOrNativeSourcesBuild is deprecated but still recommended
public class QubitNativeImageProcessor {

    // GraalVM 25+ uses reachability-metadata.json format
    private static final String QUBIT_REACHABILITY_METADATA_PATH =
            "META-INF/native-image/io.quarkiverse.qubit/quarkus-qubit/reachability-metadata.json";

    // Lambda interface class names
    private static final String QUERY_SPEC_CLASS = "io.quarkiverse.qubit.runtime.QuerySpec";
    private static final String BI_QUERY_SPEC_CLASS = "io.quarkiverse.qubit.runtime.BiQuerySpec";
    private static final String GROUP_QUERY_SPEC_CLASS = "io.quarkiverse.qubit.runtime.GroupQuerySpec";

    /**
     * Collects lambda reflection information from query transformations.
     * <p>
     * For each query transformation with captured variables, produces a
     * LambdaReflectionBuildItem that will be used to generate native image
     * reflection configuration.
     */
    @BuildStep
    void collectLambdaReflectionInfo(
            List<QubitProcessor.QueryTransformationBuildItem> transformations,
            BuildProducer<LambdaReflectionBuildItem> lambdaReflections) {

        for (QubitProcessor.QueryTransformationBuildItem transformation : transformations) {
            if (transformation.getCapturedVarCount() <= 0) {
                continue;
            }

            // Parse queryId format: "ownerClassName:methodName:lineNumber"
            String queryId = transformation.getQueryId();
            String[] parts = queryId.split(":");
            if (parts.length < 2) {
                Log.warnf("Qubit: Invalid queryId format: %s", queryId);
                continue;
            }

            String declaringClass = parts[0];
            String methodName = parts[1];

            // Determine interface type based on query characteristics
            String interfaceType = determineInterfaceType(transformation);

            lambdaReflections.produce(new LambdaReflectionBuildItem(
                    declaringClass, methodName, interfaceType, transformation.getCapturedVarCount()));

            Log.debugf("Qubit: Registered lambda reflection for %s.%s (%s, %d captured vars)",
                    declaringClass, methodName, interfaceType, transformation.getCapturedVarCount());
        }
    }

    /**
     * Determines the lambda interface type based on query characteristics.
     */
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
     * Registers runtime classes for reflection.
     * These classes are used by the generated query executors and need
     * to be accessible via reflection in native mode.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void registerRuntimeClassesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        // Register CapturedVariableExtractor for reflection
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.CapturedVariableExtractor")
                .methods()
                .fields()
                .build());

        // Register FieldNamingStrategy implementations
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.quarkiverse.qubit.runtime.FieldNamingStrategy$JavacStrategy",
                "io.quarkiverse.qubit.runtime.FieldNamingStrategy$EclipseStrategy",
                "io.quarkiverse.qubit.runtime.FieldNamingStrategy$GraalVMStrategy",
                "io.quarkiverse.qubit.runtime.FieldNamingStrategy$IndexBasedStrategy")
                .constructors()
                .methods()
                .build());
    }

    /**
     * Generates reachability-metadata.json with lambda reflection entries.
     * <p>
     * Uses GraalVM 25+ format with the "reflection" array wrapper.
     * Only lambdas with captured variables need reflection registration.
     * Lambdas without captured variables don't have fields to access.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void generateLambdaReflectionConfig(
            List<LambdaReflectionBuildItem> lambdaReflections,
            BuildProducer<GeneratedResourceBuildItem> generatedResource) {

        if (lambdaReflections.isEmpty()) {
            Log.debug("Qubit: No lambdas with captured variables found, skipping reachability-metadata.json generation");
            return;
        }

        // Deduplicate by (declaringClass, interfaceType) pair
        Set<String> seen = new HashSet<>();
        StringBuilder json = new StringBuilder();

        // GraalVM 25+ reachability-metadata.json format with "reflection" wrapper
        json.append("{\n");
        json.append("  \"reflection\": [\n");

        boolean first = true;
        for (LambdaReflectionBuildItem item : lambdaReflections) {
            if (item.getCapturedVarCount() <= 0) {
                continue;
            }

            String key = item.getDeclaringClass() + ":" + item.getInterfaceType();
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);

            if (!first) {
                json.append(",\n");
            }
            first = false;

            // Generate lambda reflection entry using GraalVM 25+ syntax
            json.append("    {\n");
            json.append("      \"type\": {\n");
            json.append("        \"lambda\": {\n");
            json.append("          \"declaringClass\": \"").append(item.getDeclaringClass()).append("\",\n");
            json.append("          \"interfaces\": [\"").append(item.getInterfaceType()).append("\"]\n");
            json.append("        }\n");
            json.append("      },\n");
            json.append("      \"allDeclaredFields\": true\n");
            json.append("    }");
        }

        json.append("\n  ]\n");
        json.append("}");

        if (seen.isEmpty()) {
            Log.debug("Qubit: No lambdas with captured variables after deduplication");
            return;
        }

        Log.infof("Qubit: Generating native image lambda reflection config for %d lambda type(s)", seen.size());
        generatedResource.produce(new GeneratedResourceBuildItem(
                QUBIT_REACHABILITY_METADATA_PATH,
                json.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
