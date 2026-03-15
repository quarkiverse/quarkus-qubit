package io.quarkiverse.qubit.deployment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for package filtering logic in QubitProcessor.
 *
 * <p>
 * After the getKnownUsers() migration, the filtering is simplified:
 * no isTestClass() heuristic, no io.quarkus.* special case, no scanTestClasses config.
 */
@DisplayName("Package Filtering Tests")
class PackageFilteringTest {

    private final QubitProcessor processor = new QubitProcessor();

    @Nested
    @DisplayName("Exclude Packages Tests")
    class ExcludePackagesTests {

        @Test
        @DisplayName("java.* classes are excluded by default")
        void javaClassesExcludedByDefault() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertFalse(isNotExcludedClass("java.lang.String", config));
            assertFalse(isNotExcludedClass("java.util.List", config));
            assertFalse(isNotExcludedClass("java.time.LocalDate", config));
        }

        @Test
        @DisplayName("jakarta.* classes are excluded by default")
        void jakartaClassesExcludedByDefault() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertFalse(isNotExcludedClass("jakarta.persistence.Entity", config));
            assertFalse(isNotExcludedClass("jakarta.enterprise.context.ApplicationScoped", config));
        }

        @Test
        @DisplayName("Custom exclude packages are respected")
        void customExcludePackagesRespected() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta.", "com.legacy."),
                    Optional.empty());

            assertFalse(isNotExcludedClass("com.legacy.OldClass", config));
            assertFalse(isNotExcludedClass("com.legacy.deep.Nested", config));
        }

        @Test
        @DisplayName("Application classes are included")
        void applicationClassesIncluded() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertTrue(isNotExcludedClass("com.example.MyService", config));
            assertTrue(isNotExcludedClass("org.acme.entity.Person", config));
        }
    }

    @Nested
    @DisplayName("Include Packages Override Tests")
    class IncludePackagesOverrideTests {

        @Test
        @DisplayName("Include packages override exclude packages")
        void includeOverridesExclude() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.of(List.of("jakarta.validation.")));

            // jakarta.validation.* should be included even though jakarta.* is excluded
            assertTrue(isNotExcludedClass("jakarta.validation.constraints.NotNull", config));
            // Other jakarta.* classes should still be excluded
            assertFalse(isNotExcludedClass("jakarta.persistence.Entity", config));
        }

        @Test
        @DisplayName("Multiple include packages work correctly")
        void multipleIncludePackages() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta.", "org.thirdparty."),
                    Optional.of(List.of("java.time.", "org.thirdparty.good.")));

            // Included packages should work
            assertTrue(isNotExcludedClass("java.time.LocalDate", config));
            assertTrue(isNotExcludedClass("org.thirdparty.good.Helper", config));

            // Other excluded packages should remain excluded
            assertFalse(isNotExcludedClass("java.util.List", config));
            assertFalse(isNotExcludedClass("org.thirdparty.bad.Other", config));
        }
    }

    @Nested
    @DisplayName("Qubit Extension Classes Tests")
    class QubitExtensionClassesTests {

        @Test
        @DisplayName("io.quarkiverse.qubit.* classes are always included")
        void qubitExtensionClassesAlwaysIncluded() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertTrue(isNotExcludedClass("io.quarkiverse.qubit.QubitEntity", config));
            assertTrue(isNotExcludedClass("io.quarkiverse.qubit.api.QueryStream", config));
        }
    }

    // Helper method to invoke the private isNotExcludedClass method
    private boolean isNotExcludedClass(String className, QubitBuildTimeConfig.ScanningConfig config) throws Exception {
        ClassInfo classInfo = mock(ClassInfo.class);
        when(classInfo.name()).thenReturn(DotName.createSimple(className));

        Method method = QubitProcessor.class.getDeclaredMethod("isNotExcludedClass", ClassInfo.class,
                QubitBuildTimeConfig.ScanningConfig.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(processor, classInfo, config);
    }

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
}
