package io.quarkus.qusaq.deployment.util;

import org.jboss.logging.Logger;

/**
 * Parses JVM method descriptors accounting for wide types (long/double take 2 slots).
 */
public final class DescriptorParser {

    private static final Logger log = Logger.getLogger(DescriptorParser.class);

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
     * Returns slot index of entity parameter.
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
     * Converts JVM slot index to parameter index.
     */
    public static int slotIndexToParameterIndex(String descriptor, int slotIndex) {
        ParameterIterator iter = new ParameterIterator(descriptor);

        while (iter.hasNext()) {
            if (iter.getSlotIndex() == slotIndex) {
                return iter.getParamIndex() + 1;
            }

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
                        log.debugf("Could not load class %s, using Object.class", className);
                        return Object.class;
                    }
                } else if (c == '[') {
                    return Object.class;
                } else {
                    return TypeConverter.primitiveCharToClass(c);
                }
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
                position++;
                if (position < descriptor.length() && descriptor.charAt(position) == 'L') {
                    while (isNotAtClassReferenceTerminator()) {
                        position++;
                    }
                    position++;
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
