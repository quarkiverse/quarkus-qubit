package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import io.quarkus.logging.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts JPA relationship annotations (@ManyToOne, @OneToOne, @OneToMany, @ManyToMany)
 * from entity classes at build time. Thread-safe with caching.
 */
public class RelationshipMetadataExtractor {

    // JPA relationship annotation names
    private static final DotName MANY_TO_ONE = DotName.createSimple("jakarta.persistence.ManyToOne");
    private static final DotName ONE_TO_ONE = DotName.createSimple("jakarta.persistence.OneToOne");
    private static final DotName ONE_TO_MANY = DotName.createSimple("jakarta.persistence.OneToMany");
    private static final DotName MANY_TO_MANY = DotName.createSimple("jakarta.persistence.ManyToMany");

    // Cache for entity relationship metadata
    private final Map<String, EntityRelationshipInfo> entityCache = new ConcurrentHashMap<>();

    // Jandex index for class introspection
    private final IndexView index;

    public RelationshipMetadataExtractor(IndexView index) {
        this.index = index;
    }

    /** Entity's relationship field metadata. */
    public record EntityRelationshipInfo(
            String entityClass,
            Map<String, FieldRelationship> relationships) {

        public boolean isRelationship(String fieldName) {
            return relationships.containsKey(fieldName);
        }

        public FieldRelationship getRelationship(String fieldName) {
            return relationships.get(fieldName);
        }
    }

    /** Single relationship field metadata. */
    public record FieldRelationship(
            String fieldName,
            String targetEntity,
            RelationType relationType,
            String mappedBy) {
    }

    /** Gets relationship metadata for an entity class (cached). */
    public EntityRelationshipInfo getEntityRelationships(String entityClassName) {
        return entityCache.computeIfAbsent(entityClassName, this::extractRelationships);
    }

    /** Returns RelationType for a field, or FIELD if not a relationship. */
    public RelationType getRelationType(String entityClassName, String fieldName) {
        EntityRelationshipInfo info = getEntityRelationships(entityClassName);
        FieldRelationship relationship = info.getRelationship(fieldName);
        return relationship != null ? relationship.relationType() : RelationType.FIELD;
    }

    /** Returns target entity class name, or null if not a relationship. */
    public String getTargetEntity(String entityClassName, String fieldName) {
        EntityRelationshipInfo info = getEntityRelationships(entityClassName);
        FieldRelationship relationship = info.getRelationship(fieldName);
        return relationship != null ? relationship.targetEntity() : null;
    }

    private EntityRelationshipInfo extractRelationships(String entityClassName) {
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(entityClassName));
        Map<String, FieldRelationship> relationships = new HashMap<>();

        if (classInfo == null) {
            Log.tracef("Entity class not found in Jandex index: %s", entityClassName);
            return new EntityRelationshipInfo(entityClassName, relationships);
        }

        // Check all fields for relationship annotations
        for (FieldInfo field : classInfo.fields()) {
            FieldRelationship relationship = extractFieldRelationship(field);
            if (relationship != null) {
                relationships.put(field.name(), relationship);
                Log.tracef("Found %s relationship: %s.%s -> %s",
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
                        Log.tracef("Found inherited %s relationship: %s.%s -> %s",
                                relationship.relationType(), entityClassName, field.name(), relationship.targetEntity());
                    }
                }
            }

            superName = superClass.superName();
        }

        Log.debugf("Extracted %d relationships from entity %s", relationships.size(), entityClassName);
        return new EntityRelationshipInfo(entityClassName, relationships);
    }

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

    public void clearCache() {
        entityCache.clear();
    }

    public int getCacheSize() {
        return entityCache.size();
    }
}
