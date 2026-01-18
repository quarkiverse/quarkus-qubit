package io.quarkiverse.qubit.deployment.generation.methodcall;

/**
 * Priority levels for method call handlers in the Chain of Responsibility.
 * <p>
 * This enum encodes the explicit ordering constraints for handlers. Handlers
 * with lower ordinal values are processed first. The ordering is critical for
 * correctness because some handlers have overlapping method name patterns.
 *
 * <p><b>Ordering Constraints:</b>
 * <ul>
 *   <li>FAST_REJECT handlers use Set-based method name checks for immediate rejection</li>
 *   <li>DELEGATING handlers defer to expression builders that may return null</li>
 *   <li>FALLBACK handlers match broad patterns and must run last</li>
 * </ul>
 *
 * <p><b>Critical Dependencies:</b>
 * <ul>
 *   <li>TemporalAccessorHandler (getYear, getMonth) MUST precede GetterMethodHandler</li>
 *   <li>TemporalComparisonHandler (isBefore, isAfter) MUST precede GetterMethodHandler</li>
 *   <li>StringUtilityHandler (isEmpty, isBlank) MUST precede GetterMethodHandler</li>
 * </ul>
 *
 * @see MethodCallHandler#priority()
 * @see MethodCallHandlerChain
 */
public enum HandlerPriority {

    /** Fast rejection via Set-based method name checks. */
    FAST_REJECT,

    /** Delegates to expression builders that may return null. */
    DELEGATING,

    /** Fallback for broad patterns (e.g., getXxx/isXxx getters). Must run last. */
    FALLBACK
}
