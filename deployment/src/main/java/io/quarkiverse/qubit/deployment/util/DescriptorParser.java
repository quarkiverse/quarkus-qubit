package io.quarkiverse.qubit.deployment.util;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.NO_MORE_PARAMETERS;

import io.quarkus.logging.Log;

/**
 * Parses JVM method descriptors accounting for wide types (long/double take 2 slots).
 */
public final class DescriptorParser {

    private DescriptorParser() {
    }

    public static int countMethodArguments(String descriptor) {
        ParameterIterator iter = new ParameterIterator(descriptor);
        int count = 0;

        while (iter.hasNext()) {
            iter.next();
            count++;
        }

        return count;
    }

    /** Returns slot index of entity parameter (last parameter). */
    public static int calculateEntityParameterSlotIndex(String descriptor) {
        ParameterIterator iter = new ParameterIterator(descriptor);
        int lastParamSlot = 0;

        while (iter.hasNext()) {
            iter.next();
            lastParamSlot = iter.getCurrentParamSlotStart();
        }

        return lastParamSlot;
    }

    /** Returns slot indices for both entity parameters in bi-entity lambda. */
    public static int[] calculateBiEntityParameterSlotIndices(String descriptor) {
        int paramCount = countMethodArguments(descriptor);
        if (paramCount < 2) {
            return null;
        }

        ParameterIterator iter = new ParameterIterator(descriptor);
        int[] slots = new int[paramCount];
        int index = 0;

        while (iter.hasNext()) {
            iter.next();
            slots[index++] = iter.getCurrentParamSlotStart();
        }

        // Last two parameters are the entity parameters
        return new int[] { slots[paramCount - 2], slots[paramCount - 1] };
    }

    /** Returns slot index of first entity (second-to-last parameter). */
    public static int calculateFirstEntityParameterSlotIndex(String descriptor) {
        int[] slots = calculateBiEntityParameterSlotIndices(descriptor);
        return slots != null ? slots[0] : -1;
    }

    /** Returns slot index of second entity (last parameter). */
    public static int calculateSecondEntityParameterSlotIndex(String descriptor) {
        int[] slots = calculateBiEntityParameterSlotIndices(descriptor);
        return slots != null ? slots[1] : -1;
    }

    /** Converts JVM slot index to parameter index, or -1 if no match. */
    public static int slotIndexToParameterIndex(String descriptor, int slotIndex) {
        ParameterIterator iter = new ParameterIterator(descriptor);

        while (iter.hasNext()) {
            iter.next();
            if (iter.getCurrentParamSlotStart() == slotIndex) {
                return iter.getParamIndex();
            }
        }

        return -1;
    }

    /** Returns entity class name from descriptor (last parameter). Preferred over getEntityClass() at build time. */
    public static String getEntityClassName(String descriptor) {
        int paramCount = countMethodArguments(descriptor);
        if (paramCount == 0) {
            return "java.lang.Object";
        }
        return getParameterTypeName(descriptor, paramCount - 1);
    }

    /** Returns class name for parameter at index. */
    public static String getParameterTypeName(String descriptor, int paramIndex) {
        ParameterTypeInfo info = getParameterTypeInfo(descriptor, paramIndex);
        if (info == null) {
            return "java.lang.Object";
        }
        if (info.typeChar() == 'L') {
            return info.typeDescriptor().substring(1, info.typeDescriptor().length() - 1).replace('/', '.');
        }
        // Arrays ('[') and primitives are handled by primitiveCharToClass name
        return TypeConverter.primitiveCharToClass(info.typeChar()).getName();
    }

    /** Type info for a method parameter (internal). */
    private record ParameterTypeInfo(char typeChar, String typeDescriptor) {
    }

    /** Finds type info for parameter at index. */
    @org.jspecify.annotations.Nullable
    private static ParameterTypeInfo getParameterTypeInfo(String descriptor, int paramIndex) {
        ParameterIterator iter = new ParameterIterator(descriptor);

        while (iter.hasNext()) {
            iter.next();

            if (iter.getParamIndex() == paramIndex) {
                return new ParameterTypeInfo(iter.getTypeChar(), iter.getTypeDescriptor());
            }
        }

        return null;
    }

    /** Returns entity Class from descriptor (uses Class.forName(); prefer getEntityClassName() at build time). */
    public static Class<?> getEntityClass(String descriptor) {
        int paramCount = countMethodArguments(descriptor);
        if (paramCount == 0) {
            return Object.class;
        }
        // Entity is the last parameter
        return getParameterType(descriptor, paramCount - 1);
    }

    public static Class<?> getParameterType(String descriptor, int paramIndex) {
        ParameterTypeInfo info = getParameterTypeInfo(descriptor, paramIndex);
        if (info == null) {
            return Object.class;
        }

        if (info.typeChar() == 'L') {
            String className = info.typeDescriptor().substring(1, info.typeDescriptor().length() - 1).replace('/', '.');
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                Log.debugf("Could not load class %s for parameter %d in descriptor %s, using Object.class. Cause: %s",
                        className, paramIndex, descriptor, e.getMessage());
                return Object.class;
            }
        }
        // Arrays ('[') and primitives are handled by primitiveCharToClass.
        // Arrays return Object.class via the default case.
        return TypeConverter.primitiveCharToClass(info.typeChar());
    }

    // ========== Return Type Utilities ==========

    /** Extracts return type descriptor from method descriptor. */
    public static String getReturnTypeDescriptor(String methodDescriptor) {
        if (methodDescriptor == null) {
            return "";
        }
        int parenIndex = methodDescriptor.lastIndexOf(')');
        if (parenIndex < 0 || parenIndex >= methodDescriptor.length() - 1) {
            return "";
        }
        return methodDescriptor.substring(parenIndex + 1);
    }

    /** Checks if descriptor returns boolean (Z or Boolean). */
    public static boolean returnsBooleanType(String methodDescriptor) {
        String returnType = getReturnTypeDescriptor(methodDescriptor);
        return "Z".equals(returnType) || "Ljava/lang/Boolean;".equals(returnType);
    }

    /** Checks if descriptor returns int (useful for compareTo detection). */
    public static boolean returnsIntType(String methodDescriptor) {
        return "I".equals(getReturnTypeDescriptor(methodDescriptor));
    }

    /** Checks if descriptor returns specific class type. */
    public static boolean returnsType(String methodDescriptor, String classInternalName) {
        String returnType = getReturnTypeDescriptor(methodDescriptor);
        String expectedType = "L" + classInternalName + ";";
        return expectedType.equals(returnType);
    }

    /** Checks if return type contains class name (for generics/partial matching). */
    public static boolean returnTypeContains(String methodDescriptor, String classInternalName) {
        String returnType = getReturnTypeDescriptor(methodDescriptor);
        return returnType.contains(classInternalName);
    }

    /** Iterates method descriptor parameters accounting for wide types (long/double take 2 slots). */
    public static class ParameterIterator {
        private final String descriptor;
        private int position;
        private int paramIndex;
        private int slotIndex;
        private char currentTypeChar;
        private int currentTypeStart;
        private int currentTypeEnd;

        public ParameterIterator(String descriptor) {
            this.descriptor = descriptor;
            this.position = (descriptor != null && descriptor.startsWith("(")) ? 1 : 0;
            this.paramIndex = -1;
            this.slotIndex = 0;
            this.currentTypeChar = '\0';
        }

        public boolean hasNext() {
            return descriptor != null &&
                    position < descriptor.length() &&
                    descriptor.charAt(position) != ')';
        }

        public void next() {
            if (!hasNext()) {
                throw new IllegalStateException(NO_MORE_PARAMETERS);
            }

            paramIndex++;
            currentTypeStart = position;
            char c = descriptor.charAt(position);
            currentTypeChar = c;

            switch (c) {
                case 'L' -> advanceOverObjectReference();
                case '[' -> advanceOverArrayType();
                case 'J', 'D' -> advanceOverWideType();
                default -> advanceOverSingleSlotType();
            }
        }

        /** Advances over object reference type (Lclassname;). */
        private void advanceOverObjectReference() {
            skipToClassReferenceTerminator();
            position++; // Skip semicolon
            currentTypeEnd = position;
            slotIndex++;
        }

        /** Advances over array type ([...element). */
        private void advanceOverArrayType() {
            skipArrayDimensionBrackets();
            skipArrayElementType();
            currentTypeEnd = position;
            slotIndex++;
        }

        /** Advances over wide primitive type (long/double, 2 slots). */
        private void advanceOverWideType() {
            position++;
            currentTypeEnd = position;
            slotIndex += 2;
        }

        /** Advances over single-slot primitive type. */
        private void advanceOverSingleSlotType() {
            position++;
            currentTypeEnd = position;
            slotIndex++;
        }

        /** Skips all array dimension brackets (e.g., [[[ for 3D array). */
        private void skipArrayDimensionBrackets() {
            while (position < descriptor.length() && descriptor.charAt(position) == '[') {
                position++;
            }
        }

        /** Skips the array element type (object reference or primitive). */
        private void skipArrayElementType() {
            if (position < descriptor.length()) {
                if (descriptor.charAt(position) == 'L') {
                    skipToClassReferenceTerminator();
                    position++; // Skip semicolon
                } else {
                    position++; // Primitive element type
                }
            }
        }

        /** Advances position to the class reference terminator (;). */
        private void skipToClassReferenceTerminator() {
            while (isNotAtClassReferenceTerminator()) {
                position++;
            }
        }

        public int getParamIndex() {
            return paramIndex;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public int getCurrentParamSlotStart() {
            char c = descriptor.charAt(currentTypeStart);
            if (c == 'J' || c == 'D') {
                return slotIndex - 2;
            } else {
                return slotIndex - 1;
            }
        }

        public char getTypeChar() {
            return currentTypeChar;
        }

        public String getTypeDescriptor() {
            return descriptor.substring(currentTypeStart, currentTypeEnd);
        }

        private boolean isNotAtClassReferenceTerminator() {
            return position < descriptor.length() && descriptor.charAt(position) != ';';
        }
    }
}
