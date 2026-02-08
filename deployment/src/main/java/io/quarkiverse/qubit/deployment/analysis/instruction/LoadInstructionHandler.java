package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupParameter;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType;
import io.quarkiverse.qubit.Group;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkiverse.qubit.deployment.util.TypeConverter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles load instructions: ALOAD, primitives, GETFIELD.
 * Supports relationship navigation and bi-entity lambdas for join queries.
 */
public enum LoadInstructionHandler implements InstructionHandler {
    INSTANCE;

    /** Opcodes handled by this handler for O(1) dispatch. */
    private static final Set<Integer> SUPPORTED_OPCODES = Set.of(
            ALOAD, ILOAD, LLOAD, FLOAD, DLOAD, GETFIELD, GETSTATIC
    );

    @Override
    public Set<Integer> supportedOpcodes() {
        return SUPPORTED_OPCODES;
    }

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        return SUPPORTED_OPCODES.contains(insn.getOpcode());
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        switch (opcode) {
            case ALOAD -> handleALoad(ctx, (VarInsnNode) insn);
            case ILOAD, LLOAD, FLOAD, DLOAD -> handlePrimitiveLoad(ctx, opcode, (VarInsnNode) insn);
            case GETFIELD -> handleGetField(ctx, (FieldInsnNode) insn);
            case GETSTATIC -> handleGetStatic(ctx, (FieldInsnNode) insn);
        }

        return false;
    }

    /** Handles ALOAD: entity parameter, group parameter, or captured variable. */
    private void handleALoad(AnalysisContext ctx, VarInsnNode varInsn) {
        EntityPosition entityPosition = ctx.getEntityPosition(varInsn.var);

        if (entityPosition != null) {
            if (ctx.isGroupContextMode()) {
                ctx.push(new GroupParameter("group", Group.class, varInsn.var, Object.class, Object.class));
            } else if (ctx.isBiEntityMode()) {
                String paramName = entityPosition == EntityPosition.FIRST ? "entity" : "joinedEntity";
                ctx.push(new BiEntityParameter(paramName, Object.class, varInsn.var, entityPosition));
            } else {
                ctx.push(new LambdaExpression.Parameter("entity", Object.class, varInsn.var));
            }
        } else {
            int paramIndex = DescriptorParser.slotIndexToParameterIndex(
                    ctx.getMethod().desc, varInsn.var);

            // Negative paramIndex means the slot is not a method parameter (e.g., lambda local variable)
            if (paramIndex < 0) {
                throw new BytecodeAnalysisException(
                        String.format("ALOAD slot %d does not correspond to a method parameter in descriptor '%s'. " +
                                "Lambda local variables are not supported - use simple expressions without intermediate variables.",
                                varInsn.var, ctx.getMethod().desc));
            }

            Class<?> varType = DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex);
            String varName = ctx.getVariableNameForSlot(varInsn.var);
            ctx.push(new LambdaExpression.CapturedVariable(paramIndex, varType, varName));
        }
    }

    /** Handles primitive loads. */
    private void handlePrimitiveLoad(AnalysisContext ctx, int opcode, VarInsnNode varInsn) {
        Class<?> primitiveType = switch (opcode) {
            case ILOAD -> int.class;
            case LLOAD -> long.class;
            case FLOAD -> float.class;
            case DLOAD -> double.class;
            default -> throw BytecodeAnalysisException.unexpectedOpcode("primitive load", opcode);
        };

        int paramIndex = DescriptorParser.slotIndexToParameterIndex(
                ctx.getMethod().desc, varInsn.var);

        // Negative paramIndex means the slot is not a method parameter (e.g., lambda local variable)
        if (paramIndex < 0) {
            throw new BytecodeAnalysisException(
                    String.format("Primitive load at slot %d does not correspond to a method parameter in descriptor '%s'. " +
                            "Lambda local variables are not supported - use simple expressions without intermediate variables.",
                            varInsn.var, ctx.getMethod().desc));
        }

        Class<?> actualType = primitiveType;
        if (primitiveType == int.class) {
            actualType = DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex);
        }

        String varName = ctx.getVariableNameForSlot(varInsn.var);
        ctx.push(new LambdaExpression.CapturedVariable(paramIndex, actualType, varName));
    }

    /**
     * Handles GETSTATIC: loads static field values like enum constants.
     * <p>
     * Common patterns:
     * <ul>
     *   <li>Enum comparison: {@code o.status == OrderStatus.DELIVERED}</li>
     *   <li>Static constants: {@code Product.MAX_PRICE}</li>
     * </ul>
     * <p>
     * The value is wrapped in a Constant expression for JPQL generation.
     * Enum values are represented as the enum name for proper JPQL literal conversion.
     */
    private void handleGetStatic(AnalysisContext ctx, FieldInsnNode fieldInsn) {
        Class<?> fieldType = TypeConverter.descriptorToClass(fieldInsn.desc);
        String fieldName = fieldInsn.name;

        // For enum constants, create a Constant with the enum value name
        // For other static fields, create a constant placeholder
        ctx.push(new LambdaExpression.Constant(fieldName, fieldType));
    }

    /**
     * Handles GETFIELD: converts to FieldAccess or PathExpression node.
     * <p>
     * Detects chained field access patterns and builds PathExpression AST nodes
     * for relationship navigation.
     * <p>
     * Produces BiEntityFieldAccess and BiEntityPathExpression nodes in bi-entity
     * mode (join queries) to track entity position.
     * <p>
     * Bytecode patterns:
     * <pre>
     * // Single field access: p.age
     * ALOAD 0              // Stack: [Parameter]
     * GETFIELD Person.age  // Stack: [FieldAccess(age)]
     *
     * // Chained access: p.owner.firstName
     * ALOAD 0              // Stack: [Parameter]
     * GETFIELD Phone.owner // Stack: [FieldAccess(owner)]  <- intermediate
     * GETFIELD Person.firstName // Stack: [PathExpression([owner, firstName])]
     *
     * // Bi-entity (join): (Person p, Phone ph) -> ph.type
     * ALOAD 1              // Stack: [BiEntityParameter(SECOND)]
     * GETFIELD Phone.type  // Stack: [BiEntityFieldAccess(type, SECOND)]
     * </pre>
     */
    private void handleGetField(AnalysisContext ctx, FieldInsnNode fieldInsn) {
        LambdaExpression target = ctx.isStackEmpty() ? null : ctx.pop();
        Class<?> fieldType = TypeConverter.descriptorToClass(fieldInsn.desc);
        String fieldName = fieldInsn.name;

        // RelationType.FIELD is default; resolved during JPA generation
        PathSegment newSegment = new PathSegment(fieldName, fieldType, RelationType.FIELD);

        switch (target) {
            // ========== Bi-entity mode (join queries) ==========
            case BiEntityParameter(_, _, _, var position) ->
                // First-level field access from bi-entity parameter: ph.type
                ctx.push(new BiEntityFieldAccess(fieldName, fieldType, position));

            case BiEntityFieldAccess biField ->
                // Second-level: ph.owner.firstName → BiEntityPathExpression
                ctx.push(new BiEntityPathExpression(
                        buildPath(toSegment(biField), newSegment),
                        fieldType,
                        biField.entityPosition()));

            case BiEntityPathExpression(var segments, _, var entityPosition) ->
                // Third+ level: ph.owner.department.name
                ctx.push(new BiEntityPathExpression(
                        extendPath(segments, newSegment),
                        fieldType,
                        entityPosition));

            // ========== Single-entity mode ==========
            case LambdaExpression.Parameter _ ->
                // First-level field access from entity parameter: p.age
                ctx.push(new FieldAccess(fieldName, fieldType));

            case FieldAccess previousField ->
                // Second-level: p.owner.firstName → PathExpression
                ctx.push(new PathExpression(
                        buildPath(toSegment(previousField), newSegment),
                        fieldType));

            case PathExpression(var segments, _) ->
                // Third+ level: p.owner.department.name
                ctx.push(new PathExpression(
                        extendPath(segments, newSegment),
                        fieldType));

            // ========== Subquery correlated variables ==========
            case LambdaExpression.CapturedVariable(var index, var type, _) ->
                // Field access on captured variable → CorrelatedVariable for subquery correlation
                ctx.push(new LambdaExpression.CorrelatedVariable(
                        new FieldAccess(fieldName, fieldType),
                        index,
                        type));

            case LambdaExpression.CorrelatedVariable(var fieldExpression, var outerParameterIndex, var outerEntityType) -> {
                // Chained field access on correlated variable → extend path (e.g., p.owner.id)
                LambdaExpression extendedPath = extendCorrelatedPath(fieldExpression, newSegment, fieldType);
                ctx.push(new LambdaExpression.CorrelatedVariable(
                        extendedPath,
                        outerParameterIndex,
                        outerEntityType));
            }

            default ->
                // Fallback: create simple FieldAccess
                // This handles edge cases like field access on method return values
                ctx.push(new FieldAccess(fieldName, fieldType));
        }
    }

    // ==================== Path Building Helpers ====================

    /** Converts a FieldAccess to a PathSegment. */
    private static PathSegment toSegment(FieldAccess field) {
        return new PathSegment(field.fieldName(), field.fieldType(), RelationType.FIELD);
    }

    /** Converts a BiEntityFieldAccess to a PathSegment. */
    private static PathSegment toSegment(BiEntityFieldAccess field) {
        return new PathSegment(field.fieldName(), field.fieldType(), RelationType.FIELD);
    }

    /** Builds a 2-segment path from first and second segments. */
    private static List<PathSegment> buildPath(PathSegment first, PathSegment second) {
        return List.of(first, second);
    }

    /** Extends an existing path with a new segment. */
    private static List<PathSegment> extendPath(List<PathSegment> existing, PathSegment newSegment) {
        List<PathSegment> segments = new ArrayList<>(existing.size() + 1);
        segments.addAll(existing);
        segments.add(newSegment);
        return segments;
    }

    /** Extends a correlated variable's inner field expression with a new segment. */
    private static LambdaExpression extendCorrelatedPath(
            LambdaExpression innerField, PathSegment newSegment, Class<?> resultType) {
        return switch (innerField) {
            case FieldAccess prevField ->
                new PathExpression(buildPath(toSegment(prevField), newSegment), resultType);
            case PathExpression(var segments, _) ->
                new PathExpression(extendPath(segments, newSegment), resultType);
            default ->
                // Fallback: wrap in single-segment PathExpression
                new PathExpression(List.of(newSegment), resultType);
        };
    }
}
