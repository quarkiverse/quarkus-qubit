package io.quarkiverse.qubit.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

/**
 * Strategy for finding captured variable fields in lambda instances.
 * Different Java compilers (javac, Eclipse, GraalVM) use different naming conventions.
 */
public interface FieldNamingStrategy {

    /**
     * Attempts to find the captured variable field at the specified index.
     *
     * @param lambdaClass the lambda class to search
     * @param index the zero-based index of the captured variable
     * @return Optional containing the field if found, empty otherwise
     */
    Optional<Field> findCapturedField(Class<?> lambdaClass, int index);

    /**
     * Returns the name of this strategy for logging purposes.
     */
    String getStrategyName();

    /**
     * Standard javac strategy: arg$1, arg$2, arg$3, ...
     * Used by Oracle/OpenJDK javac (JDK 11-21+).
     */
    class JavacStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            try {
                String fieldName = "arg$" + (index + 1);
                Field field = lambdaClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException e) {
                return Optional.empty();
            }
        }

        @Override
        public String getStrategyName() {
            return "Javac (arg$N)";
        }
    }

    /**
     * Eclipse compiler strategy: val$1, val$2, val$3, ...
     * Used by Eclipse JDT compiler.
     */
    class EclipseStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            try {
                String fieldName = "val$" + (index + 1);
                Field field = lambdaClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException e) {
                return Optional.empty();
            }
        }

        @Override
        public String getStrategyName() {
            return "Eclipse (val$N)";
        }
    }

    /**
     * GraalVM native-image strategy: arg0, arg1, arg2, ...
     * Used by GraalVM native-image compiler.
     */
    class GraalVMStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            try {
                String fieldName = "arg" + index;
                Field field = lambdaClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException e) {
                return Optional.empty();
            }
        }

        @Override
        public String getStrategyName() {
            return "GraalVM (argN)";
        }
    }

    /**
     * Fallback strategy: iterate all declared fields and select by index.
     * This is a last-resort strategy when naming conventions are unknown.
     */
    class IndexBasedStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            Field[] allFields = lambdaClass.getDeclaredFields();
            if (index < 0 || index >= allFields.length) {
                return Optional.empty();
            }

            // Filter out synthetic or static fields (lambdas shouldn't have static fields)
            Field[] instanceFields = Arrays.stream(allFields)
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .toArray(Field[]::new);

            if (index >= instanceFields.length) {
                return Optional.empty();
            }

            Field field = instanceFields[index];
            field.setAccessible(true);
            return Optional.of(field);
        }

        @Override
        public String getStrategyName() {
            return "Index-based fallback";
        }
    }
}
