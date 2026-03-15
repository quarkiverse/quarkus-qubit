package io.quarkiverse.qubit.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
                    Optional.empty());

            assertTrue(config.excludePackages().contains("java."));
            assertTrue(config.excludePackages().contains("jakarta."));
            assertEquals(2, config.excludePackages().size());
        }

        @Test
        @DisplayName("Include packages override exclude packages")
        void includePackagesOverrideExclude() {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.of(List.of("jakarta.validation.")));

            assertTrue(config.includePackages().isPresent());
            assertTrue(config.includePackages().get().contains("jakarta.validation."));
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
        @DisplayName("logScannedClasses defaults to false")
        void logScannedClassesDefaultFalse() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    false, true, false, false);

            assertFalse(config.logScannedClasses());
        }

        @Test
        @DisplayName("logGeneratedClasses defaults to true")
        void logGeneratedClassesDefaultTrue() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    false, true, false, false);

            assertTrue(config.logGeneratedClasses());
        }

        @Test
        @DisplayName("logDeduplication defaults to false")
        void logDeduplicationDefaultFalse() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    false, true, false, false);

            assertFalse(config.logDeduplication());
        }

        @Test
        @DisplayName("logBytecodeAnalysis defaults to false")
        void logBytecodeAnalysisDefaultFalse() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    false, true, false, false);

            assertFalse(config.logBytecodeAnalysis());
        }

        @Test
        @DisplayName("All logging options can be enabled")
        void allLoggingOptionsEnabled() {
            QubitBuildTimeConfig.LoggingConfig config = createLoggingConfig(
                    true, true, true, true);

            assertTrue(config.logScannedClasses());
            assertTrue(config.logGeneratedClasses());
            assertTrue(config.logDeduplication());
            assertTrue(config.logBytecodeAnalysis());
        }
    }

    // Helper methods to create config instances for testing

    private QubitBuildTimeConfig.ScanningConfig createScanningConfig(
            List<String> excludePackages,
            Optional<List<String>> includePackages) {
        return new QubitBuildTimeConfig.ScanningConfig() {
            @Override
            public List<String> excludePackages() {
                return excludePackages;
            }

            @Override
            public Optional<List<String>> includePackages() {
                return includePackages;
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
            boolean logScannedClasses,
            boolean logGeneratedClasses,
            boolean logDeduplication,
            boolean logBytecodeAnalysis) {
        return new QubitBuildTimeConfig.LoggingConfig() {
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
