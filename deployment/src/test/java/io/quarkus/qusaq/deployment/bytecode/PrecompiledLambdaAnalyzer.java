package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.LambdaBytecodeAnalyzer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static io.quarkus.qusaq.runtime.QusaqConstants.QUERY_SPEC_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for bytecode analysis tests using pre-compiled lambda sources.
 *
 * <p>This analyzer loads the {@link LambdaTestSources} class and extracts
 * lambda expressions from its pre-compiled methods for analysis.
 */
public abstract class PrecompiledLambdaAnalyzer {

    private static final String SOURCES_CLASS_NAME = "io.quarkus.qusaq.deployment.testutil.LambdaTestSources";
    private static final String SOURCES_CLASS_FILE = SOURCES_CLASS_NAME.replace('.', '/') + ".class";

    private static ClassNode sourcesClassNode;

    /**
     * Analyzes a pre-compiled lambda expression by method name.
     *
     * @param methodName the name of the method in LambdaTestSources containing the lambda
     * @return the parsed LambdaExpression AST
     */
    protected LambdaExpression analyzeLambda(String methodName) {
        try {
            // Load the LambdaTestSources class on first use
            if (sourcesClassNode == null) {
                sourcesClassNode = loadSourcesClass();
            }

            // Find the method containing the lambda
            MethodNode sourceMethod = findMethod(sourcesClassNode, methodName);
            if (sourceMethod == null) {
                throw new RuntimeException("Cannot find source method: " + methodName);
            }

            // Find the invokedynamic instruction that creates the lambda
            InvokeDynamicInsnNode invokeDynamic = findInvokeDynamic(sourceMethod);
            if (invokeDynamic == null) {
                throw new RuntimeException("No invokedynamic instruction found in method: " + methodName);
            }

            // Extract the lambda implementation handle
            Handle lambdaHandle = extractLambdaHandle(invokeDynamic);
            if (lambdaHandle == null) {
                throw new RuntimeException("Cannot extract lambda handle from method: " + methodName);
            }

            // Get the class bytecode for the analyzer
            byte[] classBytes;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(SOURCES_CLASS_FILE)) {
                if (is == null) {
                    throw new RuntimeException("Cannot find LambdaTestSources class file: " + SOURCES_CLASS_FILE);
                }
                classBytes = is.readAllBytes();
            }

            // Use the LambdaBytecodeAnalyzer directly
            LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer();
            return analyzer.analyze(classBytes, lambdaHandle.getName(), lambdaHandle.getDesc());

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze lambda from method: " + methodName, e);
        }
    }

    /**
     * Loads the LambdaTestSources class bytecode.
     */
    private ClassNode loadSourcesClass() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(SOURCES_CLASS_FILE);
        if (is == null) {
            throw new RuntimeException("Cannot find LambdaTestSources class file: " + SOURCES_CLASS_FILE);
        }

        ClassReader reader = new ClassReader(is);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    /**
     * Finds a method by name in a class.
     */
    private MethodNode findMethod(ClassNode classNode, String methodName) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Finds the first invokedynamic instruction in a method that creates a QuerySpec.
     */
    private InvokeDynamicInsnNode findInvokeDynamic(MethodNode method) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) method.instructions.get(i);
                if (isQuerySpecLambda(invokeDynamic)) {
                    return invokeDynamic;
                }
            }
        }
        return null;
    }

    /**
     * Checks if an invokedynamic instruction creates a QuerySpec lambda.
     */
    private boolean isQuerySpecLambda(InvokeDynamicInsnNode invokeDynamic) {
        String desc = invokeDynamic.desc;
        return desc.contains(QUERY_SPEC_DESCRIPTOR);
    }

    /**
     * Extracts the lambda implementation method handle.
     */
    private Handle extractLambdaHandle(InvokeDynamicInsnNode invokeDynamic) {
        Object[] bsmArgs = invokeDynamic.bsmArgs;
        if (bsmArgs != null && bsmArgs.length >= 2 && bsmArgs[1] instanceof Handle) {
            return (Handle) bsmArgs[1];
        }
        return null;
    }

    // ==================== ASSERTION HELPERS ====================

    /**
     * Asserts that an expression is a binary operation with the expected operator.
     */
    protected void assertBinaryOp(LambdaExpression expr, LambdaExpression.BinaryOp.Operator expectedOp) {
        assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertThat(binOp.operator()).isEqualTo(expectedOp);
    }

    /**
     * Asserts that an expression is a field access with the expected field name.
     */
    protected void assertFieldAccess(LambdaExpression expr, String expectedFieldName) {
        assertThat(expr).isInstanceOf(LambdaExpression.FieldAccess.class);
        LambdaExpression.FieldAccess fieldAccess = (LambdaExpression.FieldAccess) expr;
        assertThat(fieldAccess.fieldName()).isEqualTo(expectedFieldName);
    }

    /**
     * Asserts that an expression is a constant with the expected value.
     */
    protected void assertConstant(LambdaExpression expr, Object expectedValue) {
        assertThat(expr).isInstanceOf(LambdaExpression.Constant.class);
        LambdaExpression.Constant constant = (LambdaExpression.Constant) expr;
        assertThat(constant.value()).isEqualTo(expectedValue);
    }

    /**
     * Asserts that an expression is a method call with the expected method name.
     */
    protected void assertMethodCall(LambdaExpression expr, String expectedMethodName) {
        assertThat(expr).isInstanceOf(LambdaExpression.MethodCall.class);
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertThat(methodCall.methodName()).isEqualTo(expectedMethodName);
    }

    /**
     * Asserts that an expression is a unary operation with the expected operator.
     */
    protected void assertUnaryOp(LambdaExpression expr, LambdaExpression.UnaryOp.Operator expectedOp) {
        assertThat(expr).isInstanceOf(LambdaExpression.UnaryOp.class);
        LambdaExpression.UnaryOp unaryOp = (LambdaExpression.UnaryOp) expr;
        assertThat(unaryOp.operator()).isEqualTo(expectedOp);
    }

    /**
     * Asserts that an expression is a captured variable.
     */
    protected void assertCapturedVariable(LambdaExpression expr) {
        assertThat(expr).isInstanceOf(LambdaExpression.CapturedVariable.class);
    }

    /**
     * Asserts that an expression is a captured variable with a specific index.
     */
    protected void assertCapturedVariable(LambdaExpression expr, int expectedIndex) {
        assertThat(expr).isInstanceOf(LambdaExpression.CapturedVariable.class);
        LambdaExpression.CapturedVariable capturedVar = (LambdaExpression.CapturedVariable) expr;
        assertThat(capturedVar.index()).isEqualTo(expectedIndex);
    }

    /**
     * Asserts that an expression is a null literal.
     */
    protected void assertNullLiteral(LambdaExpression expr) {
        assertThat(expr).isInstanceOf(LambdaExpression.NullLiteral.class);
    }
}
