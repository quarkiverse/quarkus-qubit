package io.quarkiverse.qubit.deployment.analysis;

import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkus.logging.Log;

/**
 * Cache for parsed ClassNode instances, avoiding repeated ASM parsing of the same class.
 * Thread-safe via ConcurrentHashMap.
 */
final class ClassNodeCache {

    private static final ConcurrentHashMap<String, ClassNode> CACHE = new ConcurrentHashMap<>();

    private ClassNodeCache() {
    }

    /** Clears the cache. Used for dev mode hot reload. */
    static void clear() {
        CACHE.clear();
        Log.debug("ClassNodeCache cleared");
    }

    /** Pre-loads a ClassNode into the cache during warm-up to eliminate contention. */
    static void preload(byte[] classBytes, BuildMetricsCollector metricsCollector) {
        getOrParse(classBytes, metricsCollector);
    }

    /** Gets or parses a ClassNode from bytecode, caching to avoid repeated parsing. */
    static ClassNode getOrParse(byte[] classBytes, BuildMetricsCollector metricsCollector) {
        ClassReader reader = new ClassReader(classBytes);
        String className = reader.getClassName();

        return CACHE.computeIfAbsent(className, key -> {
            long asmStartTime = System.nanoTime();
            try {
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return classNode;
            } finally {
                if (metricsCollector != null) {
                    metricsCollector.addAsmParsingTime(System.nanoTime() - asmStartTime);
                    metricsCollector.incrementUniqueClassesLoaded();
                }
            }
        });
    }
}
