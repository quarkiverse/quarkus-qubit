package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

import java.util.Collection;

/**
 * Polymorphic context interface for method call dispatching.
 * Enables handler reuse across single-entity ({@link MethodCallContext})
 * and bi-entity ({@link BiEntityMethodCallContext}) query types.
 *
 * @see MethodCallHandler
 */
public interface MethodCallDispatchContext {

    // ========== Core Handles ==========

    /** Gizmo method creator for bytecode generation. */
    MethodCreator method();

    /** Method call expression from the lambda AST. */
    LambdaExpression.MethodCall methodCall();

    /** CriteriaBuilder handle. */
    ResultHandle cb();

    // ========== Expression Generation (Context-Specific) ==========

    /** Generates JPA Expression from the method call's target. Context determines path resolution. */
    ResultHandle generateTargetAsJpaExpression();

    /** Generates target bytecode, returning raw values where appropriate (e.g., LIKE patterns). */
    ResultHandle generateTarget();

    /** Generates JPA Expression from a lambda expression argument. */
    ResultHandle generateArgumentAsJpaExpression(LambdaExpression expression);

    /** Generates argument bytecode, returning raw values where appropriate. */
    ResultHandle generateArgument(LambdaExpression expression);

    // ========== Field Access Generation ==========

    /** Generates JPA field access expression (path.get("fieldName")). */
    ResultHandle generateFieldAccess(LambdaExpression.FieldAccess fieldAccess, ResultHandle path);

    /** Returns the default root for fallback scenarios. */
    ResultHandle defaultRoot();

    // ========== Builder Registry Access ==========

    /** Expression builder registry for delegating to specialized builders. */
    ExpressionBuilderRegistry builderRegistry();

    // ========== Convenience Methods (Default Implementations) ==========

    /** Returns the method name from the method call. */
    default String methodName() {
        return methodCall().methodName();
    }

    /** Returns true if the method call has arguments. */
    default boolean hasArguments() {
        return !methodCall().arguments().isEmpty();
    }

    /** Returns the first argument expression, or null if no arguments. */
    default LambdaExpression firstArgument() {
        return hasArguments() ? methodCall().arguments().get(0) : null;
    }

    // ========== Method Name Validation Helpers ==========

    /** Returns true if the method name is in the collection. */
    default boolean isMethodIn(Collection<String> validMethodNames) {
        return validMethodNames.contains(methodName());
    }

    /** Returns true if the method name equals the expected name. */
    default boolean isMethod(String expectedMethodName) {
        return methodName().equals(expectedMethodName);
    }

    /** Returns true if method name is in the collection and has arguments. */
    default boolean isValidMethodWithArguments(Collection<String> validMethodNames) {
        return isMethodIn(validMethodNames) && hasArguments();
    }
}
