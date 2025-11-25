package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.InExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.MemberOfExpression;
import io.quarkus.qusaq.deployment.util.DescriptorParser;
import io.quarkus.qusaq.deployment.util.TypeConverter;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkus.qusaq.runtime.QusaqConstants.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles method invocations: INVOKEVIRTUAL (equals, String, compareTo, BigDecimal, temporal, getters),
 * INVOKESTATIC (Boolean.valueOf skip, temporal factory methods with constant folding),
 * INVOKESPECIAL (constructors including BigDecimal constant folding),
 * INVOKEINTERFACE (Collection.contains for IN and MEMBER OF expressions).
 */
public class MethodInvocationHandler implements InstructionHandler {

    private static final Logger log = Logger.getLogger(MethodInvocationHandler.class);

    /**
     * Collection interface types that support contains() for IN/MEMBER OF detection.
     */
    private static final Set<String> COLLECTION_INTERFACE_OWNERS = Set.of(
            "java/util/Collection",
            "java/util/List",
            "java/util/Set",
            "java/util/AbstractCollection",
            "java/util/AbstractList",
            "java/util/AbstractSet",
            "java/util/ArrayList",
            "java/util/LinkedList",
            "java/util/HashSet",
            "java/util/TreeSet",
            "java/util/LinkedHashSet"
    );

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

    /** Handles INVOKEVIRTUAL: equals, String, compareTo, BigDecimal, temporal, getters. */
    private void handleInvokeVirtual(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (isEqualsMethodCall(methodInsn)) {
            handleEqualsMethod(ctx);
            return;
        }

        if (methodInsn.owner.equals("java/lang/String")) {
            handleStringMethods(ctx, methodInsn);
            return;
        }

        if (isCompareToMethodCall(methodInsn)) {
            handleSingleArgumentMethodCall(ctx, METHOD_COMPARE_TO, int.class);
            return;
        }

        if (isBigDecimalArithmeticCall(methodInsn)) {
            handleBigDecimalMethods(ctx, methodInsn);
            return;
        }

        if (methodInsn.owner.startsWith("java/time/Local")) {
            handleTemporalMethods(ctx, methodInsn);
            return;
        }

        if (isGetterMethodCall(methodInsn)) {
            handleGetterMethod(ctx, methodInsn);
        }
    }

    /** Handles INVOKESTATIC: skips Boolean.valueOf, handles temporal factory methods with constant folding. */
    private void handleInvokeStatic(AnalysisContext ctx, MethodInsnNode staticInsn) {
        // Skip Boolean.valueOf (optimization - we work directly with boolean expressions)
        if (staticInsn.owner.equals("java/lang/Boolean") &&
            staticInsn.name.equals(METHOD_VALUE_OF) &&
            staticInsn.desc.equals("(Z)Ljava/lang/Boolean;")) {
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

            List<LambdaExpression> args = new ArrayList<>();
            for (int i = 0; i < argCount; i++) {
                if (!ctx.isStackEmpty()) {
                    args.add(0, ctx.pop());
                }
            }

            // Pop NEW and DUP markers
            if (!ctx.isStackEmpty()) ctx.pop();
            if (!ctx.isStackEmpty()) ctx.pop();

            // Special handling: BigDecimal(String) constructor with constant folding
            if (isBigDecimalStringConstruction(specialInsn, argCount, args)) {
                LambdaExpression.Constant constant = (LambdaExpression.Constant) args.get(0);
                String stringValue = (String) constant.value();
                handleBigDecimalConstantFolding(ctx, args, stringValue, specialInsn.owner);
            } else {
                pushConstructorCall(ctx, args, specialInsn.owner);
            }
        } catch (Exception e) {
            log.errorf(e, "Error processing INVOKESPECIAL %s for %s", CONSTRUCTOR, specialInsn.owner);
            throw e;
        }
    }

    // ========== INVOKEINTERFACE Handler (Iteration 5: Collections) ==========

    /**
     * Handles INVOKEINTERFACE: primarily Collection.contains() for IN and MEMBER OF expressions.
     * <p>
     * Detects two patterns:
     * <ul>
     *   <li><b>IN clause</b>: {@code collection.contains(p.field)} where collection is a captured variable
     *       → Creates {@link InExpression}</li>
     *   <li><b>MEMBER OF</b>: {@code p.collectionField.contains(value)} where collectionField is a mapped collection
     *       → Creates {@link MemberOfExpression}</li>
     * </ul>
     * <p>
     * Bytecode pattern for IN clause ({@code cities.contains(p.city)}):
     * <pre>
     * ALOAD 1              // Load captured cities collection (CapturedVariable)
     * ALOAD 0              // Load lambda parameter (Person)
     * GETFIELD Person.city // Get city field (FieldAccess)
     * INVOKEINTERFACE Collection.contains(Object)
     * </pre>
     * <p>
     * Bytecode pattern for MEMBER OF ({@code p.roles.contains("admin")}):
     * <pre>
     * ALOAD 0              // Load lambda parameter (Person)
     * GETFIELD Person.roles // Get roles collection field (FieldAccess)
     * LDC "admin"          // Load constant value
     * INVOKEINTERFACE Collection.contains(Object)
     * </pre>
     */
    private void handleInvokeInterface(AnalysisContext ctx, MethodInsnNode interfaceInsn) {
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

    /**
     * Checks if the instruction is a Collection.contains() call.
     */
    private boolean isCollectionContainsCall(MethodInsnNode methodInsn) {
        return methodInsn.name.equals(METHOD_CONTAINS) &&
               methodInsn.desc.equals("(Ljava/lang/Object;)Z") &&
               COLLECTION_INTERFACE_OWNERS.contains(methodInsn.owner);
    }

    /**
     * Handles Collection.contains() by determining whether it's an IN clause or MEMBER OF pattern.
     * <p>
     * The key distinction is based on the target (collection) expression:
     * <ul>
     *   <li><b>IN clause</b>: target is CapturedVariable (collection from outer scope)
     *       → Field is in collection, creates {@code InExpression(field, collection, false)}</li>
     *   <li><b>MEMBER OF</b>: target is FieldAccess or PathExpression (collection field on entity)
     *       → Value is in collection field, creates {@code MemberOfExpression(value, collectionField, false)}</li>
     * </ul>
     */
    private void handleCollectionContains(AnalysisContext ctx) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression argument = ctx.pop();  // The contains() argument
        LambdaExpression target = ctx.pop();    // The collection (target of contains())

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
     * Determines if the contains() call represents an IN clause pattern.
     * <p>
     * IN clause pattern: captured collection.contains(entity field)
     * Example: {@code cities.contains(p.city)}
     * - target (collection) is CapturedVariable
     * - argument (field) is FieldAccess or PathExpression
     *
     * @param target The collection (left side of contains)
     * @param argument The value being checked (argument to contains)
     * @return true if this is an IN clause pattern
     */
    private boolean isInClausePattern(LambdaExpression target, LambdaExpression argument) {
        // Target must be a captured variable (the collection from outer scope)
        boolean targetIsCaptured = target instanceof LambdaExpression.CapturedVariable;

        // Argument must be a field access or path expression (entity field)
        boolean argumentIsEntityField = argument instanceof LambdaExpression.FieldAccess ||
                                         argument instanceof LambdaExpression.PathExpression;

        return targetIsCaptured && argumentIsEntityField;
    }

    /**
     * Determines if the contains() call represents a MEMBER OF pattern.
     * <p>
     * MEMBER OF pattern: entity collection field.contains(value)
     * Example: {@code p.roles.contains("admin")}
     * - target (collection field) is FieldAccess or PathExpression
     * - argument (value) is Constant or CapturedVariable
     *
     * @param target The collection field (left side of contains)
     * @param argument The value being checked (argument to contains)
     * @return true if this is a MEMBER OF pattern
     */
    private boolean isMemberOfPattern(LambdaExpression target, LambdaExpression argument) {
        // Target must be a field access or path expression (collection field on entity)
        boolean targetIsEntityField = target instanceof LambdaExpression.FieldAccess ||
                                       target instanceof LambdaExpression.PathExpression;

        // Argument must be a constant or captured variable (the value to check)
        boolean argumentIsValue = argument instanceof LambdaExpression.Constant ||
                                  argument instanceof LambdaExpression.CapturedVariable;

        return targetIsEntityField && argumentIsValue;
    }

    // ========== INVOKEVIRTUAL Helper Methods ==========

    /**
     * Checks if instruction is an equals() method call.
     */
    private boolean isEqualsMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.name.equals(METHOD_EQUALS) && methodInsn.desc.equals("(Ljava/lang/Object;)Z");
    }

    /**
     * Handles equals() method call by converting to equality comparison.
     */
    private void handleEqualsMethod(AnalysisContext ctx) {
        if (ctx.getStackSize() >= 2) {
            LambdaExpression right = ctx.pop();
            LambdaExpression left = ctx.pop();
            ctx.push(new LambdaExpression.BinaryOp(left, EQ, right));
        }
    }

    /**
     * Checks if instruction is a compareTo() method call.
     */
    private boolean isCompareToMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.name.equals(METHOD_COMPARE_TO) && methodInsn.desc.endsWith(")I");
    }

    /**
     * Checks if instruction is a BigDecimal arithmetic method call.
     */
    private boolean isBigDecimalArithmeticCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals("java/math/BigDecimal") &&
               methodInsn.desc.equals("(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
    }

    /**
     * Checks if instruction is a getter method call (getXxx or isXxx).
     */
    private boolean isGetterMethodCall(MethodInsnNode methodInsn) {
        return (methodInsn.name.startsWith("get") || methodInsn.name.startsWith("is")) &&
               methodInsn.desc.startsWith("()");
    }

    /**
     * Handles String method calls (startsWith, endsWith, contains, length, etc.).
     */
    private void handleStringMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_STARTS_WITH, METHOD_ENDS_WITH ->
                handleSingleArgumentStringMethod(ctx, methodInsn, "(Ljava/lang/String;)Z", boolean.class);

            case METHOD_CONTAINS ->
                handleSingleArgumentStringMethod(ctx, methodInsn, "(Ljava/lang/CharSequence;)Z", boolean.class);

            case METHOD_LENGTH ->
                handleNoArgumentStringMethod(ctx, methodInsn, "()I", int.class);

            case METHOD_IS_EMPTY ->
                handleNoArgumentStringMethod(ctx, methodInsn, "()Z", boolean.class);

            case METHOD_TO_LOWER_CASE, METHOD_TO_UPPER_CASE, METHOD_TRIM ->
                handleNoArgumentStringMethod(ctx, methodInsn, "()Ljava/lang/String;", String.class);

            case METHOD_SUBSTRING ->
                handleSubstringMethod(ctx, methodInsn);

            default -> { /* No action for unrecognized String methods */ }
        }
    }

    /**
     * Handles String methods with a single argument.
     */
    private void handleSingleArgumentStringMethod(AnalysisContext ctx, MethodInsnNode methodInsn,
                                                   String expectedDescriptor, Class<?> returnType) {
        if (methodInsn.desc.equals(expectedDescriptor)) {
            handleSingleArgumentMethodCall(ctx, methodInsn.name, returnType);
        }
    }

    /**
     * Handles String methods with no arguments.
     */
    private void handleNoArgumentStringMethod(AnalysisContext ctx, MethodInsnNode methodInsn,
                                              String expectedDescriptor, Class<?> returnType) {
        if (methodInsn.desc.equals(expectedDescriptor)) {
            handleNoArgumentMethodCall(ctx, methodInsn.name, returnType);
        }
    }

    /**
     * Handles String.substring method (supports both single and two-argument variants).
     */
    private void handleSubstringMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (methodInsn.desc.equals("(I)Ljava/lang/String;") ||
            methodInsn.desc.equals("(II)Ljava/lang/String;")) {
            int argCount = methodInsn.desc.equals("(I)Ljava/lang/String;") ? 1 : 2;
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

    /**
     * Handles BigDecimal method calls (add, subtract, multiply, divide).
     */
    private void handleBigDecimalMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_ADD, METHOD_SUBTRACT, METHOD_MULTIPLY, METHOD_DIVIDE ->
                handleSingleArgumentMethodCall(ctx, methodInsn.name, BigDecimal.class);
            default -> { /* No action for unrecognized BigDecimal methods */ }
        }
    }

    /**
     * Handles temporal (java.time) method calls.
     */
    private void handleTemporalMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodInsn.name)) {
            handleSingleArgumentMethodCall(ctx, methodInsn.name, boolean.class);
        } else {
            switch (methodInsn.owner) {
                case "java/time/LocalDate" -> handleLocalDateMethods(ctx, methodInsn);
                case "java/time/LocalDateTime" -> handleLocalDateTimeMethods(ctx, methodInsn);
                case "java/time/LocalTime" -> handleLocalTimeMethods(ctx, methodInsn);
                default -> { /* No action for unrecognized temporal types */ }
            }
        }
    }

    /**
     * Handles LocalDate accessor methods (getYear, getMonthValue, getDayOfMonth).
     */
    private void handleLocalDateMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, METHOD_GET_DAY_OF_MONTH ->
                handleTemporalAccessorMethod(ctx, methodInsn, "java/time/LocalDate",
                    METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, METHOD_GET_DAY_OF_MONTH);
            default -> { /* No action for unrecognized LocalDate methods */ }
        }
    }

    /**
     * Handles LocalDateTime accessor methods (getYear, getMonthValue, getDayOfMonth, getHour, getMinute, getSecond).
     */
    private void handleLocalDateTimeMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, METHOD_GET_DAY_OF_MONTH,
                 METHOD_GET_HOUR, METHOD_GET_MINUTE, METHOD_GET_SECOND ->
                handleTemporalAccessorMethod(ctx, methodInsn, "java/time/LocalDateTime",
                    METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, METHOD_GET_DAY_OF_MONTH,
                    METHOD_GET_HOUR, METHOD_GET_MINUTE, METHOD_GET_SECOND);
            default -> { /* No action for unrecognized LocalDateTime methods */ }
        }
    }

    /**
     * Handles LocalTime accessor methods (getHour, getMinute, getSecond).
     */
    private void handleLocalTimeMethods(AnalysisContext ctx, MethodInsnNode methodInsn) {
        switch (methodInsn.name) {
            case METHOD_GET_HOUR, METHOD_GET_MINUTE, METHOD_GET_SECOND ->
                handleTemporalAccessorMethod(ctx, methodInsn, "java/time/LocalTime",
                    METHOD_GET_HOUR, METHOD_GET_MINUTE, METHOD_GET_SECOND);
            default -> { /* No action for unrecognized LocalTime methods */ }
        }
    }

    /**
     * Handles temporal accessor methods (getYear, getMonthValue, etc.).
     */
    private void handleTemporalAccessorMethod(AnalysisContext ctx, MethodInsnNode methodInsn,
                                              String ownerType, String... validMethods) {
        if (!methodInsn.owner.equals(ownerType)) {
            return;
        }

        for (String validMethod : validMethods) {
            if (methodInsn.name.equals(validMethod)) {
                if (!ctx.isStackEmpty()) {
                    LambdaExpression target = ctx.pop();
                    ctx.push(new LambdaExpression.MethodCall(
                        target,
                        methodInsn.name,
                        List.of(),
                        int.class
                    ));
                }
                return;
            }
        }
    }

    /**
     * Handles getter method calls (getXxx, isXxx) and converts to field access.
     */
    private void handleGetterMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (!ctx.isStackEmpty()) {
            ctx.pop();
        }

        String fieldName = extractFieldName(methodInsn.name);
        String returnTypeDesc = methodInsn.desc.substring(2);
        Class<?> returnType = TypeConverter.descriptorToClass(returnTypeDesc);
        ctx.push(new LambdaExpression.FieldAccess(fieldName, returnType));
    }

    /**
     * Extracts field name from getter (getAge -> age, isActive -> active).
     */
    private String extractFieldName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String fieldName = methodName.substring(3);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            String fieldName = methodName.substring(2);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return methodName;
    }

    /**
     * Handles zero-argument method calls (e.g., length(), isEmpty()).
     */
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

    /**
     * Handles single-argument method calls (e.g., startsWith(), compareTo()).
     */
    private void handleSingleArgumentMethodCall(AnalysisContext ctx, String methodName, Class<?> returnType) {
        if (ctx.getStackSize() >= 2) {
            LambdaExpression argument = ctx.pop();  // The method argument
            LambdaExpression target = ctx.pop();    // The object calling the method
            ctx.push(new LambdaExpression.MethodCall(
                target,
                methodName,
                List.of(argument),
                returnType
            ));
        }
    }

    // ========== INVOKESTATIC Helper Methods ==========

    /**
     * Handles temporal factory methods with constant folding optimization.
     *
     * <p>If all arguments are constants, the temporal value is created at analysis time.
     * Otherwise, a MethodCall is created for runtime evaluation.
     *
     * @param ctx the analysis context
     * @param staticInsn the static method instruction
     * @param temporalClass the temporal class (LocalDate, LocalDateTime, LocalTime)
     * @param expectedArgCount the expected number of arguments
     * @param evaluator function to create the temporal value from constant arguments
     * @param <T> the temporal type
     */
    private <T> void handleTemporalFactoryMethod(
            AnalysisContext ctx,
            MethodInsnNode staticInsn,
            Class<T> temporalClass,
            int expectedArgCount,
            java.util.function.Function<int[], T> evaluator) {

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
                log.debugf("Failed to evaluate constant temporal value, will create method call instead: %s",
                           e.getMessage());
            }
        }

        // Fallback: create method call
        ctx.push(new LambdaExpression.MethodCall(null, METHOD_OF, List.of(args), temporalClass));
    }

    // ========== INVOKESPECIAL Helper Methods ==========

    /**
     * Checks if instruction is a BigDecimal string constructor call.
     */
    private boolean isBigDecimalStringConstruction(MethodInsnNode specialInsn, int argCount,
                                                    List<LambdaExpression> args) {
        return specialInsn.owner.equals("java/math/BigDecimal") &&
               argCount == 1 &&
               !args.isEmpty() &&
               args.get(0) instanceof LambdaExpression.Constant constant &&
               constant.value() instanceof String;
    }

    /**
     * Attempts to fold BigDecimal string construction into a constant at build time.
     * Falls back to constructor call if the string is not a valid number.
     */
    private void handleBigDecimalConstantFolding(AnalysisContext ctx, List<LambdaExpression> args,
                                                  String stringValue, String owner) {
        try {
            BigDecimal value = new BigDecimal(stringValue);
            ctx.push(new LambdaExpression.Constant(value, BigDecimal.class));
        } catch (NumberFormatException e) {
            pushConstructorCall(ctx, args, owner);
        }
    }

    /**
     * Pushes a constructor call expression onto the stack.
     * Creates a ConstructorCall for DTO projections.
     */
    private void pushConstructorCall(AnalysisContext ctx, List<LambdaExpression> args, String owner) {
        Class<?> constructedType = TypeConverter.descriptorToClass("L" + owner + ";");
        ctx.push(new LambdaExpression.ConstructorCall(owner, args, constructedType));
    }
}
