package io.quarkiverse.qubit;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Strategy for finding captured variable fields in lambda instances.
 * Different Java compilers (javac, Eclipse, GraalVM) use different naming conventions.
 */
public interface FieldNamingStrategy {

    /** Returns field at index if found by this strategy's naming pattern. */
    Optional<Field> findCapturedField(Class<?> lambdaClass, int index);

    /** Strategy name for logging. */
    String getStrategyName();

    /** Standard javac strategy: arg$1, arg$2, ... */
    class JavacStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            try {
                String fieldName = "arg$" + (index + 1);
                Field field = lambdaClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException _) {
                return Optional.empty();
            }
        }

        @Override
        public String getStrategyName() {
            return "Javac (arg$N)";
        }
    }

    /** Eclipse JDT strategy: val$1, val$2, ... */
    class EclipseStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            try {
                String fieldName = "val$" + (index + 1);
                Field field = lambdaClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException _) {
                return Optional.empty();
            }
        }

        @Override
        public String getStrategyName() {
            return "Eclipse (val$N)";
        }
    }

    /** GraalVM native-image strategy: arg0, arg1, ... */
    class GraalVMStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            try {
                String fieldName = "arg" + index;
                Field field = lambdaClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException _) {
                return Optional.empty();
            }
        }

        @Override
        public String getStrategyName() {
            return "GraalVM (argN)";
        }
    }

    /** Disabled fallback - returns empty to prevent silent data corruption from field ordering. */
    class IndexBasedStrategy implements FieldNamingStrategy {
        @Override
        public Optional<Field> findCapturedField(Class<?> lambdaClass, int index) {
            return Optional.empty(); // Always fail - field ordering is non-deterministic
        }

        @Override
        public String getStrategyName() {
            return "Index-based fallback (DISABLED - unreliable field ordering)";
        }
    }
}
