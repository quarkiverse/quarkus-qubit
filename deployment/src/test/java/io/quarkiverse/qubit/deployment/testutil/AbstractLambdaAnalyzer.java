package io.quarkiverse.qubit.deployment.testutil;

import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.BeforeEach;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for lambda bytecode analysis tests.
 *
 * <p>Provides shared infrastructure for loading compiled test source classes
 * and extracting lambda expressions from their methods. This eliminates code
 * duplication across PrecompiledLambdaAnalyzer, PrecompiledBiEntityLambdaAnalyzer,
 * PrecompiledSubqueryLambdaAnalyzer, and CriteriaQueryTestBase.
 *
 * <p>Subclasses specify:
 * <ul>
 *   <li>The source class to analyze (via {@link #getSourcesClassName()})</li>
 *   <li>The descriptor pattern to match (via {@link #getDescriptorPattern()})</li>
 * </ul>
 */
public abstract class AbstractLambdaAnalyzer {

    // Cache of loaded class nodes, keyed by class name
    private static final Map<String, ClassNode> classNodeCache = new HashMap<>();

    /** Clears all caches before each test for isolation. */
    @BeforeEach
    void clearAnalyzerCache() {
        LambdaBytecodeAnalyzer.clearCache();
        classNodeCache.clear();
    }

    /**
     * Returns the fully-qualified name of the class containing lambda test sources.
     *
     * @return the source class name (e.g., "io.quarkiverse.qubit.deployment.testutil.LambdaTestSources")
     */
    protected abstract String getSourcesClassName();

    /**
     * Returns the descriptor pattern to match when searching for invokedynamic instructions.
     * This is used to identify the correct lambda type (QuerySpec, BiQuerySpec, etc.).
     *
     * @return the descriptor pattern substring to match
     */
    protected abstract String getDescriptorPattern();

    /**
     * Loads the source class bytecode and caches it.
     *
     * @return the ClassNode representing the compiled class
     */
    protected ClassNode loadSourcesClass() {
        String className = getSourcesClassName();
        return classNodeCache.computeIfAbsent(className, this::loadClassNode);
    }

    private ClassNode loadClassNode(String className) {
        try {
            String classFile = className.replace('.', '/') + ".class";
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(classFile)) {
                if (is == null) {
                    throw new RuntimeException("Cannot find class file: " + classFile);
                }
                ClassReader reader = new ClassReader(is);
                ClassNode classNode = new ClassNode();
                // Skip debug info and frames - not needed for lambda analysis
                reader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return classNode;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load class: " + className, e);
        }
    }

    /**
     * Gets the bytecode of the source class.
     *
     * @return the class bytes
     */
    protected byte[] getSourceClassBytes() {
        String className = getSourcesClassName();
        String classFile = className.replace('.', '/') + ".class";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classFile)) {
            if (is == null) {
                throw new RuntimeException("Cannot find class file: " + classFile);
            }
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read class bytes: " + className, e);
        }
    }

    /**
     * Finds a method by name in a class.
     *
     * @param classNode the class to search
     * @param methodName the method name to find
     * @return the MethodNode, or null if not found
     */
    protected MethodNode findMethod(ClassNode classNode, String methodName) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Finds the first invokedynamic instruction in a method that matches the descriptor pattern.
     *
     * @param method the method to search
     * @return the InvokeDynamicInsnNode, or null if not found
     */
    protected InvokeDynamicInsnNode findInvokeDynamic(MethodNode method) {
        String pattern = getDescriptorPattern();
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof InvokeDynamicInsnNode invokeDynamic) {
                if (invokeDynamic.desc.contains(pattern)) {
                    return invokeDynamic;
                }
            }
        }
        return null;
    }

    /**
     * Extracts the lambda implementation method handle from an invokedynamic instruction.
     *
     * @param invokeDynamic the invokedynamic instruction
     * @return the Handle representing the lambda implementation method, or null if not found
     */
    protected Handle extractLambdaHandle(InvokeDynamicInsnNode invokeDynamic) {
        Object[] bsmArgs = invokeDynamic.bsmArgs;
        if (bsmArgs != null && bsmArgs.length >= 2 && bsmArgs[1] instanceof Handle handle) {
            return handle;
        }
        return null;
    }

    /**
     * Validates and retrieves the lambda handle from a method.
     * Throws RuntimeException with descriptive message if any step fails.
     *
     * @param methodName the method name containing the lambda
     * @return the lambda Handle
     */
    protected Handle getLambdaHandle(String methodName) {
        ClassNode classNode = loadSourcesClass();

        MethodNode sourceMethod = findMethod(classNode, methodName);
        if (sourceMethod == null) {
            throw new RuntimeException("Cannot find source method: " + methodName +
                    " in class " + getSourcesClassName());
        }

        InvokeDynamicInsnNode invokeDynamic = findInvokeDynamic(sourceMethod);
        if (invokeDynamic == null) {
            throw new RuntimeException("No invokedynamic instruction found in method: " + methodName +
                    " matching pattern: " + getDescriptorPattern());
        }

        Handle lambdaHandle = extractLambdaHandle(invokeDynamic);
        if (lambdaHandle == null) {
            throw new RuntimeException("Cannot extract lambda handle from method: " + methodName);
        }

        return lambdaHandle;
    }

    // ==================== COMMON ASSERTION HELPERS ====================

    /**
     * Asserts that an expression is a binary operation with the expected operator.
     */
    protected void assertBinaryOp(LambdaExpression expr, LambdaExpression.BinaryOp.Operator expectedOp) {
        assertThat(expr)
                .as("Expression should be a BinaryOp but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.BinaryOp.class);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertThat(binOp.operator())
                .as("BinaryOp operator should be %s", expectedOp)
                .isEqualTo(expectedOp);
    }

    /**
     * Asserts that an expression is a field access with the expected field name.
     */
    protected void assertFieldAccess(LambdaExpression expr, String expectedFieldName) {
        assertThat(expr)
                .as("Expression should be a FieldAccess but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.FieldAccess.class);
        LambdaExpression.FieldAccess fieldAccess = (LambdaExpression.FieldAccess) expr;
        assertThat(fieldAccess.fieldName())
                .as("FieldAccess field name should be '%s'", expectedFieldName)
                .isEqualTo(expectedFieldName);
    }

    /**
     * Asserts that an expression is a constant with the expected value.
     */
    protected void assertConstant(LambdaExpression expr, Object expectedValue) {
        assertThat(expr)
                .as("Expression should be a Constant but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.Constant.class);
        LambdaExpression.Constant constant = (LambdaExpression.Constant) expr;
        assertThat(constant.value())
                .as("Constant value should be '%s'", expectedValue)
                .isEqualTo(expectedValue);
    }

    /**
     * Asserts that an expression is a method call with the expected method name.
     */
    protected void assertMethodCall(LambdaExpression expr, String expectedMethodName) {
        assertThat(expr)
                .as("Expression should be a MethodCall but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.MethodCall.class);
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertThat(methodCall.methodName())
                .as("MethodCall method name should be '%s'", expectedMethodName)
                .isEqualTo(expectedMethodName);
    }

    /**
     * Asserts that an expression is a unary operation with the expected operator.
     */
    protected void assertUnaryOp(LambdaExpression expr, LambdaExpression.UnaryOp.Operator expectedOp) {
        assertThat(expr)
                .as("Expression should be a UnaryOp but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.UnaryOp.class);
        LambdaExpression.UnaryOp unaryOp = (LambdaExpression.UnaryOp) expr;
        assertThat(unaryOp.operator())
                .as("UnaryOp operator should be %s", expectedOp)
                .isEqualTo(expectedOp);
    }

    /**
     * Asserts that an expression is a captured variable.
     */
    protected void assertCapturedVariable(LambdaExpression expr) {
        assertThat(expr)
                .as("Expression should be a CapturedVariable but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.CapturedVariable.class);
    }

    /**
     * Asserts that an expression is a captured variable with a specific index.
     */
    protected void assertCapturedVariable(LambdaExpression expr, int expectedIndex) {
        assertThat(expr)
                .as("Expression should be a CapturedVariable but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.CapturedVariable.class);
        LambdaExpression.CapturedVariable capturedVar = (LambdaExpression.CapturedVariable) expr;
        assertThat(capturedVar.index())
                .as("CapturedVariable index should be %d", expectedIndex)
                .isEqualTo(expectedIndex);
    }

    /**
     * Asserts that an expression is a null literal.
     */
    protected void assertNullLiteral(LambdaExpression expr) {
        assertThat(expr)
                .as("Expression should be a NullLiteral but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.NullLiteral.class);
    }
}
