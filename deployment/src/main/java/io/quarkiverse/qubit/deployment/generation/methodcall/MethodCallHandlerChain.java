package io.quarkiverse.qubit.deployment.generation.methodcall;

import java.util.List;
import java.util.Optional;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Chain of Responsibility coordinator for method call → JPA expression generation.
 * Iterates handlers in priority order (FAST_REJECT → DELEGATING → FALLBACK).
 * Validates ordering at construction to prevent silent handler interception bugs.
 */
@SuppressWarnings("java:S6206") // Behavioral pattern class, not a data carrier - record semantics inappropriate
public final class MethodCallHandlerChain {

    private static final MethodCallHandlerChain DEFAULT_INSTANCE = new MethodCallHandlerChain(createDefaultHandlers());

    private final List<MethodCallHandler> handlers;

    /** Creates chain with ordering validation (lower priority ordinals must come first). */
    public MethodCallHandlerChain(List<MethodCallHandler> handlers) {
        validateHandlerOrdering(handlers);
        this.handlers = List.copyOf(handlers);
    }

    /** Validates handlers are ordered by priority (FAST_REJECT < DELEGATING < FALLBACK). */
    private static void validateHandlerOrdering(List<MethodCallHandler> handlers) {
        if (handlers.isEmpty()) {
            return;
        }

        HandlerPriority previousPriority = null;
        MethodCallHandler previousHandler = null;

        for (MethodCallHandler handler : handlers) {
            HandlerPriority currentPriority = handler.priority();

            if (previousPriority != null && currentPriority.ordinal() < previousPriority.ordinal()) {
                throw new IllegalArgumentException(String.format(
                        "Handler ordering violation: %s (priority=%s) must not follow %s (priority=%s). " +
                                "Handlers must be ordered by priority: FAST_REJECT < DELEGATING < FALLBACK.",
                        handler.getClass().getSimpleName(),
                        currentPriority,
                        previousHandler.getClass().getSimpleName(),
                        previousPriority));
            }

            previousPriority = currentPriority;
            previousHandler = handler;
        }
    }

    /** Returns the default singleton instance. */
    public static MethodCallHandlerChain defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /** Creates default handlers ordered by HandlerPriority. */
    public static List<MethodCallHandler> createDefaultHandlers() {
        return List.of(
                // FAST_REJECT priority - Set-based method name rejection
                TemporalComparisonHandler.INSTANCE, // isBefore, isAfter, isEqual
                StringLikePatternHandler.INSTANCE, // startsWith, endsWith, contains
                BigDecimalArithmeticHandler.INSTANCE, // add, subtract, multiply, divide
                StringSubstringHandler.INSTANCE, // substring
                StringIndexOfHandler.INSTANCE, // indexOf -> LOCATE with 0-based conversion
                QubitLikeHandler.INSTANCE, // Qubit.like(), Qubit.notLike()

                // DELEGATING priority - delegate to expression builders
                TemporalAccessorHandler.INSTANCE, // getYear, getMonth, getDayOfMonth
                StringTransformationHandler.INSTANCE, // toUpperCase, toLowerCase, trim
                StringUtilityHandler.INSTANCE, // equals, isEmpty, isBlank, length

                // FALLBACK priority - broad getter pattern matching (MUST be last)
                GetterMethodHandler.INSTANCE // getXxx, isXxx → field access
        );
    }

    /** Iterates handlers, returns Success or Unsupported (never null). */
    public GenerationResult handleMethodCall(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr root,
            Expr capturedValues,
            ExpressionBuilderRegistry builderRegistry,
            ExpressionGeneratorHelper helper) {

        MethodCallContext context = new MethodCallContext(
                bc, methodCall, cb, root, capturedValues, builderRegistry, helper);

        for (MethodCallHandler handler : handlers) {
            Optional<Expr> result = handler.handle(context);
            if (result.isPresent()) {
                return GenerationResult.success(result.get());
            }
        }

        // No handler could process this method call - return explicit Unsupported
        return GenerationResult.Unsupported.noHandlerFound(methodCall.methodName());
    }

    /** Returns unmodifiable handler list. */
    public List<MethodCallHandler> handlers() {
        return handlers;
    }

    /** Polymorphic dispatch: accepts MethodCallContext or BiEntityMethodCallContext. */
    public GenerationResult handleMethodCall(MethodCallDispatchContext context) {
        for (MethodCallHandler handler : handlers) {
            Optional<Expr> result = handler.handle(context);
            if (result.isPresent()) {
                return GenerationResult.success(result.get());
            }
        }

        // No handler could process this method call - return explicit Unsupported
        return GenerationResult.Unsupported.noHandlerFound(context.methodName());
    }
}
