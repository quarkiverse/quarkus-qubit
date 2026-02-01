package io.quarkiverse.qubit.deployment.generation.methodcall;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MethodCallHandlerChain} handler ordering validation.
 */
class MethodCallHandlerChainTest {

    @Test
    void defaultHandlers_areInPriorityOrder() {
        // This should not throw - default handlers are correctly ordered
        MethodCallHandlerChain chain = MethodCallHandlerChain.defaultInstance();

        List<MethodCallHandler> handlers = chain.handlers();
        assertFalse(handlers.isEmpty());
        assertEquals(8, handlers.size());
    }

    @Test
    void constructor_rejectsMisorderedHandlers() {
        // GetterMethodHandler (FALLBACK) placed before TemporalAccessorHandler (DELEGATING)
        List<MethodCallHandler> misordered = List.of(
                GetterMethodHandler.INSTANCE,        // FALLBACK - should be last
                TemporalAccessorHandler.INSTANCE     // DELEGATING - should come before FALLBACK
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MethodCallHandlerChain(misordered)
        );

        assertTrue(exception.getMessage().contains("Handler ordering violation"));
        assertTrue(exception.getMessage().contains("TemporalAccessorHandler"));
        assertTrue(exception.getMessage().contains("GetterMethodHandler"));
    }

    @Test
    void constructor_acceptsCorrectlyOrderedHandlers() {
        // Subset of handlers in correct priority order
        List<MethodCallHandler> correctOrder = List.of(
                TemporalComparisonHandler.INSTANCE,   // FAST_REJECT
                StringLikePatternHandler.INSTANCE,    // FAST_REJECT
                TemporalAccessorHandler.INSTANCE,     // DELEGATING
                GetterMethodHandler.INSTANCE          // FALLBACK
        );

        // Should not throw
        MethodCallHandlerChain chain = new MethodCallHandlerChain(correctOrder);
        assertEquals(4, chain.handlers().size());
    }

    @Test
    void constructor_acceptsEmptyHandlerList() {
        // Edge case: empty list should be valid
        MethodCallHandlerChain chain = new MethodCallHandlerChain(List.of());
        assertTrue(chain.handlers().isEmpty());
    }

    @Test
    void constructor_acceptsSingleHandler() {
        // Edge case: single handler should be valid
        MethodCallHandlerChain chain = new MethodCallHandlerChain(
                List.of(GetterMethodHandler.INSTANCE));
        assertEquals(1, chain.handlers().size());
    }

    @Test
    void allHandlers_havePriorityDefined() {
        for (MethodCallHandler handler : MethodCallHandlerChain.createDefaultHandlers()) {
            assertNotNull(handler.priority(),
                    "Handler " + handler.getClass().getSimpleName() + " must have priority defined");
        }
    }

    @Test
    void fastRejectHandlers_haveCorrectPriority() {
        assertEquals(HandlerPriority.FAST_REJECT, TemporalComparisonHandler.INSTANCE.priority());
        assertEquals(HandlerPriority.FAST_REJECT, StringLikePatternHandler.INSTANCE.priority());
        assertEquals(HandlerPriority.FAST_REJECT, BigDecimalArithmeticHandler.INSTANCE.priority());
        assertEquals(HandlerPriority.FAST_REJECT, StringSubstringHandler.INSTANCE.priority());
    }

    @Test
    void delegatingHandlers_haveCorrectPriority() {
        assertEquals(HandlerPriority.DELEGATING, TemporalAccessorHandler.INSTANCE.priority());
        assertEquals(HandlerPriority.DELEGATING, StringTransformationHandler.INSTANCE.priority());
        assertEquals(HandlerPriority.DELEGATING, StringUtilityHandler.INSTANCE.priority());
    }

    @Test
    void fallbackHandler_hasCorrectPriority() {
        assertEquals(HandlerPriority.FALLBACK, GetterMethodHandler.INSTANCE.priority());
    }

    @Test
    void getterMethodHandler_mustBeLast() {
        List<MethodCallHandler> handlers = MethodCallHandlerChain.createDefaultHandlers();
        MethodCallHandler lastHandler = handlers.getLast();

        assertSame(GetterMethodHandler.INSTANCE, lastHandler,
                "GetterMethodHandler must be the last handler to avoid intercepting " +
                "temporal accessor methods like getYear() and utility methods like isEmpty()");
    }
}
