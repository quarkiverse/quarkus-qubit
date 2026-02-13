package io.quarkiverse.qubit.deployment.generation.methodcall;

import java.util.Collection;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Polymorphic context interface for method call dispatching.
 * Enables handler reuse across single-entity ({@link MethodCallContext})
 * and bi-entity ({@link BiEntityMethodCallContext}) query types.
 *
 * @see MethodCallHandler
 */
public interface MethodCallDispatchContext {

    /** Gizmo 2 block creator for bytecode generation. */
    BlockCreator bc();

    /** Method call expression from the lambda AST. */
    LambdaExpression.MethodCall methodCall();

    /** CriteriaBuilder handle. */
    Expr cb();

    /** Generates JPA Expression from the method call's target. Context determines path resolution. */
    Expr generateTargetAsJpaExpression();

    /** Generates target bytecode, returning raw values where appropriate (e.g., LIKE patterns). */
    Expr generateTarget();

    /** Generates JPA Expression from a lambda expression argument. */
    Expr generateArgumentAsJpaExpression(LambdaExpression expression);

    /** Generates argument bytecode, returning raw values where appropriate. */
    Expr generateArgument(LambdaExpression expression);

    /** Generates JPA field access expression (path.get("fieldName")). */
    Expr generateFieldAccess(LambdaExpression.FieldAccess fieldAccess, Expr path);

    /** Returns the default root for fallback scenarios. */
    Expr defaultRoot();

    /** Expression builder registry for delegating to specialized builders. */
    ExpressionBuilderRegistry builderRegistry();

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
        return hasArguments() ? methodCall().arguments().getFirst() : null;
    }

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
