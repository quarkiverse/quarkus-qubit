package io.quarkiverse.qubit.deployment.util;

import io.quarkus.logging.Log;

/**
 * Parses JVM method descriptors accounting for wide types (long/double take 2 slots).
 */
public final class DescriptorParser {

    private DescriptorParser() {
    }

    /**
     * Returns number of parameters in method descriptor.
     */
    public static int countMethodArguments(String descriptor) {
        ParameterIterator iter = new ParameterIterator(descriptor);
        int count = 0;

        while (iter.hasNext()) {
            iter.next();
            count++;
        }

        return count;
    }

    /**
     * Returns slot index of entity parameter (last parameter in descriptor).
     */
    public static int calculateEntityParameterSlotIndex(String descriptor) {
        ParameterIterator iter = new ParameterIterator(descriptor);
        int lastParamSlot = 0;

        while (iter.hasNext()) {
            iter.next();
            lastParamSlot = iter.getCurrentParamSlotStart();
        }

        return lastParamSlot;
    }

    /**
     * Returns slot indices for both entity parameters in a bi-entity lambda.
     * <p>
     * For BiQuerySpec like {@code (Person p, Phone ph) -> ...}, this returns
     * the slot indices of both the first and second entity parameters.
     *
     * @param descriptor the method descriptor (e.g., "(LPerson;LPhone;)Z")
     * @return array of [firstEntitySlot, secondEntitySlot], or null if less than 2 params
     */
    public static int [] calculateBiEntityParameterSlotIndices(String descriptor) {
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

    /**
     * Returns the slot index of the first entity parameter (second-to-last parameter).
     * <p>
     * For BiQuerySpec like {@code (Person p, Phone ph) -> ...}, returns slot of 'p'.
     *
     * @param descriptor the method descriptor
     * @return slot index of first entity, or -1 if less than 2 parameters
     */
    public static int calculateFirstEntityParameterSlotIndex(String descriptor) {
        int[] slots = calculateBiEntityParameterSlotIndices(descriptor);
        return slots != null ? slots[0] : -1;
    }

    /**
     * Returns the slot index of the second entity parameter (last parameter).
     * <p>
     * For BiQuerySpec like {@code (Person p, Phone ph) -> ...}, returns slot of 'ph'.
     * Same as calculateEntityParameterSlotIndex() but more explicit for bi-entity context.
     *
     * @param descriptor the method descriptor
     * @return slot index of second entity, or -1 if less than 2 parameters
     */
    public static int calculateSecondEntityParameterSlotIndex(String descriptor) {
        int[] slots = calculateBiEntityParameterSlotIndices(descriptor);
        return slots != null ? slots[1] : -1;
    }

    /**
     * Converts JVM slot index to parameter index.
     * Returns the parameter index whose starting slot matches the given slot index,
     * or -1 if no parameter starts at that slot.
     */
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

    /**
     * Returns Class for parameter at index.
     */
    public static Class<?> getParameterType(String descriptor, int paramIndex) {
        ParameterIterator iter = new ParameterIterator(descriptor);

        while (iter.hasNext()) {
            iter.next();

            if (iter.getParamIndex() == paramIndex) {
                char c = iter.getTypeChar();

                if (c == 'L') {
                    String typeDescriptor = iter.getTypeDescriptor();
                    String className = typeDescriptor.substring(1, typeDescriptor.length() - 1).replace('/', '.');
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        Log.debugf("Could not load class %s, using Object.class", className);
                        return Object.class;
                    }
                }
                // Arrays ('[') and primitives are handled by primitiveCharToClass.
                // Arrays return Object.class via the default case.
                return TypeConverter.primitiveCharToClass(c);
            }
        }

        return Object.class;
    }

    /**
     * Iterates method descriptor parameters accounting for wide types.
     */
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

        /**
         * Returns true if more parameters exist.
         */
        public boolean hasNext() {
            return descriptor != null &&
                   position < descriptor.length() &&
                   descriptor.charAt(position) != ')';
        }

        /**
         * Advances to next parameter.
         */
        public void next() {
            if (!hasNext()) {
                throw new IllegalStateException("No more parameters");
            }

            paramIndex++;
            currentTypeStart = position;
            char c = descriptor.charAt(position);
            currentTypeChar = c;

            if (c == 'L') {
                while (isNotAtClassReferenceTerminator()) {
                    position++;
                }
                position++;
                currentTypeEnd = position;
                slotIndex++;
            } else if (c == '[') {
                // Skip all array dimension brackets (e.g., [[[ for 3D array)
                while (position < descriptor.length() && descriptor.charAt(position) == '[') {
                    position++;
                }
                // Now skip the element type
                if (position < descriptor.length()) {
                    char elementType = descriptor.charAt(position);
                    if (elementType == 'L') {
                        // Object array: skip to semicolon
                        while (isNotAtClassReferenceTerminator()) {
                            position++;
                        }
                        position++; // Skip the semicolon
                    } else {
                        // Primitive array: skip the single type character
                        position++;
                    }
                }
                currentTypeEnd = position;
                slotIndex++;
            } else if (c == 'J' || c == 'D') {
                position++;
                currentTypeEnd = position;
                slotIndex += 2;
            } else {
                position++;
                currentTypeEnd = position;
                slotIndex++;
            }
        }

        /**
         * Returns current parameter index.
         */
        public int getParamIndex() {
            return paramIndex;
        }

        /**
         * Returns slot index after current parameter.
         */
        public int getSlotIndex() {
            return slotIndex;
        }

        /**
         * Returns starting slot of current parameter.
         */
        public int getCurrentParamSlotStart() {
            char c = descriptor.charAt(currentTypeStart);
            if (c == 'J' || c == 'D') {
                return slotIndex - 2;
            } else {
                return slotIndex - 1;
            }
        }

        /**
         * Returns type descriptor character.
         */
        public char getTypeChar() {
            return currentTypeChar;
        }

        /**
         * Returns full type descriptor string.
         */
        public String getTypeDescriptor() {
            return descriptor.substring(currentTypeStart, currentTypeEnd);
        }

        /**
         * Returns true if not at the terminator (;) of a class reference descriptor.
         */
        private boolean isNotAtClassReferenceTerminator() {
            return position < descriptor.length() && descriptor.charAt(position) != ';';
        }
    }
}
