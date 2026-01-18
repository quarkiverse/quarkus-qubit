package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContext.PopPairResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.eq;
import static io.quarkiverse.qubit.deployment.common.BytecodeAnalysisConstants.*;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.extractFieldName;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isGetterMethodName;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isEntityFieldExpression;
import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnsIntType;
import static io.quarkiverse.qubit.runtime.QubitConstants.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles method invocations: INVOKEVIRTUAL (equals, String, compareTo, BigDecimal, temporal, getters),
 * INVOKESTATIC (Boolean.valueOf skip, temporal factory methods with constant folding),
 * INVOKESPECIAL (constructors including BigDecimal constant folding),
 * INVOKEINTERFACE (Collection.contains for IN and MEMBER OF expressions).
 */
public enum MethodInvocationHandler implements InstructionHandler {
    INSTANCE;

    private final SubqueryAnalyzer subqueryAnalyzer = new SubqueryAnalyzer();
    private final GroupMethodAnalyzer groupMethodAnalyzer = new GroupMethodAnalyzer();

    /**
     * Maps temporal type owners to their valid accessor method sets.
     * Used to dispatch temporal accessor methods without duplicating method lists.
     */
    private static final Map<String, Set<String>> TEMPORAL_ACCESSOR_METHODS_BY_TYPE = Map.of(
        JVM_JAVA_TIME_LOCAL_DATE, LOCAL_DATE_ACCESSOR_METHODS,
        JVM_JAVA_TIME_LOCAL_DATE_TIME, LOCAL_DATE_TIME_ACCESSOR_METHODS,
        JVM_JAVA_TIME_LOCAL_TIME, LOCAL_TIME_ACCESSOR_METHODS
    );

    /** Virtual method categories, checked in priority order. */
    public enum VirtualMethodCategory {
        EQUALS,
        SUBQUERY_BUILDER,
        STRING_METHOD,
        COMPARE_TO,
        BIG_DECIMAL_ARITHMETIC,
        TEMPORAL_METHOD,
        GETTER,
        UNHANDLED;

        /**
         * Categorizes a virtual method invocation by checking patterns in priority order.
         */
        public static VirtualMethodCategory categorize(
                MethodInsnNode methodInsn,
                MethodInvocationHandler handler) {

            // Priority 1: equals() method
            if (handler.isEqualsMethodCall(methodInsn)) {
                return EQUALS;
            }

            // Priority 2: SubqueryBuilder methods (delegated analysis)
            if (handler.subqueryAnalyzer.isSubqueryBuilderMethodCall(methodInsn)) {
                return SUBQUERY_BUILDER;
            }

            // Priority 3: String methods
            if (methodInsn.owner.equals(JVM_JAVA_LANG_STRING)) {
                return STRING_METHOD;
            }

            // Priority 4: compareTo() method
            if (handler.isCompareToMethodCall(methodInsn)) {
                return COMPARE_TO;
            }

            // Priority 5: BigDecimal arithmetic
            if (handler.isBigDecimalArithmeticCall(methodInsn)) {
                return BIG_DECIMAL_ARITHMETIC;
            }

            // Priority 6: Temporal methods (LocalDate, LocalDateTime, LocalTime)
            if (methodInsn.owner.startsWith(JVM_PREFIX_JAVA_TIME_LOCAL)) {
                return TEMPORAL_METHOD;
            }

            // Priority 7: Getter methods
            if (handler.isGetterMethodCall(methodInsn)) {
                return GETTER;
            }

            // Default: Unhandled
            return UNHANDLED;
        }
    }

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == INVOKEVIRTUAL || opcode == INVOKESTATIC ||
               opcode == INVOKESPECIAL || opcode == INVOKEINTERFACE;
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();
        MethodInsnNode methodInsn = (MethodInsnNode) insn;

        switch (opcode) {
            case INVOKEVIRTUAL -> handleInvokeVirtual(ctx, methodInsn);
            case INVOKESTATIC -> handleInvokeStatic(ctx, methodInsn);
            case INVOKESPECIAL -> handleInvokeSpecial(ctx, methodInsn);
            case INVOKEINTERFACE -> handleInvokeInterface(ctx, methodInsn);
        }

        // Continue processing (don't terminate analysis)
        return false;
    }

    /**
     * Handles INVOKEVIRTUAL: equals, String, compareTo, BigDecimal, temporal, SubqueryBuilder, getters.
     */
    private void handleInvokeVirtual(AnalysisContext ctx, MethodInsnNode methodInsn) {
        VirtualMethodCategory category = VirtualMethodCategory.categorize(methodInsn, this);

        switch (category) {
            case EQUALS -> handleEqualsMethod(ctx);
            case SUBQUERY_BUILDER -> subqueryAnalyzer.handleSubqueryBuilderMethod(ctx, methodInsn);
            case STRING_METHOD -> handleStringMethods(ctx, methodInsn);
            case COMPARE_TO -> handleSingleArgumentMethodCall(ctx, METHOD_COMPARE_TO, int.class);
            case BIG_DECIMAL_ARITHMETIC -> handleBigDecimalMethods(ctx, methodInsn);
            case TEMPORAL_METHOD -> handleTemporalMethods(ctx, methodInsn);
            case GETTER -> handleGetterMethod(ctx, methodInsn);
            case UNHANDLED -> { /* no-op */ }
        }
    }

    /** Handles INVOKESTATIC: skips Boolean.valueOf, handles temporal factory methods with constant folding,
     *  and handles Subqueries.subquery() factory method for subquery builder pattern. */
    private void handleInvokeStatic(AnalysisContext ctx, MethodInsnNode staticInsn) {
        // Skip Boolean.valueOf (optimization - we work directly with boolean expressions)
        if (staticInsn.owner.equals(JVM_JAVA_LANG_BOOLEAN) &&
            staticInsn.name.equals(METHOD_VALUE_OF) &&
            staticInsn.desc.equals(DESC_BOOLEAN_VALUE_OF)) {
            return;
        }

        // Handle Subqueries.subquery(Class) factory method (delegated to SubqueryAnalyzer)
        if (subqueryAnalyzer.isSubqueriesMethodCall(staticInsn)) {
            subqueryAnalyzer.handleSubqueriesFactoryMethod(ctx, staticInsn);
            return;
        }

        // Handle temporal factory methods (LocalDate.of, LocalDateTime.of, LocalTime.of)
        handleTemporalFactoryMethod(ctx, staticInsn, LocalDate.class, 3,
                args -> LocalDate.of(args[0], args[1], args[2]));
        handleTemporalFactoryMethod(ctx, staticInsn, LocalDateTime.class, 5,
                args -> LocalDateTime.of(args[0], args[1], args[2], args[3], args[4]));
        handleTemporalFactoryMethod(ctx, staticInsn, LocalTime.class, 2,
                args -> LocalTime.of(args[0], args[1]));
    }

    /** Handles INVOKESPECIAL: constructor calls with BigDecimal constant folding. */
    private void handleInvokeSpecial(AnalysisContext ctx, MethodInsnNode specialInsn) {
        if (!specialInsn.name.equals(CONSTRUCTOR)) {
            return;
        }

        try {
            int argCount = DescriptorParser.countMethodArguments(specialInsn.desc);

            List<LambdaExpression> args = ctx.popN(argCount);
            if (args == null) {
                args = new ArrayList<>();
            }

            ctx.discardN(2);

            // Special handling: BigDecimal(String) constructor with constant folding
            if (isBigDecimalStringConstruction(specialInsn, argCount, args)) {
                LambdaExpression.Constant constant = (LambdaExpression.Constant) args.get(0);
                String stringValue = (String) constant.value();
                handleBigDecimalConstantFolding(ctx, args, stringValue, specialInsn.owner);
            } else {
                pushConstructorCall(ctx, args, specialInsn.owner);
            }
        } catch (Exception e) {
            Log.errorf(e, "Error processing INVOKESPECIAL %s for %s", CONSTRUCTOR, specialInsn.owner);
            throw e;
        }
    }

    /** Handles INVOKEINTERFACE: Collection.contains() for IN/MEMBER OF, and Group methods. */
    private void handleInvokeInterface(AnalysisContext ctx, MethodInsnNode interfaceInsn) {
        // Check if this is a Group interface method call (delegated to GroupMethodAnalyzer)
        if (groupMethodAnalyzer.isGroupMethodCall(interfaceInsn)) {
            groupMethodAnalyzer.handleGroupMethod(ctx, interfaceInsn);
            return;
        }

        // Check if this is a Collection.contains() call
        if (isCollectionContainsCall(interfaceInsn)) {
            handleCollectionContains(ctx);
            return;
        }

        // Fallback: treat as regular method call (similar to INVOKEVIRTUAL)
        // This handles other interface method calls that might be needed
        if (isEqualsMethodCall(interfaceInsn)) {
            handleEqualsMethod(ctx);
            return;
        }

        if (isCompareToMethodCall(interfaceInsn)) {
            handleSingleArgumentMethodCall(ctx, METHOD_COMPARE_TO, int.class);
        }
    }

    private boolean isCollectionContainsCall(MethodInsnNode methodInsn) {
        return methodInsn.name.equals(METHOD_CONTAINS) &&
               methodInsn.desc.equals(DESC_OBJECT_TO_BOOLEAN) &&
               COLLECTION_INTERFACE_OWNERS.contains(methodInsn.owner);
    }

    /** Distinguishes IN clause (captured collection) from MEMBER OF (entity collection field). */
    private void handleCollectionContains(AnalysisContext ctx) {
        PopPairResult pair = ctx.popPair();
        if (pair == null) {
            return;
        }

        LambdaExpression argument = pair.right();  // The contains() argument (was on top)
        LambdaExpression target = pair.left();     // The collection (target of contains())

        // Determine if this is IN clause or MEMBER OF pattern
        if (isInClausePattern(target, argument)) {
            // IN clause: collection.contains(field)
            // The argument is the field we're checking, target is the collection
            ctx.push(InExpression.in(argument, target));
        } else if (isMemberOfPattern(target, argument)) {
            // MEMBER OF: entityField.contains(value)
            // The target is the collection field, argument is the value we're checking
            ctx.push(MemberOfExpression.memberOf(argument, target));
        } else {
            // Fallback: create a regular MethodCall for unknown patterns
            ctx.push(new LambdaExpression.MethodCall(
                    target,
                    METHOD_CONTAINS,
                    List.of(argument),
                    boolean.class
            ));
        }
    }

    /**
     * IN clause: captured collection.contains(entity field) → cities.contains(p.city)
     */
    private boolean isInClausePattern(LambdaExpression target, LambdaExpression argument) {
        // Target must be a captured variable (the collection from outer scope)
        boolean targetIsCaptured = target instanceof LambdaExpression.CapturedVariable;

        // Argument must be a field access or path expression (entity field)
        // Supports both single-entity and bi-entity expressions
        return targetIsCaptured && isEntityFieldExpression(argument);
    }

    /**
     * MEMBER OF: entity collection.contains(value) → p.roles.contains("admin")
     */
    private boolean isMemberOfPattern(LambdaExpression target, LambdaExpression argument) {
        // Target must be a field access or path expression (collection field on entity)
        // Supports both single-entity and bi-entity expressions
        boolean targetIsEntityField = isEntityFieldExpression(target);

        // Argument must be a constant or captured variable (the value to check)
        boolean argumentIsValue = argument instanceof LambdaExpression.Constant ||
                                  argument instanceof LambdaExpression.CapturedVariable;

        return targetIsEntityField && argumentIsValue;
    }

    private boolean isEqualsMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.name.equals(METHOD_EQUALS) && methodInsn.desc.equals(DESC_OBJECT_TO_BOOLEAN);
    }

    private void handleEqualsMethod(AnalysisContext ctx) {
        PopPairResult pair = ctx.popPair();
        if (pair != null) {
            ctx.push(eq(pair.left(), pair.right()));
        }
    }

    private boolean isCompareToMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.name.equals(METHOD_COMPARE_TO) && returnsIntType(methodInsn.desc);
    }

    private boolean isBigDecimalArithmeticCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(JVM_JAVA_MATH_BIG_DECIMAL) &&
               methodInsn.desc.equals(DESC_BIG_DECIMAL_ARITHMETIC);
    }

    private boolean isGetterMethodCall(MethodInsnNode methodInsn) {
        return isGetterMethodName(methodInsn.name) && methodInsn.desc.startsWith("()");
    }

    private void handleStringMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_STARTS_WITH, METHOD_ENDS_WITH ->
                handleSingleArgumentStringMethod(ctx, methodInsn, DESC_STRING_TO_BOOLEAN, boolean.class);

            case METHOD_CONTAINS ->
                handleSingleArgumentStringMethod(ctx, methodInsn, DESC_CHAR_SEQUENCE_TO_BOOLEAN, boolean.class);

            case METHOD_LENGTH ->
                handleNoArgumentStringMethod(ctx, methodInsn, DESC_NO_ARG_TO_INT, int.class);

            case METHOD_IS_EMPTY ->
                handleNoArgumentStringMethod(ctx, methodInsn, DESC_NO_ARG_TO_BOOLEAN, boolean.class);

            case METHOD_TO_LOWER_CASE, METHOD_TO_UPPER_CASE, METHOD_TRIM ->
                handleNoArgumentStringMethod(ctx, methodInsn, DESC_NO_ARG_TO_STRING, String.class);

            case METHOD_SUBSTRING ->
                handleSubstringMethod(ctx, methodInsn);

            default -> { /* No action for unrecognized String methods */ }
        }
    }

    private void handleSingleArgumentStringMethod(AnalysisContext ctx, MethodInsnNode methodInsn,
                                                   String expectedDescriptor, Class<?> returnType) {
        if (methodInsn.desc.equals(expectedDescriptor)) {
            handleSingleArgumentMethodCall(ctx, methodInsn.name, returnType);
        }
    }

    private void handleNoArgumentStringMethod(AnalysisContext ctx, MethodInsnNode methodInsn,
                                              String expectedDescriptor, Class<?> returnType) {
        if (methodInsn.desc.equals(expectedDescriptor)) {
            handleNoArgumentMethodCall(ctx, methodInsn.name, returnType);
        }
    }

    private void handleSubstringMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (methodInsn.desc.equals(DESC_INT_TO_STRING) ||
            methodInsn.desc.equals(DESC_TWO_INTS_TO_STRING)) {
            int argCount = methodInsn.desc.equals(DESC_INT_TO_STRING) ? 1 : 2;
            if (ctx.getStackSize() >= argCount + 1) {
                List<LambdaExpression> arguments = new ArrayList<>();
                for (int i = 0; i < argCount; i++) {
                    arguments.add(0, ctx.pop());
                }
                LambdaExpression target = ctx.pop();
                ctx.push(new LambdaExpression.MethodCall(target, METHOD_SUBSTRING, arguments, String.class));
            }
        }
    }

    private void handleBigDecimalMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_ADD, METHOD_SUBTRACT, METHOD_MULTIPLY, METHOD_DIVIDE ->
                handleSingleArgumentMethodCall(ctx, methodInsn.name, BigDecimal.class);
            default -> { /* no-op */ }
        }
    }

    private void handleTemporalMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodInsn.name)) {
            handleSingleArgumentMethodCall(ctx, methodInsn.name, boolean.class);
        } else {
            Set<String> validMethods = TEMPORAL_ACCESSOR_METHODS_BY_TYPE.get(methodInsn.owner);
            if (validMethods != null && validMethods.contains(methodInsn.name)) {
                handleTemporalAccessorMethod(ctx, methodInsn);
            }
        }
    }

    private void handleTemporalAccessorMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (!ctx.isStackEmpty()) {
            LambdaExpression target = ctx.pop();
            ctx.push(new LambdaExpression.MethodCall(
                target,
                methodInsn.name,
                List.of(),
                int.class
            ));
        }
    }

    /** Converts getter calls to field access; produces BiEntityFieldAccess in bi-entity mode. */
    private void handleGetterMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        String fieldName = extractFieldName(methodInsn.name);
        String returnTypeDesc = methodInsn.desc.substring(2);
        Class<?> returnType = TypeConverter.descriptorToClass(returnTypeDesc);

        // Handle bi-entity parameters for join queries
        if (target instanceof LambdaExpression.BiEntityParameter biParam) {
            ctx.push(new LambdaExpression.BiEntityFieldAccess(fieldName, returnType, biParam.position()));
        } else {
            ctx.push(new LambdaExpression.FieldAccess(fieldName, returnType));
        }
    }

    private void handleNoArgumentMethodCall(AnalysisContext ctx, String methodName, Class<?> returnType) {
        if (!ctx.isStackEmpty()) {
            LambdaExpression target = ctx.pop();
            ctx.push(new LambdaExpression.MethodCall(
                target,
                methodName,
                List.of(),
                returnType
            ));
        }
    }

    private void handleSingleArgumentMethodCall(AnalysisContext ctx, String methodName, Class<?> returnType) {
        PopPairResult pair = ctx.popPair();
        if (pair != null) {
            ctx.push(new LambdaExpression.MethodCall(
                pair.left(),   // The object calling the method (was second-to-top)
                methodName,
                List.of(pair.right()),  // The method argument (was on top)
                returnType
            ));
        }
    }

    /** Temporal factory methods with constant folding: evaluates at analysis time if all args constant. */
    private <T> void handleTemporalFactoryMethod(
            AnalysisContext ctx,
            MethodInsnNode staticInsn,
            Class<T> temporalClass,
            int expectedArgCount,
            Function<int[], T> evaluator) {

        String expectedOwner = "java/time/" + temporalClass.getSimpleName();
        if (!staticInsn.owner.equals(expectedOwner) || !staticInsn.name.equals(METHOD_OF)) {
            return;
        }

        int argCount = DescriptorParser.countMethodArguments(staticInsn.desc);

        if (argCount != expectedArgCount || ctx.getStackSize() < expectedArgCount) {
            return;
        }

        LambdaExpression[] args = new LambdaExpression[expectedArgCount];
        for (int i = expectedArgCount - 1; i >= 0; i--) {
            args[i] = ctx.pop();
        }

        // Check if all arguments are constants
        boolean allConstants = true;
        int[] constantValues = new int[expectedArgCount];
        for (int i = 0; i < expectedArgCount; i++) {
            if (args[i] instanceof LambdaExpression.Constant constant) {
                constantValues[i] = ((Number) constant.value()).intValue();
            } else {
                allConstants = false;
                break;
            }
        }

        // Constant folding optimization
        if (allConstants) {
            try {
                T value = evaluator.apply(constantValues);
                ctx.push(new LambdaExpression.Constant(value, temporalClass));
                return;
            } catch (Exception e) {
                Log.debugf("Failed to evaluate constant temporal value, will create method call instead: %s",
                           e.getMessage());
            }
        }

        // Fallback: create method call
        ctx.push(new LambdaExpression.MethodCall(null, METHOD_OF, List.of(args), temporalClass));
    }

    private boolean isBigDecimalStringConstruction(MethodInsnNode specialInsn, int argCount,
                                                    List<LambdaExpression> args) {
        return specialInsn.owner.equals(JVM_JAVA_MATH_BIG_DECIMAL) &&
               argCount == 1 &&
               !args.isEmpty() &&
               args.get(0) instanceof LambdaExpression.Constant constant &&
               constant.value() instanceof String;
    }

    /** Folds BigDecimal(String) to constant at build time; falls back to constructor call. */
    private void handleBigDecimalConstantFolding(AnalysisContext ctx, List<LambdaExpression> args,
                                                  String stringValue, String owner) {
        try {
            BigDecimal value = new BigDecimal(stringValue);
            ctx.push(new LambdaExpression.Constant(value, BigDecimal.class));
        } catch (NumberFormatException e) {
            pushConstructorCall(ctx, args, owner);
        }
    }

    private void pushConstructorCall(AnalysisContext ctx, List<LambdaExpression> args, String owner) {
        Class<?> constructedType = TypeConverter.descriptorToClass("L" + owner + ";");
        ctx.push(new LambdaExpression.ConstructorCall(owner, args, constructedType));
    }

    // Note: Subquery Methods have been extracted to SubqueryAnalyzer
}
