package io.quarkiverse.qubit.deployment;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.util.List;
import java.util.Optional;

/**
 * Build-time configuration for the QUBIT extension.
 * @see <a href="https://quarkus.io/guides/writing-extensions">Quarkus Extension Writing Guide</a>
 */
@ConfigMapping(prefix = "quarkus.qubit")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface QubitBuildTimeConfig {

    /**
     * Scanning configuration for lambda analysis.
     */
    ScanningConfig scanning();

    /**
     * Code generation configuration.
     */
    GenerationConfig generation();

    /**
     * Logging configuration for build-time processing.
     */
    LoggingConfig logging();

    /**
     * Configuration for package scanning during lambda analysis.
     */
    interface ScanningConfig {

        /**
         * Package prefixes to exclude from lambda scanning.
         * Classes in these packages will be skipped during build-time analysis.
         * <p>
         * Default excludes: {@code java.}, {@code jakarta.}
         */
        @WithDefault("java.,jakarta.")
        List<String> excludePackages();

        /**
         * Additional package prefixes to include (overrides exclude).
         * Useful for including specific framework packages that contain lambda queries.
         * <p>
         * If a class matches both exclude and include, include takes precedence.
         */
        Optional<List<String>> includePackages();

        /**
         * Whether to scan test classes (classes in paths containing .it. or .test.).
         * <p>
         * When enabled, integration test classes are analyzed for lambda queries.
         */
        @WithDefault("true")
        boolean scanTestClasses();
    }

    /**
     * Configuration for query executor class generation.
     */
    interface GenerationConfig {

        /**
         * Prefix for generated executor class names.
         * <p>
         * Generated classes will be named: {@code {targetPackage}.{classNamePrefix}{hash}}
         * <p>
         * Default: {@code QueryExecutor_}
         */
        @WithDefault("QueryExecutor_")
        String classNamePrefix();

        /**
         * Package for generated executor classes.
         * <p>
         * If not specified, classes are generated in {@code io.quarkiverse.qubit.generated}.
         */
        Optional<String> targetPackage();
    }

    /**
     * Logging configuration for QUBIT build-time processing.
     * <p>
     * These settings control the verbosity of build-time log output
     * for debugging and troubleshooting lambda analysis.
     */
    interface LoggingConfig {

        /**
         * Log level for QUBIT build processing.
         * <p>
         * Options: OFF, ERROR, WARN, INFO, DEBUG, TRACE
         */
        @WithDefault("INFO")
        String level();

        /**
         * Log each scanned class (very verbose).
         * <p>
         * Enable for debugging class scanning issues.
         */
        @WithDefault("false")
        boolean logScannedClasses();

        /**
         * Log each generated executor class.
         * <p>
         * Useful for tracking which query executors are created.
         */
        @WithDefault("true")
        boolean logGeneratedClasses();

        /**
         * Log lambda deduplication events.
         * <p>
         * Shows when identical lambdas reuse existing executors.
         */
        @WithDefault("false")
        boolean logDeduplication();

        /**
         * Log detailed bytecode analysis steps (TRACE-level detail).
         * <p>
         * Enable for debugging bytecode parsing issues.
         */
        @WithDefault("false")
        boolean logBytecodeAnalysis();
    }
}
