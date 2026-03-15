package io.quarkiverse.qubit.deployment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the getKnownUsers()-based class discovery in QubitProcessor.
 */
@DisplayName("getKnownUsers() Class Discovery Tests")
class KnownUsersDiscoveryTest {

    private static final DotName QUERY_SPEC = DotName.createSimple("io.quarkiverse.qubit.QuerySpec");
    private static final DotName BI_QUERY_SPEC = DotName.createSimple("io.quarkiverse.qubit.BiQuerySpec");
    private static final DotName GROUP_QUERY_SPEC = DotName.createSimple("io.quarkiverse.qubit.GroupQuerySpec");
    private static final DotName QUBIT_ENTITY = DotName.createSimple("io.quarkiverse.qubit.QubitEntity");
    private static final DotName QUBIT_REPOSITORY = DotName.createSimple("io.quarkiverse.qubit.QubitRepository");

    private final QubitProcessor processor = new QubitProcessor();

    /** Creates an IndexView mock with empty defaults for entity/repository lookups. */
    private IndexView mockIndexWithDefaults() {
        IndexView index = mock(IndexView.class);
        when(index.getKnownUsers(any(DotName.class))).thenReturn(List.of());
        when(index.getAllKnownSubclasses(QUBIT_ENTITY)).thenReturn(List.of());
        when(index.getAllKnownImplementations(QUBIT_REPOSITORY)).thenReturn(List.of());
        return index;
    }

    @Nested
    @DisplayName("discoverQubitUserClasses")
    class DiscoverQubitUserClassesTests {

        @Test
        @DisplayName("returns classes that reference QuerySpec")
        void returnsQuerySpecUsers() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo productResource = mockClassInfo("com.example.resource.ProductResource");
            when(index.getKnownUsers(QUERY_SPEC)).thenReturn(List.of(productResource));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            assertTrue(result.stream().anyMatch(c -> c.name().toString().equals("com.example.resource.ProductResource")));
        }

        @Test
        @DisplayName("returns union of all three spec type users")
        void returnsUnionOfAllSpecTypes() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo simpleUser = mockClassInfo("com.example.SimpleQuery");
            ClassInfo joinUser = mockClassInfo("com.example.JoinQuery");
            ClassInfo groupUser = mockClassInfo("com.example.GroupQuery");

            when(index.getKnownUsers(QUERY_SPEC)).thenReturn(List.of(simpleUser));
            when(index.getKnownUsers(BI_QUERY_SPEC)).thenReturn(List.of(joinUser));
            when(index.getKnownUsers(GROUP_QUERY_SPEC)).thenReturn(List.of(groupUser));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            Set<String> names = result.stream().map(c -> c.name().toString()).collect(Collectors.toSet());
            assertTrue(names.contains("com.example.SimpleQuery"));
            assertTrue(names.contains("com.example.JoinQuery"));
            assertTrue(names.contains("com.example.GroupQuery"));
        }

        @Test
        @DisplayName("deduplicates classes referenced via multiple strategies")
        void deduplicatesAcrossStrategies() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo resource = mockClassInfo("com.example.ProductResource");
            ClassInfo entity = mockClassInfo("com.example.Product");

            // resource found via both QuerySpec users AND Product entity users
            when(index.getKnownUsers(QUERY_SPEC)).thenReturn(List.of(resource));
            when(index.getAllKnownSubclasses(QUBIT_ENTITY)).thenReturn(List.of(entity));
            when(index.getKnownUsers(entity.name())).thenReturn(List.of(resource));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            long resourceCount = result.stream()
                    .filter(c -> c.name().toString().equals("com.example.ProductResource"))
                    .count();
            assertEquals(1, resourceCount, "Should deduplicate across strategies");
        }

        @Test
        @DisplayName("returns empty when no classes reference any Qubit type")
        void returnsEmptyWhenNoUsers() throws Exception {
            IndexView index = mockIndexWithDefaults();

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("finds users of QubitEntity subclasses")
        void findsUsersOfEntitySubclasses() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo personEntity = mockClassInfo("com.example.Person");
            ClassInfo personResource = mockClassInfo("com.example.PersonResource");

            when(index.getAllKnownSubclasses(QUBIT_ENTITY)).thenReturn(List.of(personEntity));
            when(index.getKnownUsers(personEntity.name())).thenReturn(List.of(personResource));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            assertTrue(result.stream().anyMatch(c -> c.name().toString().equals("com.example.PersonResource")));
        }

        @Test
        @DisplayName("finds users of QubitRepository implementations")
        void findsUsersOfRepositoryImplementations() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo personRepo = mockClassInfo("com.example.PersonRepository");
            ClassInfo personService = mockClassInfo("com.example.PersonService");

            when(index.getAllKnownImplementations(QUBIT_REPOSITORY)).thenReturn(List.of(personRepo));
            when(index.getKnownUsers(personRepo.name())).thenReturn(List.of(personService));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            assertTrue(result.stream().anyMatch(c -> c.name().toString().equals("com.example.PersonService")));
        }

        @Test
        @DisplayName("includes classes from .it. packages (no test-class heuristic)")
        void includesItPackages() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo itResource = mockClassInfo("com.example.it.PersonResource");
            when(index.getKnownUsers(QUERY_SPEC)).thenReturn(List.of(itResource));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            assertEquals(1, result.size());
            assertEquals("com.example.it.PersonResource", result.iterator().next().name().toString());
        }

        @Test
        @DisplayName("includes Italian domain packages (.it.)")
        void includesItalianDomainPackages() throws Exception {
            IndexView index = mockIndexWithDefaults();
            ClassInfo italianClass = mockClassInfo("com.azienda.it.servizio.QueryService");
            when(index.getKnownUsers(QUERY_SPEC)).thenReturn(List.of(italianClass));

            Collection<ClassInfo> result = invokeDiscoverQubitUserClasses(index);

            assertEquals(1, result.size(),
                    "Italian domain package should NOT be filtered out by test-class heuristic");
        }
    }

    @Nested
    @DisplayName("Simplified isNotExcludedClass (post-migration)")
    class SimplifiedFilterTests {

        @Test
        @DisplayName("exclude packages still filter out java.* and jakarta.*")
        void excludePackagesStillWork() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertFalse(isNotExcludedClass("java.lang.String", config));
            assertFalse(isNotExcludedClass("jakarta.persistence.Entity", config));
        }

        @Test
        @DisplayName("application classes pass through")
        void applicationClassesPass() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertTrue(isNotExcludedClass("com.example.MyService", config));
        }

        @Test
        @DisplayName("qubit extension classes always included")
        void qubitExtensionClassesIncluded() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertTrue(isNotExcludedClass("io.quarkiverse.qubit.QubitEntity", config));
        }

        @Test
        @DisplayName("io.quarkus.* classes are excluded by excludePackages only, no special case")
        void ioQuarkusClassesExcludedByPackageOnly() throws Exception {
            QubitBuildTimeConfig.ScanningConfig configExcluded = createScanningConfig(
                    List.of("java.", "jakarta.", "io.quarkus."),
                    Optional.empty());
            assertFalse(isNotExcludedClass("io.quarkus.arc.runtime.BeanContainer", configExcluded));

            QubitBuildTimeConfig.ScanningConfig configIncluded = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());
            assertTrue(isNotExcludedClass("io.quarkus.arc.runtime.BeanContainer", configIncluded));
        }

        @Test
        @DisplayName(".it. and .test. packages are NOT filtered (no isTestClass heuristic)")
        void noTestClassHeuristic() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.empty());

            assertTrue(isNotExcludedClass("com.example.it.MyIntegrationTest", config));
            assertTrue(isNotExcludedClass("com.example.test.MyTest", config));
            assertTrue(isNotExcludedClass("com.azienda.it.servizio.QueryService", config));
        }

        @Test
        @DisplayName("include packages still work as whitelist")
        void includePackagesWhitelist() throws Exception {
            QubitBuildTimeConfig.ScanningConfig config = createScanningConfig(
                    List.of("java.", "jakarta."),
                    Optional.of(List.of("com.example.")));

            assertTrue(isNotExcludedClass("com.example.MyService", config));
            assertFalse(isNotExcludedClass("org.other.SomeClass", config));
        }
    }

    @Nested
    @DisplayName("ScanningConfig no longer has scanTestClasses")
    class ConfigTests {

        @Test
        @DisplayName("ScanningConfig interface has no scanTestClasses method")
        void noScanTestClassesMethod() {
            assertThrows(NoSuchMethodException.class,
                    () -> QubitBuildTimeConfig.ScanningConfig.class.getDeclaredMethod("scanTestClasses"));
        }
    }

    // --- Helper methods ---

    private ClassInfo mockClassInfo(String className) {
        ClassInfo classInfo = mock(ClassInfo.class);
        when(classInfo.name()).thenReturn(DotName.createSimple(className));
        return classInfo;
    }

    @SuppressWarnings("unchecked")
    private Collection<ClassInfo> invokeDiscoverQubitUserClasses(IndexView index) throws Exception {
        Method method = QubitProcessor.class.getDeclaredMethod("discoverQubitUserClasses", IndexView.class);
        method.setAccessible(true);
        return (Collection<ClassInfo>) method.invoke(processor, index);
    }

    private boolean isNotExcludedClass(String className, QubitBuildTimeConfig.ScanningConfig config) throws Exception {
        ClassInfo classInfo = mockClassInfo(className);
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
