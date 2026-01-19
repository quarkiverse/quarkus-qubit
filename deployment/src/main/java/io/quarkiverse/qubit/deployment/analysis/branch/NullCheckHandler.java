package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.JumpInsnNode;

import java.util.Deque;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.NE;
import static java.lang.Boolean.TRUE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;

/**
 * Handles null reference check instructions (IFNULL, IFNONNULL).
 *
 * <p>These instructions check if a reference on the stack is null:
 * <ul>
 *   <li>IFNULL - if reference == null, jump</li>
 *   <li>IFNONNULL - if reference != null, jump</li>
 * </ul>
 */
public class NullCheckHandler implements BranchHandler {

    private static final String INSTRUCTION_NAME = "IFNULL/IFNONNULL";

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        int opcode = jumpInsn.getOpcode();
        return opcode == IFNULL || opcode == IFNONNULL;
    }

    @Override
    public BranchState handle(BranchContext ctx) {
        Deque<LambdaExpression> stack = ctx.stack();

        if (stack.isEmpty()) {
            Log.tracef(INSTRUCTION_NAME + ": Stack empty, skipping");
            return ctx.state();
        }

        LambdaExpression fieldAccess = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME);
        LambdaExpression nullLiteral = new LambdaExpression.NullLiteral(Object.class);

        Boolean jumpTarget = ctx.jumpTarget();

        // Determine the null check operator based on opcode AND jump target
        // The correct operator depends on what the jump means:
        // - IFNULL jumping to TRUE: field == null (EQ)
        // - IFNULL jumping to FALSE: field != null (NE)
        // - IFNONNULL jumping to TRUE: field != null (NE)
        // - IFNONNULL jumping to FALSE: field == null (EQ)
        boolean isIfNull = (ctx.opcode() == IFNULL);
        boolean jumpingToTrue = TRUE.equals(jumpTarget);

        Operator operator;
        if (isIfNull && jumpingToTrue) {
            operator = EQ;  // IFNULL → TRUE means "is null"
        } else if (isIfNull) {
            operator = NE;  // IFNULL → FALSE means "is not null"
        } else if (jumpingToTrue) {
            operator = NE;  // IFNONNULL → TRUE means "is not null"
        } else {
            operator = EQ;  // IFNONNULL → FALSE means "is null"
        }

        LambdaExpression comparison = new LambdaExpression.BinaryOp(
            fieldAccess,
            operator,
            nullLiteral
        );

        Log.tracef(INSTRUCTION_NAME + ": opcode=%d, jumpTarget=%s, operator=%s, comparison=%s",
                ctx.opcode(), jumpTarget, operator, comparison);

        // Delegate to shared branch processing and combination logic
        return processAndCombineBranch(ctx, comparison, INSTRUCTION_NAME, TRUE.equals(jumpTarget));
    }
}
