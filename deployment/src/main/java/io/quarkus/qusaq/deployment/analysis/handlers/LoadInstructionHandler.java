package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.BiEntityFieldAccess;
import io.quarkus.qusaq.deployment.LambdaExpression.BiEntityParameter;
import io.quarkus.qusaq.deployment.LambdaExpression.BiEntityPathExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.EntityPosition;
import io.quarkus.qusaq.deployment.LambdaExpression.FieldAccess;
import io.quarkus.qusaq.deployment.LambdaExpression.PathExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.PathSegment;
import io.quarkus.qusaq.deployment.LambdaExpression.RelationType;
import io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisException;
import io.quarkus.qusaq.deployment.util.DescriptorParser;
import io.quarkus.qusaq.deployment.util.TypeConverter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles load instructions: ALOAD, primitives, GETFIELD.
 * <p>
 * Enhanced in Iteration 4 to support relationship navigation by detecting
 * chained GETFIELD instructions and building PathExpression AST nodes.
 * <p>
 * Enhanced in Iteration 6 to support bi-entity lambdas (BiQuerySpec) for join
 * queries. In bi-entity mode, produces BiEntityParameter, BiEntityFieldAccess,
 * and BiEntityPathExpression nodes that track which entity the expression
 * belongs to (FIRST or SECOND).
 */
public class LoadInstructionHandler implements InstructionHandler {

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

    /**
     * Handles ALOAD: entity parameter or captured variable.
     * <p>
     * In bi-entity mode (join queries), checks both entity parameter slots and
     * produces BiEntityParameter nodes that track the entity position.
     */
    private void handleALoad(AnalysisContext ctx, VarInsnNode varInsn) {
        EntityPosition entityPosition = ctx.getEntityPosition(varInsn.var);

        if (entityPosition != null) {
            // This is an entity parameter
            if (ctx.isBiEntityMode()) {
                // Bi-entity lambda: produce BiEntityParameter
                String paramName = entityPosition == EntityPosition.FIRST ? "entity" : "joinedEntity";
                ctx.push(new BiEntityParameter(paramName, Object.class, varInsn.var, entityPosition));
            } else {
                // Single-entity lambda: produce Parameter
                ctx.push(new LambdaExpression.Parameter("entity", Object.class, varInsn.var));
            }
        } else {
            // This is a captured variable from the enclosing scope
            int paramIndex = DescriptorParser.slotIndexToParameterIndex(
                    ctx.getMethod().desc, varInsn.var);
            Class<?> varType = paramIndex >= 0
                    ? DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex)
                    : Object.class;
            ctx.push(new LambdaExpression.CapturedVariable(paramIndex, varType));
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

        Class<?> actualType = primitiveType;
        if (primitiveType == int.class && paramIndex >= 0) {
            actualType = DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex);
        }

        ctx.push(new LambdaExpression.CapturedVariable(paramIndex, actualType));
    }

    /**
     * Handles GETFIELD: converts to FieldAccess or PathExpression node.
     * <p>
     * Iteration 4 Enhancement: Detects chained field access patterns and builds
     * PathExpression AST nodes for relationship navigation.
     * <p>
     * Iteration 6 Enhancement: Produces BiEntityFieldAccess and BiEntityPathExpression
     * nodes in bi-entity mode (join queries) to track entity position.
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

        // Create new segment for this field access
        // Note: RelationType.FIELD is used as default; actual relationship type
        // will be resolved during JPA generation using RelationshipMetadataExtractor
        PathSegment newSegment = new PathSegment(fieldName, fieldType, RelationType.FIELD);

        // =========================================================================
        // Bi-entity mode handling (join queries)
        // =========================================================================

        if (target instanceof BiEntityParameter biParam) {
            // First-level field access from bi-entity parameter: ph.type
            ctx.push(new BiEntityFieldAccess(fieldName, fieldType, biParam.position()));

        } else if (target instanceof BiEntityFieldAccess biField) {
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

        } else if (target instanceof BiEntityPathExpression biPath) {
            // Third+ level bi-entity field access: ph.owner.department.name
            List<PathSegment> segments = new ArrayList<>(biPath.segments());
            segments.add(newSegment);

            ctx.push(new BiEntityPathExpression(segments, fieldType, biPath.entityPosition()));

        // =========================================================================
        // Single-entity mode handling (original behavior)
        // =========================================================================

        } else if (target instanceof LambdaExpression.Parameter) {
            // First-level field access from entity parameter: p.age
            // For single-level access, continue using FieldAccess for backward compatibility
            ctx.push(new FieldAccess(fieldName, fieldType));

        } else if (target instanceof FieldAccess previousField) {
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

        } else if (target instanceof PathExpression pathExpr) {
            // Third+ level field access: p.owner.department.name
            // Extend existing PathExpression with new segment
            List<PathSegment> segments = new ArrayList<>(pathExpr.segments());
            segments.add(newSegment);

            ctx.push(new PathExpression(segments, fieldType));

        } else {
            // Fallback: create simple FieldAccess
            // This handles edge cases like field access on method return values
            ctx.push(new FieldAccess(fieldName, fieldType));
        }
    }
}
