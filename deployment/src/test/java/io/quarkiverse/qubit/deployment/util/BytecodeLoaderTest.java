package io.quarkiverse.qubit.deployment.util;

import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.paths.PathList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BytecodeLoader}.
 *
 * <p>Tests bytecode loading from classloader fallback path.
 */
class BytecodeLoaderTest {

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
        void loadClassBytecode_withNonExistentClass_returnsEmptyArray() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "com.nonexistent.NonExistentClass", emptyArchives);

            assertThat(result)
                    .as("Non-existent class should return empty array")
                    .isEmpty();
        }

        @Test
        void loadClassBytecode_withInvalidClassName_returnsEmptyArray() {
            ApplicationArchivesBuildItem emptyArchives = mock(ApplicationArchivesBuildItem.class);
            when(emptyArchives.getAllApplicationArchives()).thenReturn(Set.of());

            byte[] result = BytecodeLoader.loadClassBytecode(
                    "not.a.valid.class!", emptyArchives);

            assertThat(result)
                    .as("Invalid class name should return empty array")
                    .isEmpty();
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
}
