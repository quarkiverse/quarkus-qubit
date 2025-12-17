package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType;
import io.quarkiverse.qubit.deployment.analysis.RelationshipMetadataExtractor.EntityRelationshipInfo;
import io.quarkiverse.qubit.deployment.analysis.RelationshipMetadataExtractor.FieldRelationship;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RelationshipMetadataExtractor.
 *
 * <p>These tests verify that JPA relationship annotations are correctly
 * extracted from entity classes using Jandex.
 */
class RelationshipMetadataExtractorTest {

    private static Index testIndex;
    private static RelationshipMetadataExtractor extractor;

    // Test entity class names from FluentApiTestSources
    private static final String TEST_ORDER_CLASS =
            "io.quarkiverse.qubit.deployment.testutil.FluentApiTestSources$TestOrder";
    private static final String TEST_CUSTOMER_CLASS =
            "io.quarkiverse.qubit.deployment.testutil.FluentApiTestSources$TestCustomer";
    private static final String TEST_ORDER_ITEM_CLASS =
            "io.quarkiverse.qubit.deployment.testutil.FluentApiTestSources$TestOrderItem";
    private static final String TEST_EMPLOYEE_CLASS =
            "io.quarkiverse.qubit.deployment.testutil.FluentApiTestSources$TestEmployee";

    @BeforeAll
    static void buildIndex() throws Exception {
        Indexer indexer = new Indexer();

        // Index the FluentApiTestSources class which contains our test entities
        String classFile = "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources.class";
        try (InputStream is = RelationshipMetadataExtractorTest.class.getClassLoader()
                .getResourceAsStream(classFile)) {
            if (is != null) {
                indexer.index(is);
            }
        }

        // Index the inner classes
        String[] innerClasses = {
                "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources$TestEmployee.class",
                "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources$TestOrder.class",
                "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources$TestCustomer.class",
                "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources$TestOrderItem.class"
        };

        for (String innerClass : innerClasses) {
            try (InputStream is = RelationshipMetadataExtractorTest.class.getClassLoader()
                    .getResourceAsStream(innerClass)) {
                if (is != null) {
                    indexer.index(is);
                }
            }
        }

        testIndex = indexer.complete();
        extractor = new RelationshipMetadataExtractor(testIndex);
    }

    @Nested
    class GetEntityRelationships {

        @Test
        void returnsEntityRelationshipInfoForKnownEntity() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_ORDER_CLASS);

            assertThat(info)
                    .isNotNull()
                    .satisfies(i -> assertThat(i.entityClass()).isEqualTo(TEST_ORDER_CLASS));
        }

        @Test
        void returnsEmptyRelationshipsForUnknownEntity() {
            EntityRelationshipInfo info = extractor.getEntityRelationships("com.nonexistent.Entity");

            assertThat(info)
                    .isNotNull()
                    .satisfies(i -> assertThat(i.relationships()).isEmpty());
        }

        @Test
        void findsRelationshipsInTestOrderEntity() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_ORDER_CLASS);

            // TestOrder has @ManyToOne customer and @OneToMany items
            assertThat(info.relationships())
                    .as("TestOrder should have relationships")
                    .isNotEmpty();
        }

        @Test
        void cachesEntityRelationshipInfo() {
            // First call
            extractor.getEntityRelationships(TEST_ORDER_CLASS);

            // Cache should have entry
            assertThat(extractor.getCacheSize())
                    .as("Cache should contain at least one entry")
                    .isGreaterThanOrEqualTo(1);

            // Second call should use cache
            EntityRelationshipInfo info2 = extractor.getEntityRelationships(TEST_ORDER_CLASS);
            assertThat(info2).isNotNull();
        }
    }

    @Nested
    class GetRelationType {

        @Test
        void returnsManyToOneForManyToOneRelationship() {
            RelationType type = extractor.getRelationType(TEST_ORDER_CLASS, "customer");

            assertThat(type)
                    .as("customer field should be MANY_TO_ONE")
                    .isEqualTo(RelationType.MANY_TO_ONE);
        }

        @Test
        void returnsOneToManyForOneToManyRelationship() {
            RelationType type = extractor.getRelationType(TEST_ORDER_CLASS, "items");

            assertThat(type)
                    .as("items field should be ONE_TO_MANY")
                    .isEqualTo(RelationType.ONE_TO_MANY);
        }

        @Test
        void returnsFieldForNonRelationshipField() {
            RelationType type = extractor.getRelationType(TEST_ORDER_CLASS, "total");

            assertThat(type)
                    .as("total field should be FIELD (not a relationship)")
                    .isEqualTo(RelationType.FIELD);
        }

        @Test
        void returnsFieldForUnknownField() {
            RelationType type = extractor.getRelationType(TEST_ORDER_CLASS, "nonExistentField");

            assertThat(type)
                    .as("Unknown field should return FIELD")
                    .isEqualTo(RelationType.FIELD);
        }

        @Test
        void returnsFieldForUnknownEntity() {
            RelationType type = extractor.getRelationType("com.unknown.Entity", "someField");

            assertThat(type)
                    .as("Unknown entity should return FIELD")
                    .isEqualTo(RelationType.FIELD);
        }
    }

    @Nested
    class GetTargetEntity {

        @Test
        void returnsTargetEntityForManyToOneRelationship() {
            String target = extractor.getTargetEntity(TEST_ORDER_CLASS, "customer");

            assertThat(target)
                    .as("customer field should target TestCustomer")
                    .isEqualTo(TEST_CUSTOMER_CLASS);
        }

        @Test
        void returnsTargetEntityForOneToManyRelationship() {
            String target = extractor.getTargetEntity(TEST_ORDER_CLASS, "items");

            // The target should be the element type of the collection
            assertThat(target)
                    .as("items field should target TestOrderItem")
                    .isEqualTo(TEST_ORDER_ITEM_CLASS);
        }

        @Test
        void returnsNullForNonRelationshipField() {
            String target = extractor.getTargetEntity(TEST_ORDER_CLASS, "total");

            assertThat(target)
                    .as("total field is not a relationship")
                    .isNull();
        }

        @Test
        void returnsNullForUnknownField() {
            String target = extractor.getTargetEntity(TEST_ORDER_CLASS, "nonExistentField");

            assertThat(target)
                    .as("Unknown field should return null")
                    .isNull();
        }
    }

    @Nested
    class EntityRelationshipInfoRecord {

        @Test
        void isRelationshipReturnsTrueForRelationshipField() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_ORDER_CLASS);

            assertThat(info.isRelationship("customer"))
                    .as("customer should be a relationship")
                    .isTrue();
        }

        @Test
        void isRelationshipReturnsFalseForNonRelationshipField() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_ORDER_CLASS);

            assertThat(info.isRelationship("total"))
                    .as("total should not be a relationship")
                    .isFalse();
        }

        @Test
        void getRelationshipReturnsFieldRelationship() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_ORDER_CLASS);

            FieldRelationship relationship = info.getRelationship("customer");

            assertThat(relationship)
                    .isNotNull()
                    .satisfies(r -> {
                        assertThat(r.fieldName()).isEqualTo("customer");
                        assertThat(r.relationType()).isEqualTo(RelationType.MANY_TO_ONE);
                    });
        }

        @Test
        void getRelationshipReturnsNullForNonRelationship() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_ORDER_CLASS);

            FieldRelationship relationship = info.getRelationship("total");

            assertThat(relationship).isNull();
        }
    }

    @Nested
    class FieldRelationshipRecord {

        @Test
        void fieldRelationshipStoresAllProperties() {
            FieldRelationship relationship = new FieldRelationship(
                    "orders",
                    "com.example.Order",
                    RelationType.ONE_TO_MANY,
                    "customer");

            assertThat(relationship.fieldName()).isEqualTo("orders");
            assertThat(relationship.targetEntity()).isEqualTo("com.example.Order");
            assertThat(relationship.relationType()).isEqualTo(RelationType.ONE_TO_MANY);
            assertThat(relationship.mappedBy()).isEqualTo("customer");
        }

        @Test
        void fieldRelationshipAllowsNullMappedBy() {
            FieldRelationship relationship = new FieldRelationship(
                    "customer",
                    "com.example.Customer",
                    RelationType.MANY_TO_ONE,
                    null);

            assertThat(relationship.mappedBy()).isNull();
        }
    }

    @Nested
    class CacheOperations {

        @Test
        void clearCacheRemovesAllEntries() {
            // Populate cache
            extractor.getEntityRelationships(TEST_ORDER_CLASS);
            assertThat(extractor.getCacheSize()).isGreaterThan(0);

            // Clear cache
            extractor.clearCache();

            assertThat(extractor.getCacheSize())
                    .as("Cache should be empty after clear")
                    .isEqualTo(0);
        }

        @Test
        void getCacheSizeReturnsCorrectCount() {
            extractor.clearCache();
            assertThat(extractor.getCacheSize()).isEqualTo(0);

            extractor.getEntityRelationships(TEST_ORDER_CLASS);
            assertThat(extractor.getCacheSize()).isEqualTo(1);

            extractor.getEntityRelationships(TEST_CUSTOMER_CLASS);
            assertThat(extractor.getCacheSize()).isEqualTo(2);
        }
    }

    @Nested
    class EntityWithNoRelationships {

        @Test
        void entityWithoutRelationshipsHasEmptyRelationshipsMap() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_EMPLOYEE_CLASS);

            // TestEmployee has no relationship annotations
            assertThat(info.relationships())
                    .as("TestEmployee should have no relationships")
                    .isEmpty();
        }
    }

    @Nested
    class OneToManyWithMappedBy {

        @Test
        void extractsMappedByAttribute() {
            EntityRelationshipInfo info = extractor.getEntityRelationships(TEST_CUSTOMER_CLASS);

            // TestCustomer has @OneToMany(mappedBy = "customer") List<TestOrder> orders
            FieldRelationship orders = info.getRelationship("orders");

            assertThat(orders)
                    .isNotNull()
                    .satisfies(r -> {
                        assertThat(r.relationType()).isEqualTo(RelationType.ONE_TO_MANY);
                        assertThat(r.mappedBy()).isEqualTo("customer");
                    });
        }
    }
}
