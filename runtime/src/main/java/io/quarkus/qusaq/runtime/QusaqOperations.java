package io.quarkus.qusaq.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

import java.util.List;

import static io.quarkus.qusaq.runtime.QusaqConstants.QUERY_METHOD_NAMES;

/**
 * Internal operations implementation for Qusaq query methods.
 */
public final class QusaqOperations {

    private QusaqOperations() {
    }

    /**
     * Executes findWhere query for entity class.
     */
    public static <T extends PanacheEntity> List<T> findWhere(Class<T> entityClass, QuerySpec<T, Boolean> spec) {
        String callSiteId = getCallSiteId();
        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);
        Object[] capturedValues = CapturedVariableExtractor.extract(spec, capturedCount);
        return registry.executeListQuery(callSiteId, entityClass, capturedValues);
    }

    /**
     * Executes countWhere query for entity class.
     */
    public static <T extends PanacheEntity> long countWhere(Class<T> entityClass, QuerySpec<T, Boolean> spec) {
        String callSiteId = getCallSiteId();
        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);
        Object[] capturedValues = CapturedVariableExtractor.extract(spec, capturedCount);
        return registry.executeCountQuery(callSiteId, entityClass, capturedValues);
    }

    /**
     * Executes exists query for entity class.
     */
    public static <T extends PanacheEntity> boolean exists(Class<T> entityClass, QuerySpec<T, Boolean> spec) {
        String callSiteId = getCallSiteId();
        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);
        Object[] capturedValues = CapturedVariableExtractor.extract(spec, capturedCount);
        return registry.executeExistsQuery(callSiteId, entityClass, capturedValues);
    }

    /**
     * Skips runtime package frames and query method frames to find user code call site.
     */
    private static String getCallSiteId() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> frames
                .skip(1)
                .filter(frame -> !frame.getClassName().startsWith("io.quarkus.qusaq.runtime."))
                .filter(frame -> !isQueryMethod(frame.getMethodName()))
                .findFirst()
                .map(frame -> frame.getClassName() + ":" +
                             frame.getMethodName() + ":" +
                             frame.getLineNumber())
                .orElseThrow(() -> new IllegalStateException("Could not determine call site")));
    }

    private static boolean isQueryMethod(String methodName) {
        return QUERY_METHOD_NAMES.contains(methodName);
    }

}
