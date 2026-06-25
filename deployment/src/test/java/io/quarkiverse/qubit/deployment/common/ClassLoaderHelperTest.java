package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant;

class ClassLoaderHelperTest {

    @Nested
    class ExtractEntityClassInfoTests {

        @Test
        void extractEntityClassInfo_withTypeConstant_returnsPlaceholderToAvoidDeadlock() {
            Type asmType = Type.getType(String.class);
            Constant constant = new Constant(asmType, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Placeholder should use Object.class to avoid build-time class loading")
                    .isEqualTo(Object.class);
            assertThat(result.className())
                    .as("className should contain the deferred class name")
                    .isEqualTo("java.lang.String");
            EntityClassInfoAssert.assertThat(result)
                    .as("Should be marked as placeholder for runtime resolution")
                    .isPlaceholder();
        }

        @Test
        void extractEntityClassInfo_withTypeConstantInteger_returnsPlaceholderToAvoidDeadlock() {
            Type asmType = Type.getType(Integer.class);
            Constant constant = new Constant(asmType, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Placeholder should use Object.class")
                    .isEqualTo(Object.class);
            assertThat(result.className())
                    .as("className should contain the deferred class name")
                    .isEqualTo("java.lang.Integer");
            EntityClassInfoAssert.assertThat(result)
                    .isPlaceholder();
        }

        @Test
        void extractEntityClassInfo_withUnloadableType_returnsPlaceholder() {
            Type asmType = Type.getType("Lcom/nonexistent/EntityClass;");
            Constant constant = new Constant(asmType, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Placeholder should use Object.class")
                    .isEqualTo(Object.class);
            assertThat(result.className())
                    .as("className should be set for unloadable class")
                    .isEqualTo("com.nonexistent.EntityClass");
        }

        @Test
        void extractEntityClassInfo_withClassConstant_returnsClass() {
            Constant constant = new Constant(String.class, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Class constant should return that class")
                    .isEqualTo(String.class);
            assertThat(result.className()).isNull();
        }

        @Test
        void extractEntityClassInfo_withClassConstantLong_returnsClass() {
            Constant constant = new Constant(Long.class, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Class constant should return Long.class")
                    .isEqualTo(Long.class);
        }

        @Test
        void extractEntityClassInfo_withStringConstant_returnsObjectClass() {
            Constant constant = new Constant("not a class", String.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Non-Class constant should return Object.class")
                    .isEqualTo(Object.class);
            assertThat(result.className())
                    .as("className should be null for invalid constant")
                    .isNull();
        }

        @Test
        void extractEntityClassInfo_withIntegerConstant_returnsObjectClass() {
            Constant constant = new Constant(42, Integer.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Integer constant should return Object.class")
                    .isEqualTo(Object.class);
        }

        @Test
        void extractEntityClassInfo_withFieldAccess_returnsObjectClass() {
            LambdaExpression expr = field("name", String.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(expr);

            assertThat(result.clazz())
                    .as("FieldAccess should return Object.class")
                    .isEqualTo(Object.class);
            assertThat(result.className()).isNull();
        }

        @Test
        void extractEntityClassInfo_withParameter_returnsObjectClass() {
            LambdaExpression expr = param("e", Object.class, 0);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(expr);

            assertThat(result.clazz())
                    .as("Parameter should return Object.class")
                    .isEqualTo(Object.class);
        }

        @Test
        void extractEntityClassInfo_withCapturedVariable_returnsObjectClass() {
            LambdaExpression expr = captured(0, String.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(expr);

            assertThat(result.clazz())
                    .as("CapturedVariable should return Object.class")
                    .isEqualTo(Object.class);
        }
    }
}
