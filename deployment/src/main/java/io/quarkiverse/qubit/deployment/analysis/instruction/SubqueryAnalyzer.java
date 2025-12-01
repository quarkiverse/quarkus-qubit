package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryBuilderReference;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import org.jboss.logging.Logger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.List;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.and;
import static io.quarkiverse.qubit.runtime.QubitConstants.*;

/**
 * Analyzes subquery-related bytecode instructions.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Subqueries.subquery(Class) factory method → SubqueryBuilderReference</li>
 *   <li>SubqueryBuilder.* method calls for scalar, exists, and IN subqueries</li>
 * </ul>
 *
 * <p>Iteration 8: Extracted from MethodInvocationHandler to reduce class size
 * and improve maintainability (addresses ARCH-001, MAINT-001).
 *
 * @see MethodInvocationHandler
 * @see SubqueryBuilderReference
 */
public class SubqueryAnalyzer {

    private static final Logger log = Logger.getLogger(SubqueryAnalyzer.class);

    /**
     * Checks if the instruction is a Subqueries.* static method call.
     *
     * @param methodInsn the method instruction to check
     * @return true if this is a Subqueries factory method call
     */
    public boolean isSubqueriesMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(SUBQUERIES_INTERNAL_NAME);
    }

    /**
     * Checks if the instruction is a SubqueryBuilder.* instance method call.
     *
     * @param methodInsn the method instruction to check
     * @return true if this is a SubqueryBuilder method call
     */
    public boolean isSubqueryBuilderMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(SUBQUERY_BUILDER_INTERNAL_NAME);
    }

    /**
     * Handles Subqueries.subquery(Class) factory method.
     * <p>
     * This method creates a SubqueryBuilderReference that will be used by subsequent
     * INVOKEVIRTUAL calls to SubqueryBuilder methods.
     * <p>
     * Bytecode pattern:
     * <pre>
     * LDC Person.class                   → Constant(Class)
     * INVOKESTATIC Subqueries.subquery() → SubqueryBuilderReference(Person.class)
     * </pre>
     *
     * @param ctx the analysis context
     * @param methodInsn the method instruction
     */
    public void handleSubqueriesFactoryMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (!METHOD_SUBQUERY.equals(methodInsn.name)) {
            log.warnf("Unexpected Subqueries method: %s", methodInsn.name);
            return;
        }

        // Pop the entity class from stack
        LambdaExpression classExpr = ctx.pop();
        EntityClassInfo entityInfo = extractEntityClassInfo(classExpr);

        // Push SubqueryBuilderReference onto stack
        ctx.push(new SubqueryBuilderReference(entityInfo.clazz(), entityInfo.className()));
        log.debugf("Created SubqueryBuilderReference for %s", entityInfo.clazz().getSimpleName());
    }

    /**
     * Handles SubqueryBuilder.* method calls for subquery expressions.
     * <p>
     * Method mappings: avg/sum/min/max → ScalarSubquery, count → ScalarSubquery(COUNT),
     * exists/notExists → ExistsSubquery, in/notIn → InSubquery.
     *
     * @param ctx the analysis context
     * @param methodInsn the method instruction
     */
    public void handleSubqueryBuilderMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        String methodName = methodInsn.name;
        int argCount = DescriptorParser.countMethodArguments(methodInsn.desc);

        // Pop arguments from stack (but keep them for processing)
        List<LambdaExpression> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            if (!ctx.isStackEmpty()) {
                args.add(0, ctx.pop()); // Add at beginning to maintain order
            }
        }

        // Pop the SubqueryBuilderReference (the target of the method call)
        if (ctx.isStackEmpty()) {
            log.warnf("Stack empty when expecting SubqueryBuilderReference for %s", methodName);
            return;
        }

        LambdaExpression builderRef = ctx.pop();
        if (!(builderRef instanceof SubqueryBuilderReference subqueryBuilder)) {
            log.warnf("Expected SubqueryBuilderReference but got %s for %s",
                      builderRef.getClass().getSimpleName(), methodName);
            // Push everything back and return
            ctx.push(builderRef);
            for (LambdaExpression arg : args) {
                ctx.push(arg);
            }
            return;
        }

        Class<?> entityClass = subqueryBuilder.entityClass();
        String entityClassName = subqueryBuilder.entityClassName();
        LambdaExpression predicate = subqueryBuilder.predicate();

        // Handle different SubqueryBuilder methods
        switch (methodName) {
            case METHOD_WHERE -> handleBuilderWhere(ctx, subqueryBuilder, args);
            case SUBQUERY_AVG -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args, SubqueryAggregationType.AVG, Double.class);
            case SUBQUERY_SUM -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args, SubqueryAggregationType.SUM, Number.class);
            case SUBQUERY_MIN -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args, SubqueryAggregationType.MIN, Comparable.class);
            case SUBQUERY_MAX -> handleBuilderScalarSubquery(ctx, entityClass, entityClassName, predicate, args, SubqueryAggregationType.MAX, Comparable.class);
            case SUBQUERY_COUNT -> handleBuilderCountSubquery(ctx, entityClass, entityClassName, predicate, args);
            case SUBQUERY_EXISTS -> handleBuilderExistsSubquery(ctx, entityClass, entityClassName, args, false);
            case SUBQUERY_NOT_EXISTS -> handleBuilderExistsSubquery(ctx, entityClass, entityClassName, args, true);
            case SUBQUERY_IN -> handleBuilderInSubquery(ctx, entityClass, entityClassName, predicate, args, false);
            case SUBQUERY_NOT_IN -> handleBuilderInSubquery(ctx, entityClass, entityClassName, predicate, args, true);
            default -> log.debugf("Unhandled SubqueryBuilder method: %s", methodName);
        }
    }

    /**
     * Handles SubqueryBuilder.where(predicate) method.
     * <p>
     * This method adds a filtering predicate to the subquery builder.
     * It returns a new SubqueryBuilderReference with the predicate combined.
     */
    private void handleBuilderWhere(AnalysisContext ctx, SubqueryBuilderReference currentBuilder, List<LambdaExpression> args) {
        if (args.size() != 1) {
            log.warnf("Expected 1 argument for SubqueryBuilder.where, got %d", args.size());
            return;
        }

        LambdaExpression newPredicate = args.get(0);
        SubqueryBuilderReference updatedBuilder = currentBuilder.withPredicate(newPredicate);
        ctx.push(updatedBuilder);
    }

    /**
     * Handles SubqueryBuilder.avg/sum/min/max(selector) methods.
     * <p>
     * The predicate parameter comes from the SubqueryBuilderReference (set via .where() calls).
     */
    private void handleBuilderScalarSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
                                               LambdaExpression predicate, List<LambdaExpression> args,
                                               SubqueryAggregationType aggregationType, Class<?> defaultResultType) {
        if (args.size() != 1) {
            log.warnf("Expected 1 argument for SubqueryBuilder.%s, got %d", aggregationType, args.size());
            return;
        }

        LambdaExpression selector = args.get(0);
        Class<?> resultType = inferResultType(selector, aggregationType, defaultResultType);

        ctx.push(new ScalarSubquery(aggregationType, entityClass, entityClassName, selector, predicate, resultType));
    }

    /**
     * Handles SubqueryBuilder.count() and count(predicate) methods.
     * <p>
     * The predicate parameter comes from the SubqueryBuilderReference (set via .where() calls).
     * If count() is called with a predicate argument, it's combined with the builder's predicate.
     */
    private void handleBuilderCountSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
                                              LambdaExpression builderPredicate, List<LambdaExpression> args) {
        LambdaExpression argPredicate = args.isEmpty() ? null : args.get(0);

        // Combine predicates if both exist
        LambdaExpression finalPredicate = combinePredicates(builderPredicate, argPredicate);

        ctx.push(new ScalarSubquery(SubqueryAggregationType.COUNT, entityClass, entityClassName, null, finalPredicate, Long.class));
    }

    /**
     * Handles SubqueryBuilder.exists/notExists(predicate) methods.
     * <p>
     * EXISTS/NOT EXISTS don't use the builder's predicate - they always use the provided predicate.
     */
    private void handleBuilderExistsSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
                                               List<LambdaExpression> args, boolean negated) {
        if (args.size() != 1) {
            log.warnf("Expected 1 argument for SubqueryBuilder.%s, got %d", negated ? "notExists" : "exists", args.size());
            return;
        }

        LambdaExpression predicate = args.get(0);
        ctx.push(new ExistsSubquery(entityClass, entityClassName, predicate, negated));
    }

    /**
     * Handles SubqueryBuilder.in/notIn(field, selector) and (field, selector, predicate) methods.
     * <p>
     * The builderPredicate parameter comes from the SubqueryBuilderReference (set via .where() calls).
     * If in/notIn is called with a predicate argument, it's combined with the builder's predicate.
     */
    private void handleBuilderInSubquery(AnalysisContext ctx, Class<?> entityClass, String entityClassName,
                                          LambdaExpression builderPredicate, List<LambdaExpression> args, boolean negated) {
        if (args.size() < 2 || args.size() > 3) {
            log.warnf("Expected 2-3 arguments for SubqueryBuilder.%s, got %d", negated ? "notIn" : "in", args.size());
            return;
        }

        LambdaExpression field = args.get(0);
        LambdaExpression selector = args.get(1);
        LambdaExpression argPredicate = args.size() == 3 ? args.get(2) : null;

        // Combine predicates if both exist
        LambdaExpression finalPredicate = combinePredicates(builderPredicate, argPredicate);

        ctx.push(new InSubquery(field, entityClass, entityClassName, selector, finalPredicate, negated));
    }

    /**
     * Combines two predicates with AND operator if both are non-null.
     */
    private LambdaExpression combinePredicates(LambdaExpression predicate1, LambdaExpression predicate2) {
        if (predicate1 != null && predicate2 != null) {
            return and(predicate1, predicate2);
        } else if (predicate1 != null) {
            return predicate1;
        } else {
            return predicate2;
        }
    }

    /**
     * Holds entity class information including both the Class object and optional class name.
     * The className is only set when the class cannot be loaded at build-time.
     */
    record EntityClassInfo(Class<?> clazz, String className) {}

    /**
     * Extracts entity class and optional class name from a constant expression.
     * <p>
     * Strategy: Extract className early when Type is available, then attempt
     * class loading. If loading fails, preserve className for runtime resolution.
     */
    private EntityClassInfo extractEntityClassInfo(LambdaExpression expr) {
        if (expr instanceof LambdaExpression.Constant constant) {
            Object value = constant.value();
            if (value instanceof Type asmType) {
                // Extract className FIRST (before attempting class loading)
                String className = asmType.getClassName();

                // Attempt to load the class
                Class<?> loadedClass = tryLoadClass(className);

                if (loadedClass != null) {
                    // Successfully loaded - className not needed
                    return new EntityClassInfo(loadedClass, null);
                } else {
                    // Failed to load - preserve className for code generation
                    log.debugf("Entity class not loadable at analysis time: %s (will resolve at code generation)", className);
                    return new EntityClassInfo(Object.class, className);
                }
            } else if (value instanceof Class<?> clazz) {
                return new EntityClassInfo(clazz, null);
            }
        }
        log.warnf("Expected Class constant for entity class, got: %s", expr);
        return new EntityClassInfo(Object.class, null);
    }

    /**
     * Attempts to load class using multiple classloaders.
     *
     * @param className the fully qualified class name
     * @return loaded Class, or null if not loadable
     */
    private Class<?> tryLoadClass(String className) {
        try {
            // Try context class loader first
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e1) {
            try {
                // Fallback to this class's class loader
                return Class.forName(className);
            } catch (ClassNotFoundException e2) {
                // Class not loadable at build-time
                return null;
            }
        }
    }

    /**
     * Infers the result type for a scalar subquery based on the selector and aggregation type.
     */
    private Class<?> inferResultType(LambdaExpression selector, SubqueryAggregationType aggregationType,
                                      Class<?> defaultResultType) {
        // AVG always returns Double
        if (aggregationType == SubqueryAggregationType.AVG) {
            return Double.class;
        }

        // Try to infer from the selector expression
        if (selector instanceof LambdaExpression.FieldAccess field) {
            return field.fieldType();
        } else if (selector instanceof LambdaExpression.PathExpression path) {
            return path.resultType();
        }

        return defaultResultType;
    }
}
