package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkus.logging.Log;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.add;
import static io.quarkiverse.qubit.runtime.QubitConstants.JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY;
import static io.quarkiverse.qubit.runtime.QubitConstants.JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY;
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

    /** Marker for dynamic argument in StringConcatFactory recipe. */
    private static final char RECIPE_DYNAMIC_ARG = '\u0001';

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        return insn.getOpcode() == INVOKEDYNAMIC;
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;

        // Handle StringConcatFactory (Java 9+ string concatenation)
        if (isStringConcatFactory(indy)) {
            return handleStringConcatenation(indy, ctx);
        }

        // Iteration 7 & 8: Handle nested lambda creation for group aggregations and subqueries
        // When analyzing g.avg(p -> p.salary) or subquery(Person.class).avg(q -> q.salary),
        // we need to analyze nested QuerySpec lambdas inline
        if (isLambdaMetafactory(indy) && isQuerySpecLambda(indy)) {
            return handleNestedLambda(indy, ctx);
        }

        Log.tracef("INVOKEDYNAMIC not handled: %s (bsm=%s)", indy.name,
                   indy.bsm != null ? indy.bsm.getOwner() : "null");
        return false;
    }

    /**
     * Handles StringConcatFactory for string concatenation.
     */
    private boolean handleStringConcatenation(InvokeDynamicInsnNode indy, AnalysisContext ctx) {
        // Parse the recipe string from bootstrap method arguments
        String recipe = extractRecipe(indy);
        if (recipe == null) {
            Log.warnf("Could not extract recipe from StringConcatFactory: %s", indy.name);
            return false;
        }

        Log.tracef("StringConcatFactory recipe: '%s'", escapeRecipe(recipe));

        // Build concatenation expression from recipe and stack operands
        LambdaExpression result = buildConcatenationFromRecipe(ctx, recipe);

        if (result != null) {
            ctx.push(result);
        }

        return false; // Continue processing
    }

    /**
     * Handles nested lambda creation for group aggregations and subqueries.
     * <p>
     * When analyzing a HAVING clause like {@code g -> g.avg((Person p) -> p.salary) > 70000},
     * the inner lambda {@code (Person p) -> p.salary} is created via INVOKEDYNAMIC with
     * LambdaMetafactory. We need to analyze this nested lambda inline to get the field
     * expression for the aggregation.
     * <p>
     * For capturing lambdas (e.g., {@code ph -> ph.ownerId.equals(p.id)} where p is captured),
     * the INVOKEDYNAMIC consumes captured variables from the stack. We must pop these before
     * pushing the analyzed result.
     * <p>
     * Iteration 7: GROUP BY nested lambda support.
     * Iteration 8: Subquery nested lambda support with captured variable handling.
     */
    private boolean handleNestedLambda(InvokeDynamicInsnNode indy, AnalysisContext ctx) {
        // Extract the target lambda method from bootstrap method arguments
        // The impl method handle is typically bsmArgs[1] for LambdaMetafactory
        Handle implMethodHandle = extractImplMethodHandle(indy);
        if (implMethodHandle == null) {
            Log.debugf("Could not extract impl method handle from LambdaMetafactory: %s", indy.name);
            return false;
        }

        String nestedLambdaMethodName = implMethodHandle.getName();
        String nestedLambdaDescriptor = implMethodHandle.getDesc();

        Log.debugf("Nested lambda detected: %s%s", nestedLambdaMethodName, nestedLambdaDescriptor);

        // Pop any captured variables from the stack
        // The INVOKEDYNAMIC descriptor tells us how many captured variables there are
        // For example: (LPerson;)Lio/quarkiverse/qubit/runtime/QuerySpec; means 1 captured variable
        int capturedVarCount = countCapturedVariables(indy.desc);
        for (int i = 0; i < capturedVarCount; i++) {
            if (!ctx.isStackEmpty()) {
                ctx.pop(); // Discard captured variable (we analyze the lambda in isolation)
            }
        }

        // Find the nested lambda method in the current class
        MethodNode nestedMethod = ctx.findMethod(nestedLambdaMethodName, nestedLambdaDescriptor);
        if (nestedMethod == null) {
            Log.warnf("Could not find nested lambda method %s%s", nestedLambdaMethodName, nestedLambdaDescriptor);
            return false;
        }

        // Analyze the nested lambda to extract the field expression
        // The nested lambda is a single-entity QuerySpec like (Person p) -> p.salary
        int entityParamIndex = DescriptorParser.calculateEntityParameterSlotIndex(nestedLambdaDescriptor);
        LambdaExpression nestedExpression = ctx.analyzeNestedLambda(nestedMethod, entityParamIndex);

        if (nestedExpression != null) {
            ctx.push(nestedExpression);
            Log.debugf("Nested lambda analyzed: %s", nestedExpression);
        } else {
            Log.warnf("Failed to analyze nested lambda %s", nestedLambdaMethodName);
        }

        return false; // Continue processing
    }

    /**
     * Counts the number of captured variables in an INVOKEDYNAMIC descriptor.
     * <p>
     * The descriptor format is: (capturedTypes)ReturnType
     * For example: ()Lio/quarkiverse/qubit/runtime/QuerySpec; → 0 captured
     *              (LPerson;)Lio/quarkiverse/qubit/runtime/QuerySpec; → 1 captured
     */
    private int countCapturedVariables(String desc) {
        return DescriptorParser.countMethodArguments(desc);
    }

    /**
     * Checks if the invokedynamic instruction uses LambdaMetafactory.
     */
    private boolean isLambdaMetafactory(InvokeDynamicInsnNode indy) {
        return indy.bsm != null &&
               indy.bsm.getOwner().equals(JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY);
    }

    /**
     * Checks if the invokedynamic instruction creates a QuerySpec lambda.
     * <p>
     * Used for detecting nested lambdas in subqueries and group aggregations.
     * Checks if the return type of the invokedynamic is QuerySpec.
     */
    private boolean isQuerySpecLambda(InvokeDynamicInsnNode indy) {
        // The descriptor ends with the return type (the functional interface)
        // For QuerySpec: ()Lio/quarkiverse/qubit/runtime/QuerySpec;
        // For capturing lambdas: (capturedVars)Lio/quarkiverse/qubit/runtime/QuerySpec;
        return indy.desc.endsWith("Lio/quarkiverse/qubit/runtime/QuerySpec;");
    }

    /**
     * Extracts the implementation method handle from LambdaMetafactory bootstrap args.
     * The impl method handle is typically at index 1 in bsmArgs.
     *
     * @param indy the INVOKEDYNAMIC instruction
     * @return the implementation method handle, or null if not found
     */
    private Handle extractImplMethodHandle(InvokeDynamicInsnNode indy) {
        if (indy.bsmArgs == null || indy.bsmArgs.length < 2) {
            return null;
        }

        // bsmArgs[1] is the impl method handle
        Object arg = indy.bsmArgs[1];
        if (arg instanceof Handle) {
            return (Handle) arg;
        }

        return null;
    }

    /**
     * Checks if the invokedynamic instruction uses StringConcatFactory.
     */
    private boolean isStringConcatFactory(InvokeDynamicInsnNode indy) {
        // Check bootstrap method handle owner
        return indy.bsm != null &&
               indy.bsm.getOwner().equals(JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY);
    }

    /**
     * Extracts the recipe string from bootstrap method arguments.
     * The recipe is typically the first bootstrap argument.
     *
     * @param indy the INVOKEDYNAMIC instruction
     * @return the recipe string, or null if not found
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
     * @return concatenation expression tree, or null if stack underflow occurs
     */
    private LambdaExpression buildConcatenationFromRecipe(AnalysisContext ctx, String recipe) {
        // Count dynamic arguments (\u0001 markers)
        int dynamicArgCount = countDynamicArgs(recipe);

        // Pop operands from stack (they're in reverse order)
        List<LambdaExpression> operands = new ArrayList<>();
        for (int i = 0; i < dynamicArgCount; i++) {
            if (ctx.isStackEmpty()) {
                Log.warnf("Stack underflow while parsing StringConcatFactory recipe: '%s'", escapeRecipe(recipe));
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
     * @return concatenation expression tree, or null if recipe is empty
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
     * @param result current expression tree, or null
     * @return updated expression tree, or null if buffer is empty and result is null
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
     * @param result current expression tree, or null
     * @param toAdd expression to append
     * @return updated expression tree
     */
    private LambdaExpression appendToResult(LambdaExpression result, LambdaExpression toAdd) {
        if (result == null) {
            return toAdd;
        }
        return add(result, toAdd);
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
