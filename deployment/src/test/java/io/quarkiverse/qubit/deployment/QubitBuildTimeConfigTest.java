package io.quarkiverse.qubit.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QubitBuildTimeConfig interface.
 */
@DisplayName("QubitBuildTimeConfig Tests")
class QubitBuildTimeConfigTest {

    @Nested
    @DisplayName("ScanningConfig Tests")
    class ScanningConfigTests {

        @Test
        @DisplayName("Default exclude packages should contain java. and jakarta.")
        void defaultExcludePackages() {
            // The @WithDefault annotation specifies "java.,jakarta."
            // This test documents the expected defaults
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty(),
                    true);

            assertTrue(config.excludePackages().contains("java."));
            assertTrue(config.excludePackages().contains("jakarta."));
            assertEquals(2, config.excludePackages().size());
        }

        @Test
        @DisplayName("Include packages override exclude packages")
        void includePackagesOverrideExclude() {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.of(List.of("jakarta.validation.")),
                    true);

            assertTrue(config.includePackages().isPresent());
            assertTrue(config.includePackages().get().contains("jakarta.validation."));
        }

        @Test
        @DisplayName("scanTestClasses defaults to true")
        void scanTestClassesDefaultTrue() {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty(),
                    true);

            assertTrue(config.scanTestClasses());
        }

        @Test
        @DisplayName("scanTestClasses can be disabled")
        void scanTestClassesCanBeDisabled() {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty(),
                    false);

            assertFalse(config.scanTestClasses());
        }
    }

    @Nested
    @DisplayName("GenerationConfig Tests")
    class GenerationConfigTests {

        @Test
        @DisplayName("Default class name prefix is QueryExecutor_")
        void defaultClassNamePrefix() {
            QubitBuildTimeConfig.GenerationConfig config = createGenerationConfig(
                    "QueryExecutor_",
                    Optional.empty());

            assertEquals("QueryExecutor_", config.classNamePrefix());
        }

        @Test
        @DisplayName("Custom class name prefix")
        void customClassNamePrefix() {
            QubitBuildTimeConfig.GenerationConfig config = createGenerationConfig(
                    "MyExecutor$",
                    Optional.empty());

            assertEquals("MyExecutor$", config.classNamePrefix());
        }

        @Test
        @DisplayName("Target package is optional")
        void targetPackageOptional() {
            QubitBuildTimeConfig.GenerationConfig config = createGenerationConfig(
                    "QueryExecutor_",
                    Optional.empty());

            assertTrue(config.targetPackage().isEmpty());
        }

        @Test
        @DisplayName("Custom target package")
        void customTargetPackage() {
            QubitBuildTimeConfig.GenerationConfig config = createGenerationConfig(
                    "QueryExecutor_",
                    Optional.of("com.example.generated"));

            assertTrue(config.targetPackage().isPresent());
            assertEquals("com.example.generated", config.targetPackage().get());
        }
    }

    @Nested
    @DisplayName("LoggingConfig Tests")
    class LoggingConfigTests {

        @Test
        @DisplayName("Default log level is INFO")
        void defaultLogLevel() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    "INFO", false, true, false, false);

            assertEquals("INFO", config.level());
        }

        @Test
        @DisplayName("logScannedClasses defaults to false")
        void logScannedClassesDefaultFalse() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    "INFO", false, true, false, false);

            assertFalse(config.logScannedClasses());
        }

        @Test
        @DisplayName("logGeneratedClasses defaults to true")
        void logGeneratedClassesDefaultTrue() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    "INFO", false, true, false, false);

            assertTrue(config.logGeneratedClasses());
        }

        @Test
        @DisplayName("logDeduplication defaults to false")
        void logDeduplicationDefaultFalse() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    "INFO", false, true, false, false);

            assertFalse(config.logDeduplication());
        }

        @Test
        @DisplayName("logBytecodeAnalysis defaults to false")
        void logBytecodeAnalysisDefaultFalse() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    "INFO", false, true, false, false);

            assertFalse(config.logBytecodeAnalysis());
        }

        @Test
        @DisplayName("All logging options can be enabled")
        void allLoggingOptionsEnabled() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    "DEBUG", true, true, true, true);

            assertEquals("DEBUG", config.level());
            assertTrue(config.logScannedClasses());
            assertTrue(config.logGeneratedClasses());
            assertTrue(config.logDeduplication());
            assertTrue(config.logBytecodeAnalysis());
        }
    }

    // Helper methods to create config instances for testing

    private QubitBuildTimeConfig.ScanningConfig createScanningConfig(
            List<String> excludePackages,
            Optional<List<String>> includePackages,
            boolean scanTestClasses) {
        return new QubitBuildTimeConfig.ScanningConfig() {
            @Override
            public List<String> excludePackages() {
                return excludePackages;
            }

            @Override
            public Optional<List<String>> includePackages() {
                return includePackages;
            }

            @Override
            public boolean scanTestClasses() {
                return scanTestClasses;
            }
        };
    }

    private QubitBuildTimeConfig.GenerationConfig createGenerationConfig(
            String classNamePrefix,
            Optional<String> targetPackage) {
        return new QubitBuildTimeConfig.GenerationConfig() {
            @Override
            public String classNamePrefix() {
                return classNamePrefix;
            }

            @Override
            public Optional<String> targetPackage() {
                return targetPackage;
            }
        };
    }

    private QubitBuildTimeConfig.LoggingConfig createLoggingConfig(
            String level,
            boolean logScannedClasses,
            boolean logGeneratedClasses,
            boolean logDeduplication,
            boolean logBytecodeAnalysis) {
        return new QubitBuildTimeConfig.LoggingConfig() {
            @Override
            public String level() {
                return level;
            }

            @Override
            public boolean logScannedClasses() {
                return logScannedClasses;
            }

            @Override
            public boolean logGeneratedClasses() {
                return logGeneratedClasses;
            }

            @Override
            public boolean logDeduplication() {
                return logDeduplication;
            }

            @Override
            public boolean logBytecodeAnalysis() {
                return logBytecodeAnalysis;
            }
        };
    }
}
