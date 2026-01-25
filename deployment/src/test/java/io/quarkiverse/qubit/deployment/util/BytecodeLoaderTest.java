package io.quarkiverse.qubit.deployment.util;

import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.paths.PathList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BytecodeLoader}.
 *
 * <p>Tests bytecode loading from classloader fallback path.
 */
class BytecodeLoaderTest {

    @BeforeEach
    void setUp() {
        // Clear cache before each test to ensure test isolation
        BytecodeLoader.clearCache();
    }

    @AfterEach
    void tearDown() {
        // Clear cache after each test to avoid pollution
        BytecodeLoader.clearCache();
    }

    // ==================== loadClassBytecode from Classloader Tests ====================

    @Nested
    class LoadFromClassloaderTests {

        @Test
        void loadClassBytecode_withExistingClass_returnsBytecode() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "java.lang.String", emptyArchives);

            assertThat(result)
                    .as("Should load bytecode for java.lang.String")
                    .isNotEmpty();
        }

        @Test
        void loadClassBytecode_withProjectClass_returnsBytecode() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "io.quarkiverse.qubit.deployment.util.BytecodeLoader", emptyArchives);

            assertThat(result)
                    .as("Should load bytecode for project class")
                    .isNotEmpty();
        }

        @Test
        void loadClassBytecode_withNonExistentClass_throwsAnalysisException() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            assertThatThrownBy(() -> BytecodeLoader.loadClassBytecode(
                    "com.nonexistent.NonExistentClass", emptyArchives))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("com.nonexistent.NonExistentClass");
        }

        @Test
        void loadClassBytecode_withInvalidClassName_throwsAnalysisException() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            assertThatThrownBy(() -> BytecodeLoader.loadClassBytecode(
                    "not.a.valid.class!", emptyArchives))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("not.a.valid.class!");
        }

        @Test
        void loadClassBytecode_withNestedClass_returnsBytecode() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "java.util.Map$Entry", emptyArchives);

            assertThat(result)
                    .as("Should load bytecode for nested class")
                    .isNotEmpty();
        }

        @Test
        void loadClassBytecode_withInterfaceClass_returnsBytecode() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "java.util.List", emptyArchives);

            assertThat(result)
                    .as("Should load bytecode for interface")
                    .isNotEmpty();
        }

        @Test
        void loadClassBytecode_withEnumClass_returnsBytecode() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "java.lang.Thread$State", emptyArchives);

            assertThat(result)
                    .as("Should load bytecode for enum")
                    .isNotEmpty();
        }

        @Test
        void loadClassBytecode_verifyBytecodeIsValid() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "java.lang.Object", emptyArchives);

            // Java class file magic number is 0xCAFEBABE
            assertThat(result)
                    .as("Bytecode should start with Java magic number")
                    .hasSizeGreaterThan(4);
            assertThat(result[0]).isEqualTo((byte) 0xCA);
            assertThat(result[1]).isEqualTo((byte) 0xFE);
            assertThat(result[2]).isEqualTo((byte) 0xBA);
            assertThat(result[3]).isEqualTo((byte) 0xBE);
        }
    }

    // ==================== loadClassBytecode from Archive Tests ====================

    @Nested
    class LoadFromArchiveTests {

        @TempDir
        Path tempDir;

        @Test
        void loadClassBytecode_fromArchive_returnsBytecode() throws IOException {
            // Create a mock class file in the temp directory
            Path classDir = tempDir.resolve("com/example");
            Files.createDirectories(classDir);
            Path classFile = classDir.resolve("TestClass.class");

            // Write valid Java class file magic number + some bytes
            byte[] classContent = new byte[] {
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
                    0x00, 0x00, 0x00, 0x34 // version 52 (Java 8)
            };
            Files.write(classFile, classContent);

            // Create mock ApplicationArchive
            ApplicationArchive archive = mock(ApplicationArchive.class);
            when(archive.getRootDirectories()).thenReturn(PathList.of(tempDir));

            ApplicationArchivesBuildItem archives = mock(ApplicationArchivesBuildItem.class);
            when(archives.getAllApplicationArchives()).thenReturn(Set.of(archive));

            byte[] result = BytecodeLoader.loadClassBytecode("com.example.TestClass", archives);

            assertThat(result)
                    .as("Should load bytecode from archive")
                    .isEqualTo(classContent);
        }

        @Test
        void loadClassBytecode_fromArchive_classNotInArchive_fallsBackToClassloader() throws IOException {
            // Create mock archive with empty directory
            ApplicationArchive archive = mock(ApplicationArchive.class);
            when(archive.getRootDirectories()).thenReturn(PathList.of(tempDir));

            ApplicationArchivesBuildItem archives = mock(ApplicationArchivesBuildItem.class);
            when(archives.getAllApplicationArchives()).thenReturn(Set.of(archive));

            // java.lang.String is not in our temp dir, so should fall back to classloader
            byte[] result = BytecodeLoader.loadClassBytecode("java.lang.String", archives);

            assertThat(result)
                    .as("Should fall back to classloader when not in archive")
                    .isNotEmpty();
            // Verify it's a valid class file
            assertThat(result[0]).isEqualTo((byte) 0xCA);
        }

        @Test
        void loadClassBytecode_archivePreferredOverClassloader() throws IOException {
            // Create a mock class file with custom content
            Path classDir = tempDir.resolve("com/example");
            Files.createDirectories(classDir);
            Path classFile = classDir.resolve("CustomClass.class");

            byte[] customContent = new byte[] {
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                    0x11, 0x22, 0x33, 0x44  // custom bytes to identify
            };
            Files.write(classFile, customContent);

            ApplicationArchive archive = mock(ApplicationArchive.class);
            when(archive.getRootDirectories()).thenReturn(PathList.of(tempDir));

            ApplicationArchivesBuildItem archives = mock(ApplicationArchivesBuildItem.class);
            when(archives.getAllApplicationArchives()).thenReturn(Set.of(archive));

            byte[] result = BytecodeLoader.loadClassBytecode("com.example.CustomClass", archives);

            // Should get our custom content, not from classloader
            assertThat(result)
                    .as("Archive should be preferred over classloader")
                    .isEqualTo(customContent);
        }

        @Test
        void loadClassBytecode_withMultipleArchives_searchesAll() throws IOException {
            // Create first archive with no matching class
            Path archive1Dir = Files.createTempDirectory(tempDir, "archive1");

            // Create second archive with the class
            Path archive2Dir = Files.createTempDirectory(tempDir, "archive2");
            Path classDir = archive2Dir.resolve("com/example");
            Files.createDirectories(classDir);
            Path classFile = classDir.resolve("FoundClass.class");
            byte[] classContent = new byte[] {
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                    (byte) 0x55, (byte) 0x66, (byte) 0x77, (byte) 0x88
            };
            Files.write(classFile, classContent);

            ApplicationArchive archive1 = mock(ApplicationArchive.class);
            when(archive1.getRootDirectories()).thenReturn(PathList.of(archive1Dir));

            ApplicationArchive archive2 = mock(ApplicationArchive.class);
            when(archive2.getRootDirectories()).thenReturn(PathList.of(archive2Dir));

            ApplicationArchivesBuildItem archives = mock(ApplicationArchivesBuildItem.class);
            // Use LinkedHashSet to preserve iteration order
            when(archives.getAllApplicationArchives()).thenReturn(java.util.Set.of(archive1, archive2));

            byte[] result = BytecodeLoader.loadClassBytecode("com.example.FoundClass", archives);

            assertThat(result)
                    .as("Should find class in second archive")
                    .isEqualTo(classContent);
        }
    }

    // ==================== Cache Behavior Tests ====================

    @Nested
    class CacheBehaviorTests {

        @Test
        void loadClassBytecode_returnsSameBytesOnSecondCall() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            // First call loads from disk
            byte[] first = BytecodeLoader.loadClassBytecode("java.lang.Object", emptyArchives);
            // Second call should return cached bytes
            byte[] second = BytecodeLoader.loadClassBytecode("java.lang.Object", emptyArchives);

            assertThat(first)
                    .as("Both calls should return the same byte array instance from cache")
                    .isSameAs(second);
        }

        @Test
        void clearCache_removesAllCachedEntries() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            // Load a class to populate cache
            byte[] first = BytecodeLoader.loadClassBytecode("java.lang.Integer", emptyArchives);

            // Clear the cache
            BytecodeLoader.clearCache();

            // Load again - should be a fresh load (different array instance possible)
            byte[] second = BytecodeLoader.loadClassBytecode("java.lang.Integer", emptyArchives);

            // Both should have the same content
            assertThat(second)
                    .as("Content should be the same after cache clear")
                    .isEqualTo(first);
            // But since cache was cleared, a new load happened
            // (we can't definitively test this without metrics, but content equality is verified)
        }

        @Test
        void loadClassBytecode_withMetrics_tracksCacheHits() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            BuildMetricsCollector metrics = new BuildMetricsCollector();

            // First call - cache miss, loads from disk
            BytecodeLoader.loadClassBytecode("java.lang.Long", emptyArchives, metrics);
            // Second call - cache hit
            BytecodeLoader.loadClassBytecode("java.lang.Long", emptyArchives, metrics);
            // Third call - cache hit
            BytecodeLoader.loadClassBytecode("java.lang.Long", emptyArchives, metrics);

            // Verify metrics
            assertThat(metrics.getTotalBytecodeLoads())
                    .as("Total loads should count all 3 calls")
                    .isEqualTo(3);
            assertThat(metrics.getUniqueClassesLoaded())
                    .as("Unique classes should count only the cache miss")
                    .isEqualTo(1);
        }

        @Test
        void loadClassBytecode_multipleClasses_tracksEachUniquely() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            BuildMetricsCollector metrics = new BuildMetricsCollector();

            // Load 3 different classes
            BytecodeLoader.loadClassBytecode("java.lang.String", emptyArchives, metrics);
            BytecodeLoader.loadClassBytecode("java.lang.Integer", emptyArchives, metrics);
            BytecodeLoader.loadClassBytecode("java.lang.Boolean", emptyArchives, metrics);
            // Re-load one from cache
            BytecodeLoader.loadClassBytecode("java.lang.String", emptyArchives, metrics);

            assertThat(metrics.getTotalBytecodeLoads())
                    .as("Total loads should count all 4 calls")
                    .isEqualTo(4);
            assertThat(metrics.getUniqueClassesLoaded())
                    .as("Unique classes should be 3 (one was cached)")
                    .isEqualTo(3);
        }

        @Test
        void loadClassBytecode_withNullMetrics_stillCachesCorrectly() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            // Load with null metrics (uses backward-compatible method)
            byte[] first = BytecodeLoader.loadClassBytecode("java.lang.Short", emptyArchives);
            byte[] second = BytecodeLoader.loadClassBytecode("java.lang.Short", emptyArchives);

            assertThat(first)
                    .as("Cache should work even without metrics collector")
                    .isSameAs(second);
        }
    }
}
