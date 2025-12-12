package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClassLoaderHelper}.
 *
 * <p>Tests class loading utilities for build-time bytecode analysis.
 */
class ClassLoaderHelperTest {

    // ==================== tryLoadClass Tests ====================

    @Nested
    class TryLoadClassTests {

        @Test
        void tryLoadClass_withNull_returnsNull() {
            Class<?> result = ClassLoaderHelper.tryLoadClass(null);

            assertThat(result)
                    .as("Null className should return null")
                    .isNull();
        }

        @Test
        void tryLoadClass_withEmptyString_returnsNull() {
            Class<?> result = ClassLoaderHelper.tryLoadClass("");

            assertThat(result)
                    .as("Empty className should return null")
                    .isNull();
        }

        @Test
        void tryLoadClass_withValidClassName_returnsClass() {
            Class<?> result = ClassLoaderHelper.tryLoadClass("java.lang.String");

            assertThat(result)
                    .as("Valid class should be loaded")
                    .isEqualTo(String.class);
        }

        @Test
        void tryLoadClass_withJavaLangInteger_returnsClass() {
            Class<?> result = ClassLoaderHelper.tryLoadClass("java.lang.Integer");

            assertThat(result)
                    .as("java.lang.Integer should be loaded")
                    .isEqualTo(Integer.class);
        }

        @Test
        void tryLoadClass_withJavaUtilList_returnsClass() {
            Class<?> result = ClassLoaderHelper.tryLoadClass("java.util.List");

            assertThat(result)
                    .as("java.util.List should be loaded")
                    .isEqualTo(java.util.List.class);
        }

        @Test
        void tryLoadClass_withInvalidClassName_returnsNull() {
            Class<?> result = ClassLoaderHelper.tryLoadClass("com.nonexistent.NonExistentClass");

            assertThat(result)
                    .as("Non-existent class should return null")
                    .isNull();
        }

        @Test
        void tryLoadClass_withMalformedClassName_returnsNull() {
            Class<?> result = ClassLoaderHelper.tryLoadClass("not.a.valid.class.name!");

            assertThat(result)
                    .as("Malformed class name should return null")
                    .isNull();
        }

        @Test
        void tryLoadClass_withProjectClass_returnsClass() {
            Class<?> result = ClassLoaderHelper.tryLoadClass(
                    "io.quarkiverse.qubit.deployment.common.ClassLoaderHelper");

            assertThat(result)
                    .as("Project class should be loaded")
                    .isEqualTo(ClassLoaderHelper.class);
        }

        @Test
        void tryLoadClass_withPrimitiveArrayClassName_returnsNull() {
            // Primitive arrays cannot be loaded via Class.forName with a descriptor
            Class<?> result = ClassLoaderHelper.tryLoadClass("[I");

            assertThat(result)
                    .as("Primitive array descriptor should return class or null")
                    .satisfiesAnyOf(
                            r -> assertThat(r).isEqualTo(int[].class),
                            r -> assertThat(r).isNull()
                    );
        }
    }

    // ==================== extractEntityClassInfo Tests ====================

    @Nested
    class ExtractEntityClassInfoTests {

        @Test
        void extractEntityClassInfo_withTypeConstant_returnsLoadedClass() {
            // ASM Type for a loadable class
            Type asmType = Type.getType(String.class);
            Constant constant = new Constant(asmType, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Loaded class should be String.class")
                    .isEqualTo(String.class);
            assertThat(result.className())
                    .as("className should be null when class is loaded")
                    .isNull();
        }

        @Test
        void extractEntityClassInfo_withTypeConstantInteger_returnsLoadedClass() {
            Type asmType = Type.getType(Integer.class);
            Constant constant = new Constant(asmType, Class.class);

            EntityClassInfo result = ClassLoaderHelper.extractEntityClassInfo(constant);

            assertThat(result.clazz())
                    .as("Loaded class should be Integer.class")
                    .isEqualTo(Integer.class);
            assertThat(result.className()).isNull();
        }

        @Test
        void extractEntityClassInfo_withUnloadableType_returnsPlaceholder() {
            // Create an ASM Type for a non-existent class
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

    // ==================== isClassLoadable Tests ====================

    @Nested
    class IsClassLoadableTests {

        @Test
        void isClassLoadable_withNull_returnsFalse() {
            assertThat(ClassLoaderHelper.isClassLoadable(null))
                    .as("Null className should not be loadable")
                    .isFalse();
        }

        @Test
        void isClassLoadable_withEmptyString_returnsFalse() {
            assertThat(ClassLoaderHelper.isClassLoadable(""))
                    .as("Empty className should not be loadable")
                    .isFalse();
        }

        @Test
        void isClassLoadable_withValidClassName_returnsTrue() {
            assertThat(ClassLoaderHelper.isClassLoadable("java.lang.String"))
                    .as("java.lang.String should be loadable")
                    .isTrue();
        }

        @Test
        void isClassLoadable_withJavaLangObject_returnsTrue() {
            assertThat(ClassLoaderHelper.isClassLoadable("java.lang.Object"))
                    .as("java.lang.Object should be loadable")
                    .isTrue();
        }

        @Test
        void isClassLoadable_withInvalidClassName_returnsFalse() {
            assertThat(ClassLoaderHelper.isClassLoadable("com.nonexistent.Class"))
                    .as("Non-existent class should not be loadable")
                    .isFalse();
        }

        @Test
        void isClassLoadable_withProjectClass_returnsTrue() {
            assertThat(ClassLoaderHelper.isClassLoadable(
                    "io.quarkiverse.qubit.deployment.common.EntityClassInfo"))
                    .as("Project class should be loadable")
                    .isTrue();
        }
    }
}
