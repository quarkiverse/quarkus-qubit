package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.util.ArrayList;
import java.util.List;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.ADD;
import static org.objectweb.asm.Opcodes.INVOKEDYNAMIC;

/**
 * Handles INVOKEDYNAMIC instructions, specifically for Java 9+ string concatenation
 * via StringConcatFactory.
 *
 * <p>Java 9+ compiles string concatenation using invokedynamic with
 * {@code java.lang.invoke.StringConcatFactory} as the bootstrap method, not traditional
 * StringBuilder bytecode. The recipe string describes the concatenation pattern:
 *
 * <ul>
 * <li>{@code \u0001} - placeholder for dynamic argument (field, variable)</li>
 * <li>Other characters - string constants</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 * "Hello, " + p.firstName
 *   → Recipe: "Hello, \u0001"
 *   → Operands: [p.firstName]
 *
 * p.firstName + " " + p.lastName
 *   → Recipe: "\u0001 \u0001"
 *   → Operands: [p.firstName, p.lastName]
 *
 * "Mr. " + p.firstName + " " + p.lastName
 *   → Recipe: "Mr. \u0001 \u0001"
 *   → Operands: [p.firstName, p.lastName]
 * </pre>
 *
 * <p>This handler parses the recipe and constructs a tree of {@link LambdaExpression.BinaryOp}
 * nodes with {@code ADD} operator, which {@code CriteriaExpressionGenerator} translates to
 * JPA {@code CriteriaBuilder.concat()} calls.
 */
public class InvokeDynamicHandler implements InstructionHandler {

    private static final Logger log = Logger.getLogger(InvokeDynamicHandler.class);

    /** Marker for dynamic argument in StringConcatFactory recipe. */
    private static final char RECIPE_DYNAMIC_ARG = '\u0001';

    /** StringConcatFactory bootstrap method name. */
    private static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        return insn.getOpcode() == INVOKEDYNAMIC;
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;

        // Only handle StringConcatFactory (Java 9+ string concatenation)
        if (!isStringConcatFactory(indy)) {
            log.tracef("INVOKEDYNAMIC is not StringConcatFactory, ignoring: %s", indy.name);
            return false; // Not string concatenation, skip
        }

        // Parse the recipe string from bootstrap method arguments
        String recipe = extractRecipe(indy);
        if (recipe == null) {
            log.warnf("Could not extract recipe from StringConcatFactory: %s", indy.name);
            return false;
        }

        log.tracef("StringConcatFactory recipe: '%s'", escapeRecipe(recipe));

        // Build concatenation expression from recipe and stack operands
        LambdaExpression result = buildConcatenationFromRecipe(ctx, recipe);

        if (result != null) {
            ctx.push(result);
        }

        return false; // Continue processing
    }

    /**
     * Checks if the invokedynamic instruction uses StringConcatFactory.
     */
    private boolean isStringConcatFactory(InvokeDynamicInsnNode indy) {
        // Check bootstrap method handle owner
        return indy.bsm != null &&
               indy.bsm.getOwner().equals(STRING_CONCAT_FACTORY);
    }

    /**
     * Extracts the recipe string from bootstrap method arguments.
     * The recipe is typically the first bootstrap argument.
     */
    private String extractRecipe(InvokeDynamicInsnNode indy) {
        if (indy.bsmArgs == null || indy.bsmArgs.length == 0) {
            return null;
        }

        // Recipe is usually the first argument
        Object recipeArg = indy.bsmArgs[0];
        if (recipeArg instanceof String) {
            return (String) recipeArg;
        }

        return null;
    }

    /**
     * Builds concatenation expression tree from recipe and stack operands.
     *
     * <p>Algorithm:
     * <ol>
     * <li>Count dynamic arguments in recipe (\u0001 markers)</li>
     * <li>Pop that many operands from stack (in reverse order)</li>
     * <li>Parse recipe left-to-right, building BinaryOp tree:
     *     <ul>
     *     <li>String constant → create Constant node</li>
     *     <li>\u0001 → consume next operand from list</li>
     *     <li>Combine with ADD operator</li>
     *     </ul>
     * </li>
     * </ol>
     *
     * @param ctx analysis context (provides expression stack)
     * @param recipe concatenation recipe string
     * @return concatenation expression tree, or null if parsing fails
     */
    private LambdaExpression buildConcatenationFromRecipe(AnalysisContext ctx, String recipe) {
        // Count dynamic arguments (\u0001 markers)
        int dynamicArgCount = countDynamicArgs(recipe);

        // Pop operands from stack (they're in reverse order)
        List<LambdaExpression> operands = new ArrayList<>();
        for (int i = 0; i < dynamicArgCount; i++) {
            if (ctx.isStackEmpty()) {
                log.warnf("Stack underflow while parsing StringConcatFactory recipe: '%s'", escapeRecipe(recipe));
                return null;
            }
            operands.add(0, ctx.pop()); // Insert at beginning to reverse order
        }

        // Parse recipe and build expression tree
        return parseRecipe(recipe, operands);
    }

    /**
     * Counts the number of dynamic argument markers in the recipe.
     */
    private int countDynamicArgs(String recipe) {
        int count = 0;
        for (char c : recipe.toCharArray()) {
            if (c == RECIPE_DYNAMIC_ARG) {
                count++;
            }
        }
        return count;
    }

    /**
     * Parses recipe string and builds concatenation expression tree.
     *
     * <p>Examples:
     * <pre>
     * Recipe: "Hello, \u0001"
     *   → Constant("Hello, ") + operands[0]
     *
     * Recipe: "\u0001 \u0001"
     *   → operands[0] + Constant(" ") + operands[1]
     *   → (operands[0] + Constant(" ")) + operands[1]
     *
     * Recipe: "Mr. \u0001 \u0001"
     *   → Constant("Mr. ") + operands[0] + Constant(" ") + operands[1]
     *   → ((Constant("Mr. ") + operands[0]) + Constant(" ")) + operands[1]
     * </pre>
     *
     * @param recipe concatenation recipe
     * @param operands dynamic operands (popped from stack)
     * @return concatenation expression tree
     */
    private LambdaExpression parseRecipe(String recipe, List<LambdaExpression> operands) {
        LambdaExpression result = null;
        StringBuilder constantBuffer = new StringBuilder();
        int operandIndex = 0;

        for (char c : recipe.toCharArray()) {
            if (c == RECIPE_DYNAMIC_ARG) {
                result = flushConstantBuffer(constantBuffer, result);

                if (operandIndex < operands.size()) {
                    result = appendToResult(result, operands.get(operandIndex));
                    operandIndex++;
                }
            } else {
                constantBuffer.append(c);
            }
        }

        result = flushConstantBuffer(constantBuffer, result);
        return result;
    }

    /**
     * Flushes accumulated string constant from buffer and appends to result.
     * Clears the buffer after flushing.
     *
     * @param buffer constant accumulator
     * @param result current expression tree
     * @return updated expression tree
     */
    private LambdaExpression flushConstantBuffer(StringBuilder buffer, LambdaExpression result) {
        if (buffer.isEmpty()) {
            return result;
        }

        LambdaExpression constant = new LambdaExpression.Constant(buffer.toString(), String.class);
        buffer.setLength(0);
        return appendToResult(result, constant);
    }

    /**
     * Appends expression to result tree using ADD operator.
     * If result is null, returns toAdd directly (first element).
     *
     * @param result current expression tree (may be null)
     * @param toAdd expression to append
     * @return updated expression tree
     */
    private LambdaExpression appendToResult(LambdaExpression result, LambdaExpression toAdd) {
        if (result == null) {
            return toAdd;
        }
        return new LambdaExpression.BinaryOp(result, ADD, toAdd);
    }

    /**
     * Escapes recipe string for logging (makes \u0001 visible).
     */
    private String escapeRecipe(String recipe) {
        if (recipe == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder();
        for (char c : recipe.toCharArray()) {
            if (c == RECIPE_DYNAMIC_ARG) {
                sb.append("\\u0001");
            } else if (c < 32 || c > 126) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
