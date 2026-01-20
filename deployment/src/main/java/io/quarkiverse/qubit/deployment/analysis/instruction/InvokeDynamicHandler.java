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
import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnsType;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_SPEC_INTERNAL_NAME;
import static org.objectweb.asm.Opcodes.INVOKEDYNAMIC;

/**
 * Handles INVOKEDYNAMIC: Java 9+ StringConcatFactory string concatenation and nested QuerySpec lambdas.
 * Recipe '\u0001' marks dynamic args; other chars are constants.
 */
public enum InvokeDynamicHandler implements InstructionHandler {
    INSTANCE;

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

        // Handle nested lambda creation for group aggregations and subqueries
        // When analyzing g.avg(p -> p.salary) or subquery(Person.class).avg(q -> q.salary),
        // we need to analyze nested QuerySpec lambdas inline
        if (isLambdaMetafactory(indy) && isQuerySpecLambda(indy)) {
            return handleNestedLambda(indy, ctx);
        }

        Log.tracef("INVOKEDYNAMIC not handled: %s (bsm=%s)", indy.name,
                   indy.bsm != null ? indy.bsm.getOwner() : "null");
        return false;
    }

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

    /** Analyzes nested QuerySpec lambdas inline for group aggregations and subqueries. */
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
        // For example: (LPerson;)Lio/quarkiverse/qubit/QuerySpec; means 1 captured variable
        int capturedVarCount = DescriptorParser.countMethodArguments(indy.desc);
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

    private boolean isLambdaMetafactory(InvokeDynamicInsnNode indy) {
        return indy.bsm != null &&
               indy.bsm.getOwner().equals(JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY);
    }

    private boolean isQuerySpecLambda(InvokeDynamicInsnNode indy) {
        return returnsType(indy.desc, QUERY_SPEC_INTERNAL_NAME);
    }

    /** Extracts impl method handle from bsmArgs[1]. */
    private Handle extractImplMethodHandle(InvokeDynamicInsnNode indy) {
        if (indy.bsmArgs == null || indy.bsmArgs.length < 2) {
            return null;
        }
        return indy.bsmArgs[1] instanceof Handle handle ? handle : null;
    }

    private boolean isStringConcatFactory(InvokeDynamicInsnNode indy) {
        return indy.bsm != null &&
               indy.bsm.getOwner().equals(JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY);
    }

    /** Extracts recipe string from bsmArgs[0]. */
    private String extractRecipe(InvokeDynamicInsnNode indy) {
        if (indy.bsmArgs == null || indy.bsmArgs.length == 0) {
            return null;
        }
        return indy.bsmArgs[0] instanceof String recipe ? recipe : null;
    }

    /** Builds BinaryOp ADD tree from recipe pattern and stack operands. */
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

    private int countDynamicArgs(String recipe) {
        int count = 0;
        for (char c : recipe.toCharArray()) {
            if (c == RECIPE_DYNAMIC_ARG) {
                count++;
            }
        }
        return count;
    }

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

    private LambdaExpression flushConstantBuffer(StringBuilder buffer, LambdaExpression result) {
        if (buffer.isEmpty()) {
            return result;
        }

        LambdaExpression constant = new LambdaExpression.Constant(buffer.toString(), String.class);
        buffer.setLength(0);
        return appendToResult(result, constant);
    }

    private LambdaExpression appendToResult(LambdaExpression result, LambdaExpression toAdd) {
        if (result == null) {
            return toAdd;
        }
        return add(result, toAdd);
    }

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
