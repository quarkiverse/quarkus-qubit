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

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles load instructions: ALOAD, primitives, GETFIELD.
 * Supports relationship navigation and bi-entity lambdas for join queries.
 */
public enum LoadInstructionHandler implements InstructionHandler {
    INSTANCE;

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == ALOAD || opcode == ILOAD || opcode == LLOAD ||
               opcode == FLOAD || opcode == DLOAD || opcode == GETFIELD;
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        switch (opcode) {
            case ALOAD -> handleALoad(ctx, (VarInsnNode) insn);
            case ILOAD, LLOAD, FLOAD, DLOAD -> handlePrimitiveLoad(ctx, opcode, (VarInsnNode) insn);
            case GETFIELD -> handleGetField(ctx, (FieldInsnNode) insn);
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
            case BiEntityParameter biParam ->
                // First-level field access from bi-entity parameter: ph.type
                ctx.push(new BiEntityFieldAccess(fieldName, fieldType, biParam.position()));

            case BiEntityFieldAccess biField -> {
                // Second-level field access from bi-entity: ph.owner.firstName
                // Convert to BiEntityPathExpression
                PathSegment firstSegment = new PathSegment(
                        biField.fieldName(),
                        biField.fieldType(),
                        RelationType.FIELD);

                List<PathSegment> segments = new ArrayList<>();
                segments.add(firstSegment);
                segments.add(newSegment);

                ctx.push(new BiEntityPathExpression(segments, fieldType, biField.entityPosition()));
            }

            case BiEntityPathExpression biPath -> {
                // Third+ level bi-entity field access: ph.owner.department.name
                List<PathSegment> segments = new ArrayList<>(biPath.segments());
                segments.add(newSegment);

                ctx.push(new BiEntityPathExpression(segments, fieldType, biPath.entityPosition()));
            }

            // ========== Single-entity mode ==========
            case LambdaExpression.Parameter ignored ->
                // First-level field access from entity parameter: p.age
                // For single-level access, continue using FieldAccess for backward compatibility
                ctx.push(new FieldAccess(fieldName, fieldType));

            case FieldAccess previousField -> {
                // Second-level field access: p.owner.firstName
                // Convert previous FieldAccess to PathExpression with two segments
                PathSegment firstSegment = new PathSegment(
                        previousField.fieldName(),
                        previousField.fieldType(),
                        RelationType.FIELD);

                List<PathSegment> segments = new ArrayList<>();
                segments.add(firstSegment);
                segments.add(newSegment);

                ctx.push(new PathExpression(segments, fieldType));
            }

            case PathExpression pathExpr -> {
                // Third+ level field access: p.owner.department.name
                // Extend existing PathExpression with new segment
                List<PathSegment> segments = new ArrayList<>(pathExpr.segments());
                segments.add(newSegment);

                ctx.push(new PathExpression(segments, fieldType));
            }

            // ========== Subquery correlated variables ==========
            case LambdaExpression.CapturedVariable capturedVar -> {
                // Field access on captured variable → CorrelatedVariable for subquery correlation
                LambdaExpression fieldExpr = new FieldAccess(fieldName, fieldType);
                ctx.push(new LambdaExpression.CorrelatedVariable(
                        fieldExpr,
                        capturedVar.index(),
                        capturedVar.type()));
            }

            case LambdaExpression.CorrelatedVariable correlatedVar -> {
                // Chained field access on correlated variable → extend path (e.g., p.owner.id)
                LambdaExpression innerField = correlatedVar.fieldExpression();

                LambdaExpression extendedPath = switch (innerField) {
                    case FieldAccess prevField -> {
                        // Convert FieldAccess to PathExpression with two segments
                        PathSegment firstSegment = new PathSegment(
                                prevField.fieldName(),
                                prevField.fieldType(),
                                RelationType.FIELD);
                        List<PathSegment> segments = new ArrayList<>();
                        segments.add(firstSegment);
                        segments.add(newSegment);
                        yield new PathExpression(segments, fieldType);
                    }
                    case PathExpression pathExpr -> {
                        // Extend existing PathExpression
                        List<PathSegment> segments = new ArrayList<>(pathExpr.segments());
                        segments.add(newSegment);
                        yield new PathExpression(segments, fieldType);
                    }
                    default -> {
                        // Fallback: wrap in PathExpression
                        List<PathSegment> segments = new ArrayList<>();
                        segments.add(newSegment);
                        yield new PathExpression(segments, fieldType);
                    }
                };

                ctx.push(new LambdaExpression.CorrelatedVariable(
                        extendedPath,
                        correlatedVar.outerParameterIndex(),
                        correlatedVar.outerEntityType()));
            }

            default ->
                // Fallback: create simple FieldAccess
                // This handles edge cases like field access on method return values
                ctx.push(new FieldAccess(fieldName, fieldType));
        }
    }
}
