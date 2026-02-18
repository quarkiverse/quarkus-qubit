package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.eq;
import static io.quarkiverse.qubit.deployment.common.BytecodeAnalysisConstants.*;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.extractFieldName;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isGetterMethodName;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isEntityFieldExpression;
import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnsIntType;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;
import static org.objectweb.asm.Opcodes.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContext.PopPairResult;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import io.quarkus.logging.Log;

/**
 * Handles method invocations: INVOKEVIRTUAL (equals, String, compareTo, BigDecimal, temporal, getters),
 * INVOKESTATIC (Boolean.valueOf skip, temporal factory methods with constant folding),
 * INVOKESPECIAL (constructors including BigDecimal constant folding),
 * INVOKEINTERFACE (Collection.contains for IN and MEMBER OF expressions).
 */
public enum MethodInvocationHandler implements InstructionHandler {
    INSTANCE;

    /** Opcodes handled by this handler for O(1) dispatch. */
    private static final Set<Integer> SUPPORTED_OPCODES = Set.of(
            INVOKEVIRTUAL, INVOKESTATIC, INVOKESPECIAL, INVOKEINTERFACE);

    private final SubqueryAnalyzer subqueryAnalyzer = new SubqueryAnalyzer();
    private final GroupMethodAnalyzer groupMethodAnalyzer = new GroupMethodAnalyzer();

    @Override
    public Set<Integer> supportedOpcodes() {
        return SUPPORTED_OPCODES;
    }

    /**
     * Maps temporal type owners to their valid accessor method sets.
     * Used to dispatch temporal accessor methods without duplicating method lists.
     */
    private static final Map<String, Set<String>> TEMPORAL_ACCESSOR_METHODS_BY_TYPE = Map.of(
            JVM_JAVA_TIME_LOCAL_DATE, LOCAL_DATE_ACCESSOR_METHODS,
            JVM_JAVA_TIME_LOCAL_DATE_TIME, LOCAL_DATE_TIME_ACCESSOR_METHODS,
            JVM_JAVA_TIME_LOCAL_TIME, LOCAL_TIME_ACCESSOR_METHODS);

    /**
     * Specification for temporal factory method constant folding.
     * Each spec defines a temporal type, expected argument count, and evaluator function.
     */
    private record TemporalFactorySpec(
            Class<?> temporalClass,
            int argCount,
            Function<int[], Object> evaluator) {
    }

    /**
     * Registry of temporal factory methods for constant folding optimization.
     * Data-driven approach eliminates repetitive handleTemporalFactoryMethod calls.
     */
    private static final List<TemporalFactorySpec> TEMPORAL_FACTORY_SPECS = List.of(
            // LocalDate.of(year, month, day)
            new TemporalFactorySpec(LocalDate.class, 3,
                    args -> LocalDate.of(args[0], args[1], args[2])),
            // LocalDateTime.of(year, month, day, hour, minute)
            new TemporalFactorySpec(LocalDateTime.class, 5,
                    args -> LocalDateTime.of(args[0], args[1], args[2], args[3], args[4])),
            // LocalDateTime.of(year, month, day, hour, minute, second)
            new TemporalFactorySpec(LocalDateTime.class, 6,
                    args -> LocalDateTime.of(args[0], args[1], args[2], args[3], args[4], args[5])),
            // LocalTime.of(hour, minute)
            new TemporalFactorySpec(LocalTime.class, 2,
                    args -> LocalTime.of(args[0], args[1])),
            // LocalTime.of(hour, minute, second)
            new TemporalFactorySpec(LocalTime.class, 3,
                    args -> LocalTime.of(args[0], args[1], args[2])));

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
        return SUPPORTED_OPCODES.contains(insn.getOpcode());
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
            case UNHANDLED -> {
                /* no-op */ }
        }
    }

    /**
     * Handles INVOKESTATIC: skips auto-boxing valueOf, handles temporal factory methods with constant folding,
     * and handles Subqueries.subquery() factory method for subquery builder pattern.
     */
    private void handleInvokeStatic(AnalysisContext ctx, MethodInsnNode staticInsn) {
        // Skip auto-boxing valueOf calls (Boolean, Integer, Long, Double, Float, Short, Byte)
        // These are identity wrappers that don't affect query semantics
        if (staticInsn.name.equals(METHOD_VALUE_OF) && isBoxingCall(staticInsn)) {
            return;
        }

        // Handle Math static methods (Math.abs, Math.sqrt, Math.ceil, etc.)
        if (handleMathStaticMethod(ctx, staticInsn)) {
            return;
        }

        // Handle Integer.signum() and Long.signum()
        if (handleSignumMethod(ctx, staticInsn)) {
            return;
        }

        // Handle QubitMath.round(value, decimalPlaces) marker method
        if (handleQubitMathMethod(ctx, staticInsn)) {
            return;
        }

        // Handle Subqueries.subquery(Class) factory method (delegated to SubqueryAnalyzer)
        if (subqueryAnalyzer.isSubqueriesMethodCall(staticInsn)) {
            subqueryAnalyzer.handleSubqueriesFactoryMethod(ctx, staticInsn);
            return;
        }

        // Handle temporal factory methods via data-driven registry
        for (TemporalFactorySpec spec : TEMPORAL_FACTORY_SPECS) {
            if (handleTemporalFactoryMethod(ctx, staticInsn, spec)) {
                return; // First matching spec wins
            }
        }

        // Catch-all: attempt constant folding for unknown static methods
        handleUnknownStaticMethod(ctx, staticInsn);
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
                LambdaExpression.Constant constant = (LambdaExpression.Constant) args.getFirst();
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

        LambdaExpression argument = pair.right(); // The contains() argument (was on top)
        LambdaExpression target = pair.left(); // The collection (target of contains())

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
                    boolean.class));
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

            case METHOD_IS_EMPTY, METHOD_IS_BLANK ->
                handleNoArgumentStringMethod(ctx, methodInsn, DESC_NO_ARG_TO_BOOLEAN, boolean.class);

            case METHOD_TO_LOWER_CASE, METHOD_TO_UPPER_CASE, METHOD_TRIM ->
                handleNoArgumentStringMethod(ctx, methodInsn, DESC_NO_ARG_TO_STRING, String.class);

            case METHOD_SUBSTRING ->
                handleSubstringMethod(ctx, methodInsn);

            case METHOD_INDEX_OF ->
                handleIndexOfMethod(ctx, methodInsn);

            default -> {
                /* No action for unrecognized String methods */ }
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

    /** Handles String.indexOf(String) and String.indexOf(String, int) -> MethodCall. */
    private void handleIndexOfMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        // indexOf(String) — descriptor: (Ljava/lang/String;)I
        if (methodInsn.desc.equals(DESC_STRING_TO_INT)) {
            handleSingleArgumentMethodCall(ctx, METHOD_INDEX_OF, int.class);
            return;
        }
        // indexOf(String, int) — descriptor: (Ljava/lang/String;I)I
        if (methodInsn.desc.equals(DESC_STRING_INT_TO_INT) && ctx.getStackSize() >= 3) {
            List<LambdaExpression> arguments = new ArrayList<>();
            arguments.add(0, ctx.pop()); // fromIndex (was on top)
            arguments.add(0, ctx.pop()); // searchString
            LambdaExpression target = ctx.pop();
            ctx.push(new LambdaExpression.MethodCall(target, METHOD_INDEX_OF, arguments, int.class));
        }
        // indexOf(int) and indexOf(int, int) — char-based: not supported, fall through silently
    }

    private void handleBigDecimalMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_ADD, METHOD_SUBTRACT, METHOD_MULTIPLY, METHOD_DIVIDE ->
                handleSingleArgumentMethodCall(ctx, methodInsn.name, BigDecimal.class);
            default -> {
                /* no-op */ }
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
                    int.class));
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
        if (target instanceof LambdaExpression.BiEntityParameter(_, _, _, var position)) {
            ctx.push(new LambdaExpression.BiEntityFieldAccess(fieldName, returnType, position));
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
                    returnType));
        }
    }

    private void handleSingleArgumentMethodCall(AnalysisContext ctx, String methodName, Class<?> returnType) {
        PopPairResult pair = ctx.popPair();
        if (pair != null) {
            ctx.push(new LambdaExpression.MethodCall(
                    pair.left(), // The object calling the method (was second-to-top)
                    methodName,
                    List.of(pair.right()), // The method argument (was on top)
                    returnType));
        }
    }

    /**
     * Temporal factory methods with constant folding: evaluates at analysis time if all args constant.
     *
     * @return true if this spec matched and was handled, false otherwise
     */
    private boolean handleTemporalFactoryMethod(
            AnalysisContext ctx,
            MethodInsnNode staticInsn,
            TemporalFactorySpec spec) {

        String expectedOwner = "java/time/" + spec.temporalClass().getSimpleName();
        if (!staticInsn.owner.equals(expectedOwner) || !staticInsn.name.equals(METHOD_OF)) {
            return false;
        }

        int argCount = DescriptorParser.countMethodArguments(staticInsn.desc);
        if (argCount != spec.argCount() || ctx.getStackSize() < spec.argCount()) {
            return false;
        }

        LambdaExpression[] args = new LambdaExpression[spec.argCount()];
        for (int i = spec.argCount() - 1; i >= 0; i--) {
            args[i] = ctx.pop();
        }

        // Check if all arguments are constants for constant folding
        int[] constantValues = new int[spec.argCount()];
        boolean allConstants = extractConstantValues(args, constantValues);

        // Constant folding optimization
        if (allConstants) {
            try {
                Object value = spec.evaluator().apply(constantValues);
                ctx.push(new LambdaExpression.Constant(value, spec.temporalClass()));
                return true;
            } catch (Exception e) {
                Log.debugf("Failed to evaluate constant temporal value, will create method call instead: %s",
                        e.getMessage());
            }
        }

        // Fallback: create method call
        ctx.push(new LambdaExpression.MethodCall(null, METHOD_OF, List.of(args), spec.temporalClass()));
        return true;
    }

    /** Extracts constant int values from arguments. Returns true if all args are constants. */
    private static boolean extractConstantValues(LambdaExpression[] args, int[] values) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof LambdaExpression.Constant(var value, _)) {
                values[i] = ((Number) value).intValue();
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean isBigDecimalStringConstruction(MethodInsnNode specialInsn, int argCount,
            List<LambdaExpression> args) {
        return specialInsn.owner.equals(JVM_JAVA_MATH_BIG_DECIMAL) &&
                argCount == 1 &&
                !args.isEmpty() &&
                args.getFirst() instanceof LambdaExpression.Constant constant &&
                constant.value() instanceof String;
    }

    /** Folds BigDecimal(String) to constant at build time; falls back to constructor call. */
    private void handleBigDecimalConstantFolding(AnalysisContext ctx, List<LambdaExpression> args,
            String stringValue, String owner) {
        try {
            BigDecimal value = new BigDecimal(stringValue);
            ctx.push(new LambdaExpression.Constant(value, BigDecimal.class));
        } catch (NumberFormatException _) {
            pushConstructorCall(ctx, args, owner);
        }
    }

    private void pushConstructorCall(AnalysisContext ctx, List<LambdaExpression> args, String owner) {
        Class<?> constructedType = TypeConverter.descriptorToClass("L" + owner + ";");
        ctx.push(new LambdaExpression.ConstructorCall(owner, args, constructedType));
    }

    // ─── Math Static Method Handling ─────────────────────────────────────────

    /** Dispatches java.lang.Math static methods to unary, binary, or round handlers. */
    private boolean handleMathStaticMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
        if (!staticInsn.owner.equals(JVM_JAVA_LANG_MATH)) {
            return false;
        }
        String methodName = staticInsn.name;

        if (MATH_UNARY_METHODS.contains(methodName)) {
            return handleUnaryMathMethod(ctx, methodName);
        }
        if (MATH_BINARY_METHODS.contains(methodName)) {
            return handleBinaryMathMethod(ctx, methodName);
        }
        if (methodName.equals(METHOD_ROUND)) {
            return handleMathRound(ctx);
        }
        return false;
    }

    /** Pops one operand and pushes a unary MathFunction (abs, sqrt, ceil, floor, exp, log, signum). */
    private boolean handleUnaryMathMethod(AnalysisContext ctx, String methodName) {
        if (ctx.getStack().isEmpty()) {
            return false;
        }
        LambdaExpression operand = ctx.pop();
        LambdaExpression.MathFunction.MathOp op = mapUnaryMathOp(methodName);
        ctx.push(new LambdaExpression.MathFunction(op, operand, null));
        return true;
    }

    /** Pops two operands and pushes a binary MathFunction (pow). */
    private boolean handleBinaryMathMethod(AnalysisContext ctx, String methodName) {
        if (ctx.getStack().size() < 2) {
            return false;
        }
        LambdaExpression secondOperand = ctx.pop();
        LambdaExpression firstOperand = ctx.pop();
        LambdaExpression.MathFunction.MathOp op = mapBinaryMathOp(methodName);
        ctx.push(new LambdaExpression.MathFunction(op, firstOperand, secondOperand));
        return true;
    }

    /** Handles Math.round(x) → MathFunction.round(operand, 0) for integer rounding. */
    private boolean handleMathRound(AnalysisContext ctx) {
        if (ctx.getStack().isEmpty()) {
            return false;
        }
        LambdaExpression operand = ctx.pop();
        ctx.push(LambdaExpression.MathFunction.round(operand, LambdaExpression.Constant.ZERO_INT));
        return true;
    }

    /** Handles QubitMath.round(value, decimalPlaces) marker method for arbitrary-precision rounding. */
    private boolean handleQubitMathMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
        if (!staticInsn.owner.equals(JVM_QUBIT_MATH)) {
            return false;
        }
        if (!staticInsn.name.equals(METHOD_ROUND)) {
            return false;
        }
        if (ctx.getStack().size() < 2) {
            return false;
        }
        LambdaExpression decimalPlaces = ctx.pop();
        LambdaExpression operand = ctx.pop();
        ctx.push(LambdaExpression.MathFunction.round(operand, decimalPlaces));
        return true;
    }

    /** Handles Integer.signum(int) and Long.signum(long) → MathFunction.sign(). */
    private boolean handleSignumMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
        if (!staticInsn.name.equals(METHOD_SIGNUM)) {
            return false;
        }
        if (!SIGNUM_OWNERS.contains(staticInsn.owner)) {
            return false;
        }
        if (ctx.getStack().isEmpty()) {
            return false;
        }
        LambdaExpression operand = ctx.pop();
        ctx.push(LambdaExpression.MathFunction.sign(operand));
        return true;
    }

    /** Maps Java Math method name to the corresponding unary MathOp. */
    private static LambdaExpression.MathFunction.MathOp mapUnaryMathOp(String methodName) {
        return switch (methodName) {
            case METHOD_ABS -> LambdaExpression.MathFunction.MathOp.ABS;
            case METHOD_SQRT -> LambdaExpression.MathFunction.MathOp.SQRT;
            case METHOD_CEIL -> LambdaExpression.MathFunction.MathOp.CEILING;
            case METHOD_FLOOR -> LambdaExpression.MathFunction.MathOp.FLOOR;
            case METHOD_EXP -> LambdaExpression.MathFunction.MathOp.EXP;
            case METHOD_LOG -> LambdaExpression.MathFunction.MathOp.LN;
            case METHOD_SIGNUM -> LambdaExpression.MathFunction.MathOp.SIGN;
            default -> throw new IllegalArgumentException("Unknown unary math method: " + methodName);
        };
    }

    /** Maps Java Math method name to the corresponding binary MathOp. */
    private static LambdaExpression.MathFunction.MathOp mapBinaryMathOp(String methodName) {
        return switch (methodName) {
            case METHOD_POW -> LambdaExpression.MathFunction.MathOp.POWER;
            default -> throw new IllegalArgumentException("Unknown binary math method: " + methodName);
        };
    }

    // ─── External Method Constant Folding ──────────────────────────────────

    /**
     * Classification of method arguments for constant folding.
     * ALL_CONSTANT: every argument is a Constant — can evaluate at build time.
     * HAS_CAPTURED: mix of Constants and CapturedVariables — runtime FoldedMethodCall.
     * NOT_FOLDABLE: contains entity fields or other expressions — cannot fold.
     */
    private enum Foldability {
        ALL_CONSTANT,
        HAS_CAPTURED,
        NOT_FOLDABLE
    }

    /** Classifies whether the given arguments are foldable. */
    private static Foldability classifyArguments(LambdaExpression[] args) {
        boolean allConstant = true;
        for (LambdaExpression arg : args) {
            switch (arg) {
                case LambdaExpression.Constant _ -> {
                    /* OK */ }
                case LambdaExpression.CapturedVariable _ -> allConstant = false;
                default -> {
                    return Foldability.NOT_FOLDABLE;
                }
            }
        }
        return allConstant ? Foldability.ALL_CONSTANT : Foldability.HAS_CAPTURED;
    }

    /** Re-pushes all arguments back onto the stack (used when folding is not possible). */
    private static void repushArguments(AnalysisContext ctx, LambdaExpression[] args) {
        for (LambdaExpression arg : args) {
            ctx.push(arg);
        }
    }

    /**
     * Handles unrecognized static methods by attempting constant folding.
     * If all arguments are constants or captured variables, the method can be
     * evaluated at build time (all constants) or at query execution time (captured vars).
     * If any argument is an entity field expression, the method call is silently skipped.
     */
    private void handleUnknownStaticMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
        int argCount = DescriptorParser.countMethodArguments(staticInsn.desc);
        if (argCount == 0 || ctx.getStackSize() < argCount) {
            return;
        }

        // Pop arguments from stack (reverse order)
        LambdaExpression[] args = new LambdaExpression[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = ctx.pop();
        }

        Foldability foldability = classifyArguments(args);
        if (foldability == Foldability.NOT_FOLDABLE) {
            repushArguments(ctx, args);
            return;
        }

        // Resolve the owner class
        Class<?> ownerClass = resolveOwnerClass(staticInsn.owner);
        if (ownerClass == null) {
            repushArguments(ctx, args);
            return;
        }

        // Verify the method actually exists on the owner class before proceeding
        Class<?>[] paramTypes = DescriptorParser.getParameterTypes(staticInsn.desc);
        if (!hasStaticMethod(ownerClass, staticInsn.name, paramTypes)) {
            repushArguments(ctx, args);
            return;
        }

        Class<?> returnType = DescriptorParser.getReturnType(staticInsn.desc);

        // Case 1: All constant — evaluate at build time
        if (foldability == Foldability.ALL_CONSTANT) {
            Object result = evaluateStaticMethodAtBuildTime(ownerClass, staticInsn.name, paramTypes, args);
            if (result != null) {
                ctx.push(new LambdaExpression.Constant(result, returnType));
                return;
            }
        }

        // Case 2: Has captured variables (or build-time evaluation failed) — create FoldedMethodCall
        ctx.push(new LambdaExpression.FoldedMethodCall(
                ownerClass,
                staticInsn.name,
                staticInsn.desc,
                List.of(args),
                returnType));
    }

    /** Returns true if the class has a public static method with the given name and parameter types. */
    private static boolean hasStaticMethod(Class<?> ownerClass, String methodName, Class<?>[] paramTypes) {
        try {
            java.lang.reflect.Method method = ownerClass.getMethod(methodName, paramTypes);
            return java.lang.reflect.Modifier.isStatic(method.getModifiers());
        } catch (NoSuchMethodException _) {
            return false;
        }
    }

    /** Resolves a JVM internal class name to a Class, or null if not available at build time. */
    @org.jspecify.annotations.Nullable
    private static Class<?> resolveOwnerClass(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException _) {
            return null;
        }
    }

    /**
     * Evaluates a static method at build time via reflection.
     * All arguments must be Constants (caller guarantees this via Foldability.ALL_CONSTANT check).
     * Returns null on any reflection failure.
     */
    @org.jspecify.annotations.Nullable
    private static Object evaluateStaticMethodAtBuildTime(
            Class<?> ownerClass, String methodName, Class<?>[] paramTypes, LambdaExpression[] args) {
        try {
            Object[] values = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                values[i] = ((LambdaExpression.Constant) args[i]).value();
            }
            java.lang.reflect.Method method = ownerClass.getMethod(methodName, paramTypes);
            return method.invoke(null, values);
        } catch (Exception e) {
            Log.debugf("Failed to evaluate static method %s.%s at build time: %s",
                    ownerClass.getSimpleName(), methodName, e.getMessage());
            return null;
        }
    }

    /** Auto-boxing valueOf owners — these are identity wrappers stripped during analysis. */
    private static final Set<String> BOXING_OWNERS = Set.of(
            "java/lang/Boolean", "java/lang/Integer", "java/lang/Long",
            "java/lang/Double", "java/lang/Float", "java/lang/Short",
            "java/lang/Byte", "java/lang/Character");

    /** Returns true if this INVOKESTATIC is an auto-boxing valueOf call. */
    private static boolean isBoxingCall(MethodInsnNode staticInsn) {
        return BOXING_OWNERS.contains(staticInsn.owner);
    }

    // Note: Subquery Methods have been extracted to SubqueryAnalyzer
}
