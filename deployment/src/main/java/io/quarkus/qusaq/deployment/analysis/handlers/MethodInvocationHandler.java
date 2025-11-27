package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.ExistsSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.GroupAggregation;
import io.quarkus.qusaq.deployment.LambdaExpression.GroupKeyReference;
import io.quarkus.qusaq.deployment.LambdaExpression.GroupParameter;
import io.quarkus.qusaq.deployment.LambdaExpression.InExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.InSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.MemberOfExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.ScalarSubquery;
import io.quarkus.qusaq.deployment.LambdaExpression.SubqueryAggregationType;
import io.quarkus.qusaq.deployment.LambdaExpression.SubqueryBuilderReference;
import org.objectweb.asm.Type;
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

    /** Handles INVOKEVIRTUAL: equals, String, compareTo, BigDecimal, temporal, SubqueryBuilder, getters. */
    private void handleInvokeVirtual(AnalysisContext ctx, MethodInsnNode methodInsn) {
        if (isEqualsMethodCall(methodInsn)) {
            handleEqualsMethod(ctx);
            return;
        }

        // Iteration 8: Handle SubqueryBuilder.* method calls
        if (isSubqueryBuilderMethodCall(methodInsn)) {
            handleSubqueryBuilderMethod(ctx, methodInsn);
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

    /** Handles INVOKESTATIC: skips Boolean.valueOf, handles temporal factory methods with constant folding,
     *  and handles Subqueries.subquery() factory method for subquery builder pattern. */
    private void handleInvokeStatic(AnalysisContext ctx, MethodInsnNode staticInsn) {
        // Skip Boolean.valueOf (optimization - we work directly with boolean expressions)
        if (staticInsn.owner.equals("java/lang/Boolean") &&
            staticInsn.name.equals(METHOD_VALUE_OF) &&
            staticInsn.desc.equals("(Z)Ljava/lang/Boolean;")) {
            return;
        }

        // Iteration 8: Handle Subqueries.subquery(Class) factory method
        if (isSubqueriesMethodCall(staticInsn)) {
            handleSubqueriesFactoryMethod(ctx, staticInsn);
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

    // ========== INVOKEINTERFACE Handler (Iteration 5: Collections, Iteration 7: Group) ==========

    /**
     * Handles INVOKEINTERFACE: Collection.contains() for IN/MEMBER OF, and Group methods.
     * <p>
     * Detects multiple patterns:
     * <ul>
     *   <li><b>IN clause</b>: {@code collection.contains(p.field)} where collection is a captured variable
     *       → Creates {@link InExpression}</li>
     *   <li><b>MEMBER OF</b>: {@code p.collectionField.contains(value)} where collectionField is a mapped collection
     *       → Creates {@link MemberOfExpression}</li>
     *   <li><b>Group.key()</b>: {@code g.key()} in group context
     *       → Creates {@link GroupKeyReference}</li>
     *   <li><b>Group.count()</b>: {@code g.count()} in group context
     *       → Creates {@link GroupAggregation} with COUNT type</li>
     *   <li><b>Group aggregations</b>: {@code g.avg(...)}, {@code g.min(...)}, etc.
     *       → Creates {@link GroupAggregation} with appropriate type</li>
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
        // Iteration 7: Check if this is a Group interface method call
        if (isGroupMethodCall(interfaceInsn)) {
            handleGroupMethod(ctx, interfaceInsn);
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

    // ========== Group Interface Methods (Iteration 7: GROUP BY) ==========

    /**
     * Checks if the instruction is a Group interface method call.
     */
    private boolean isGroupMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(GROUP_INTERNAL_NAME);
    }

    /**
     * Handles Group interface method calls for GROUP BY queries.
     * <p>
     * Supported methods:
     * <ul>
     *   <li>{@code g.key()} → GroupKeyReference</li>
     *   <li>{@code g.count()} → GroupAggregation(COUNT)</li>
     *   <li>{@code g.countDistinct(field)} → GroupAggregation(COUNT_DISTINCT, field)</li>
     *   <li>{@code g.avg(field)} → GroupAggregation(AVG, field)</li>
     *   <li>{@code g.min(field)} → GroupAggregation(MIN, field)</li>
     *   <li>{@code g.max(field)} → GroupAggregation(MAX, field)</li>
     *   <li>{@code g.sumInteger(field)} → GroupAggregation(SUM_INTEGER, field)</li>
     *   <li>{@code g.sumLong(field)} → GroupAggregation(SUM_LONG, field)</li>
     *   <li>{@code g.sumDouble(field)} → GroupAggregation(SUM_DOUBLE, field)</li>
     * </ul>
     */
    private void handleGroupMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
        String methodName = methodInsn.name;

        switch (methodName) {
            case METHOD_KEY -> handleGroupKey(ctx);
            case METHOD_COUNT -> handleGroupCount(ctx);
            case METHOD_COUNT_DISTINCT -> handleGroupCountDistinct(ctx);
            case METHOD_AVG -> handleGroupAggregationWithField(ctx, GroupAggregation::avg);
            case METHOD_MIN -> handleGroupMinMax(ctx, true);
            case METHOD_MAX -> handleGroupMinMax(ctx, false);
            case METHOD_SUM_INTEGER -> handleGroupAggregationWithField(ctx, GroupAggregation::sumInteger);
            case METHOD_SUM_LONG -> handleGroupAggregationWithField(ctx, GroupAggregation::sumLong);
            case METHOD_SUM_DOUBLE -> handleGroupAggregationWithField(ctx, GroupAggregation::sumDouble);
            default -> log.debugf("Unhandled Group method: %s", methodName);
        }
    }

    /**
     * Handles g.key() - returns the grouping key.
     */
    private void handleGroupKey(AnalysisContext ctx) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        if (target instanceof GroupParameter) {
            // For now, we create a placeholder GroupKeyReference
            // The actual key expression will be resolved at code generation time
            ctx.push(new GroupKeyReference(null, Object.class));
        } else {
            log.warnf("Unexpected target for g.key(): %s", target);
        }
    }

    /**
     * Handles g.count() - counts entities in the group.
     */
    private void handleGroupCount(AnalysisContext ctx) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression target = ctx.pop();
        if (target instanceof GroupParameter) {
            ctx.push(GroupAggregation.count());
        } else {
            log.warnf("Unexpected target for g.count(): %s", target);
        }
    }

    /**
     * Handles g.countDistinct(field) - counts distinct values.
     */
    private void handleGroupCountDistinct(AnalysisContext ctx) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression fieldArg = ctx.pop();  // The field extractor (analyzed nested lambda)
        LambdaExpression target = ctx.pop();     // The Group parameter

        if (target instanceof GroupParameter) {
            ctx.push(GroupAggregation.countDistinct(fieldArg));
        } else {
            log.warnf("Unexpected target for g.countDistinct(): %s", target);
        }
    }

    /**
     * Handles g.avg/sumInteger/sumLong/sumDouble(field) - aggregations that return fixed types.
     */
    private void handleGroupAggregationWithField(
            AnalysisContext ctx,
            java.util.function.Function<LambdaExpression, GroupAggregation> aggregationFactory) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression fieldArg = ctx.pop();  // The field extractor
        LambdaExpression target = ctx.pop();     // The Group parameter

        if (target instanceof GroupParameter) {
            ctx.push(aggregationFactory.apply(fieldArg));
        } else {
            log.warnf("Unexpected target for group aggregation: %s", target);
        }
    }

    /**
     * Handles g.min(field) and g.max(field) - aggregations that preserve field type.
     */
    private void handleGroupMinMax(AnalysisContext ctx, boolean isMin) {
        if (ctx.getStackSize() < 2) {
            return;
        }

        LambdaExpression fieldArg = ctx.pop();  // The field extractor
        LambdaExpression target = ctx.pop();     // The Group parameter

        if (target instanceof GroupParameter) {
            // Determine result type from field expression
            Class<?> resultType = inferFieldType(fieldArg);
            if (isMin) {
                ctx.push(GroupAggregation.min(fieldArg, resultType));
            } else {
                ctx.push(GroupAggregation.max(fieldArg, resultType));
            }
        } else {
            log.warnf("Unexpected target for g.%s(): %s", isMin ? "min" : "max", target);
        }
    }

    /**
     * Infers the result type from a field expression.
     */
    private Class<?> inferFieldType(LambdaExpression fieldExpr) {
        if (fieldExpr instanceof LambdaExpression.FieldAccess field) {
            return field.fieldType();
        } else if (fieldExpr instanceof LambdaExpression.PathExpression path) {
            return path.resultType();
        }
        return Object.class;
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
     * - argument (field) is FieldAccess, PathExpression, BiEntityFieldAccess, or BiEntityPathExpression
     *
     * @param target The collection (left side of contains)
     * @param argument The value being checked (argument to contains)
     * @return true if this is an IN clause pattern
     */
    private boolean isInClausePattern(LambdaExpression target, LambdaExpression argument) {
        // Target must be a captured variable (the collection from outer scope)
        boolean targetIsCaptured = target instanceof LambdaExpression.CapturedVariable;

        // Argument must be a field access or path expression (entity field)
        // Supports both single-entity and bi-entity expressions
        boolean argumentIsEntityField = argument instanceof LambdaExpression.FieldAccess ||
                                         argument instanceof LambdaExpression.PathExpression ||
                                         argument instanceof LambdaExpression.BiEntityFieldAccess ||
                                         argument instanceof LambdaExpression.BiEntityPathExpression;

        return targetIsCaptured && argumentIsEntityField;
    }

    /**
     * Determines if the contains() call represents a MEMBER OF pattern.
     * <p>
     * MEMBER OF pattern: entity collection field.contains(value)
     * Example: {@code p.roles.contains("admin")}
     * - target (collection field) is FieldAccess, PathExpression, BiEntityFieldAccess, or BiEntityPathExpression
     * - argument (value) is Constant or CapturedVariable
     *
     * @param target The collection field (left side of contains)
     * @param argument The value being checked (argument to contains)
     * @return true if this is a MEMBER OF pattern
     */
    private boolean isMemberOfPattern(LambdaExpression target, LambdaExpression argument) {
        // Target must be a field access or path expression (collection field on entity)
        // Supports both single-entity and bi-entity expressions
        boolean targetIsEntityField = target instanceof LambdaExpression.FieldAccess ||
                                       target instanceof LambdaExpression.PathExpression ||
                                       target instanceof LambdaExpression.BiEntityFieldAccess ||
                                       target instanceof LambdaExpression.BiEntityPathExpression;

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
     * <p>
     * In bi-entity mode, produces BiEntityFieldAccess when called on a BiEntityParameter.
     */
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

    // ========== Subqueries Methods (Iteration 8: Subqueries) ==========

    /**
     * Checks if the instruction is a Subqueries.* static method call.
     */
    private boolean isSubqueriesMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(SUBQUERIES_INTERNAL_NAME);
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
     */
    private void handleSubqueriesFactoryMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
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
     * Checks if the instruction is a SubqueryBuilder.* instance method call.
     */
    private boolean isSubqueryBuilderMethodCall(MethodInsnNode methodInsn) {
        return methodInsn.owner.equals(SUBQUERY_BUILDER_INTERNAL_NAME);
    }

    /**
     * Handles SubqueryBuilder.* method calls for subquery expressions.
     * <p>
     * Method mappings: avg/sum/min/max → ScalarSubquery, count → ScalarSubquery(COUNT),
     * exists/notExists → ExistsSubquery, in/notIn → InSubquery.
     */
    private void handleSubqueryBuilderMethod(AnalysisContext ctx, MethodInsnNode methodInsn) {
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
        LambdaExpression finalPredicate;
        if (builderPredicate != null && argPredicate != null) {
            finalPredicate = new LambdaExpression.BinaryOp(builderPredicate, LambdaExpression.BinaryOp.Operator.AND, argPredicate);
        } else if (builderPredicate != null) {
            finalPredicate = builderPredicate;
        } else {
            finalPredicate = argPredicate;
        }

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
        LambdaExpression finalPredicate;
        if (builderPredicate != null && argPredicate != null) {
            finalPredicate = new LambdaExpression.BinaryOp(builderPredicate, LambdaExpression.BinaryOp.Operator.AND, argPredicate);
        } else if (builderPredicate != null) {
            finalPredicate = builderPredicate;
        } else {
            finalPredicate = argPredicate;
        }

        ctx.push(new InSubquery(field, entityClass, entityClassName, selector, finalPredicate, negated));
    }

    /**
     * Holds entity class information including both the Class object and optional class name.
     * The className is only set when the class cannot be loaded at build-time.
     */
    private record EntityClassInfo(Class<?> clazz, String className) {}

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
                // Fallback to the handler's class loader
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
