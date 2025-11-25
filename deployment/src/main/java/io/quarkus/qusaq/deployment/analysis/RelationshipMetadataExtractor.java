package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.qusaq.deployment.LambdaExpression.RelationType;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts JPA relationship annotations from entity classes at build time.
 * <p>
 * Scans entity bytecode for @ManyToOne, @OneToOne, @OneToMany, and @ManyToMany
 * annotations to build a relationship metadata cache used during lambda bytecode
 * analysis for relationship navigation.
 * <p>
 * Thread-safe and supports caching to avoid repeated Jandex lookups.
 */
public class RelationshipMetadataExtractor {

    private static final Logger log = Logger.getLogger(RelationshipMetadataExtractor.class);

    // JPA relationship annotation names
    private static final DotName MANY_TO_ONE = DotName.createSimple("jakarta.persistence.ManyToOne");
    private static final DotName ONE_TO_ONE = DotName.createSimple("jakarta.persistence.OneToOne");
    private static final DotName ONE_TO_MANY = DotName.createSimple("jakarta.persistence.OneToMany");
    private static final DotName MANY_TO_MANY = DotName.createSimple("jakarta.persistence.ManyToMany");

    // Cache for entity relationship metadata
    private final Map<String, EntityRelationshipInfo> entityCache = new ConcurrentHashMap<>();

    // Jandex index for class introspection
    private final IndexView index;

    /**
     * Creates a new extractor with the given Jandex index.
     *
     * @param index Jandex index containing entity class metadata
     */
    public RelationshipMetadataExtractor(IndexView index) {
        this.index = index;
    }

    /**
     * Information about an entity's relationship fields.
     *
     * @param entityClass The fully qualified entity class name
     * @param relationships Map of field name to relationship info
     */
    public record EntityRelationshipInfo(
            String entityClass,
            Map<String, FieldRelationship> relationships) {

        /**
         * Checks if a field is a relationship field.
         *
         * @param fieldName The field name to check
         * @return true if the field is a relationship
         */
        public boolean isRelationship(String fieldName) {
            return relationships.containsKey(fieldName);
        }

        /**
         * Gets relationship info for a field.
         *
         * @param fieldName The field name
         * @return Relationship info or null if not a relationship
         */
        public FieldRelationship getRelationship(String fieldName) {
            return relationships.get(fieldName);
        }
    }

    /**
     * Information about a single relationship field.
     *
     * @param fieldName The field name
     * @param targetEntity The target entity class (fully qualified name)
     * @param relationType The type of relationship
     * @param mappedBy The mappedBy attribute value (for bidirectional relationships)
     */
    public record FieldRelationship(
            String fieldName,
            String targetEntity,
            RelationType relationType,
            String mappedBy) {
    }

    /**
     * Gets relationship metadata for an entity class.
     * <p>
     * Uses caching to avoid repeated Jandex lookups.
     *
     * @param entityClassName The fully qualified entity class name
     * @return Entity relationship info, or empty info if entity not found
     */
    public EntityRelationshipInfo getEntityRelationships(String entityClassName) {
        return entityCache.computeIfAbsent(entityClassName, this::extractRelationships);
    }

    /**
     * Checks if a field on an entity is a relationship field.
     *
     * @param entityClassName The fully qualified entity class name
     * @param fieldName The field name to check
     * @return The relationship type, or FIELD if not a relationship
     */
    public RelationType getRelationType(String entityClassName, String fieldName) {
        EntityRelationshipInfo info = getEntityRelationships(entityClassName);
        FieldRelationship relationship = info.getRelationship(fieldName);
        return relationship != null ? relationship.relationType() : RelationType.FIELD;
    }

    /**
     * Gets the target entity class for a relationship field.
     *
     * @param entityClassName The owner entity class name
     * @param fieldName The relationship field name
     * @return The target entity class name, or null if not a relationship
     */
    public String getTargetEntity(String entityClassName, String fieldName) {
        EntityRelationshipInfo info = getEntityRelationships(entityClassName);
        FieldRelationship relationship = info.getRelationship(fieldName);
        return relationship != null ? relationship.targetEntity() : null;
    }

    /**
     * Extracts relationship info from an entity class using Jandex.
     */
    private EntityRelationshipInfo extractRelationships(String entityClassName) {
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(entityClassName));
        Map<String, FieldRelationship> relationships = new HashMap<>();

        if (classInfo == null) {
            log.tracef("Entity class not found in Jandex index: %s", entityClassName);
            return new EntityRelationshipInfo(entityClassName, relationships);
        }

        // Check all fields for relationship annotations
        for (FieldInfo field : classInfo.fields()) {
            FieldRelationship relationship = extractFieldRelationship(field);
            if (relationship != null) {
                relationships.put(field.name(), relationship);
                log.tracef("Found %s relationship: %s.%s -> %s",
                        relationship.relationType(), entityClassName, field.name(), relationship.targetEntity());
            }
        }

        // Also check superclasses for inherited relationships
        DotName superName = classInfo.superName();
        while (superName != null && !superName.toString().equals("java.lang.Object")) {
            ClassInfo superClass = index.getClassByName(superName);
            if (superClass == null) {
                break;
            }

            for (FieldInfo field : superClass.fields()) {
                if (!relationships.containsKey(field.name())) {
                    FieldRelationship relationship = extractFieldRelationship(field);
                    if (relationship != null) {
                        relationships.put(field.name(), relationship);
                        log.tracef("Found inherited %s relationship: %s.%s -> %s",
                                relationship.relationType(), entityClassName, field.name(), relationship.targetEntity());
                    }
                }
            }

            superName = superClass.superName();
        }

        log.debugf("Extracted %d relationships from entity %s", relationships.size(), entityClassName);
        return new EntityRelationshipInfo(entityClassName, relationships);
    }

    /**
     * Extracts relationship info from a single field.
     */
    private FieldRelationship extractFieldRelationship(FieldInfo field) {
        // Check for @ManyToOne
        AnnotationInstance manyToOne = field.annotation(MANY_TO_ONE);
        if (manyToOne != null) {
            return createFieldRelationship(field, manyToOne, RelationType.MANY_TO_ONE);
        }

        // Check for @OneToOne
        AnnotationInstance oneToOne = field.annotation(ONE_TO_ONE);
        if (oneToOne != null) {
            return createFieldRelationship(field, oneToOne, RelationType.ONE_TO_ONE);
        }

        // Check for @OneToMany
        AnnotationInstance oneToMany = field.annotation(ONE_TO_MANY);
        if (oneToMany != null) {
            return createFieldRelationship(field, oneToMany, RelationType.ONE_TO_MANY);
        }

        // Check for @ManyToMany
        AnnotationInstance manyToMany = field.annotation(MANY_TO_MANY);
        if (manyToMany != null) {
            return createFieldRelationship(field, manyToMany, RelationType.MANY_TO_MANY);
        }

        return null;
    }

    /**
     * Creates a FieldRelationship from annotation info.
     */
    private FieldRelationship createFieldRelationship(
            FieldInfo field,
            AnnotationInstance annotation,
            RelationType relationType) {

        String targetEntity = extractTargetEntity(field, annotation, relationType);
        String mappedBy = extractMappedBy(annotation);

        return new FieldRelationship(
                field.name(),
                targetEntity,
                relationType,
                mappedBy);
    }

    /**
     * Extracts the target entity class from the field type or annotation.
     */
    private String extractTargetEntity(FieldInfo field, AnnotationInstance annotation, RelationType relationType) {
        // First, try to get targetEntity from annotation
        var targetEntityValue = annotation.value("targetEntity");
        if (targetEntityValue != null) {
            Type targetType = targetEntityValue.asClass();
            if (targetType != null && !targetType.name().toString().equals("void")) {
                return targetType.name().toString();
            }
        }

        // For @ManyToOne and @OneToOne, the field type IS the target entity
        if (relationType == RelationType.MANY_TO_ONE || relationType == RelationType.ONE_TO_ONE) {
            return field.type().name().toString();
        }

        // For @OneToMany and @ManyToMany, we need to extract from the collection's type parameter
        if (field.type().kind() == Type.Kind.PARAMETERIZED_TYPE) {
            var parameterizedType = field.type().asParameterizedType();
            var typeArguments = parameterizedType.arguments();
            if (!typeArguments.isEmpty()) {
                return typeArguments.get(0).name().toString();
            }
        }

        // Fallback to the raw type
        return field.type().name().toString();
    }

    /**
     * Extracts the mappedBy attribute from a relationship annotation.
     */
    private String extractMappedBy(AnnotationInstance annotation) {
        var mappedByValue = annotation.value("mappedBy");
        if (mappedByValue != null) {
            String mappedBy = mappedByValue.asString();
            if (mappedBy != null && !mappedBy.isEmpty()) {
                return mappedBy;
            }
        }
        return null;
    }

    /**
     * Clears the entity relationship cache.
     * Useful for testing or when index changes.
     */
    public void clearCache() {
        entityCache.clear();
    }

    /**
     * Returns the current cache size.
     * Useful for debugging and monitoring.
     */
    public int getCacheSize() {
        return entityCache.size();
    }
}
