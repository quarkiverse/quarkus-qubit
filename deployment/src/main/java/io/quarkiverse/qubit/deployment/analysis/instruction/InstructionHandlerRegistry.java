package io.quarkiverse.qubit.deployment.analysis.instruction;

import java.util.List;
import java.util.Objects;

/**
 * Registry holding all instruction handler instances for dependency injection.
 *
 * <p>This registry enables testability by allowing tests to inject mock or custom
 * handler implementations. In production, use {@link #createDefault()} to obtain
 * a registry with standard handler instances in the correct processing order.
 *
 * <p><b>Design Rationale (ARCH-005):</b>
 * <ul>
 *   <li>All handlers are stateless, making them safe to share</li>
 *   <li>Record provides immutability and automatic equals/hashCode</li>
 *   <li>Static factory method encapsulates default configuration and ordering</li>
 *   <li>Constructor injection enables testing with mock handlers</li>
 *   <li>Handler order matters: first matching handler wins (chain of responsibility)</li>
 * </ul>
 *
 * <p><b>Handler Processing Order:</b>
 * <ol>
 *   <li>{@link LoadInstructionHandler} - ALOAD, ILOAD, GETFIELD, etc.</li>
 *   <li>{@link ConstantInstructionHandler} - LDC, ICONST, BIPUSH, etc.</li>
 *   <li>{@link ArithmeticInstructionHandler} - IADD, ISUB, DCMPL, etc.</li>
 *   <li>{@link TypeConversionHandler} - I2L, L2F, D2I, etc.</li>
 *   <li>{@link InvokeDynamicHandler} - INVOKEDYNAMIC (Java 9+ string concatenation)</li>
 *   <li>{@link MethodInvocationHandler} - INVOKEVIRTUAL, INVOKESTATIC, INVOKESPECIAL</li>
 * </ol>
 *
 * <p><b>Usage in Production:</b>
 * <pre>
 * LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer();
 * // Uses default registry internally
 * </pre>
 *
 * <p><b>Usage in Tests:</b>
 * <pre>
 * InstructionHandlerRegistry customRegistry = new InstructionHandlerRegistry(
 *     List.of(mockHandler1, mockHandler2)
 * );
 * LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer(customRegistry);
 * </pre>
 *
 * @see InstructionHandler
 * @see io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer
 */
public record InstructionHandlerRegistry(List<InstructionHandler> handlers) {

    /**
     * Creates a registry with validation that handlers list is not null or empty.
     *
     * @throws NullPointerException if handlers is null
     * @throws IllegalArgumentException if handlers is empty
     */
    public InstructionHandlerRegistry {
        Objects.requireNonNull(handlers, "handlers cannot be null");
        if (handlers.isEmpty()) {
            throw new IllegalArgumentException("handlers cannot be empty");
        }
        // Create defensive immutable copy
        handlers = List.copyOf(handlers);
    }

    /**
     * Creates a default registry with all standard instruction handlers.
     *
     * <p>This is the recommended way to obtain a registry for production use.
     * All handlers are stateless and thread-safe. The order of handlers
     * determines processing priority (first matching handler wins).
     *
     * @return a new registry with default handler instances
     */
    public static InstructionHandlerRegistry createDefault() {
        return new InstructionHandlerRegistry(List.of(
                new LoadInstructionHandler(),
                new ConstantInstructionHandler(),
                new ArithmeticInstructionHandler(),
                new TypeConversionHandler(),
                new InvokeDynamicHandler(),      // Java 9+ string concatenation
                new MethodInvocationHandler()
        ));
    }
}
