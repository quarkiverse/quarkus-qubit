package io.quarkiverse.qubit.deployment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DescriptorParser}.
 * <p>
 * Tests JVM method descriptor parsing with special attention to:
 * <ul>
 *   <li>Wide types (long/double) that occupy 2 slots instead of 1</li>
 *   <li>Object references and arrays</li>
 *   <li>Mixed parameter combinations</li>
 *   <li>Bi-entity and group query descriptors</li>
 * </ul>
 * <p>
 * JVM Type Descriptors:
 * <ul>
 *   <li>B = byte, C = char, D = double, F = float</li>
 *   <li>I = int, J = long, S = short, Z = boolean</li>
 *   <li>L...;= reference type, [ = array</li>
 * </ul>
 */
class DescriptorParserTest {

    // ========== Wide Type Tests (long/double take 2 slots) ==========

    @Nested
    @DisplayName("Wide Type Handling (long/double take 2 slots)")
    class WideTypeTests {

        @Test
        @DisplayName("Long parameter occupies 2 slots")
        void calculateEntityParameterSlotIndex_withWideLongParameter_returnsCorrectSlot() {
            // Test: (long id, String name) -> descriptor: "(JLjava/lang/String;)V"
            // long takes 2 slots (0-1), String at slot 2
            String descriptor = "(JLjava/lang/String;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(2); // String is at slot 2, not slot 1
        }

        @Test
        @DisplayName("Double parameter occupies 2 slots")
        void calculateEntityParameterSlotIndex_withWideDoubleParameter_returnsCorrectSlot() {
            // Test: (double score, String name) -> descriptor: "(DLjava/lang/String;)V"
            String descriptor = "(DLjava/lang/String;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(2);
        }

        @Test
        @DisplayName("Multiple wide types accumulate correctly")
        void calculateEntityParameterSlotIndex_withMultipleWideTypes_returnsCorrectSlot() {
            // Test: (long id, double score, Person p) -> descriptor: "(JDLPerson;)V"
            // long: slots 0-1 (2 slots), double: slots 2-3 (2 slots), Person: slot 4
            String descriptor = "(JDLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(4);
        }

        @Test
        @DisplayName("Wide type at end occupies 2 slots")
        void calculateEntityParameterSlotIndex_withWideTypeAtEnd_returnsCorrectSlot() {
            // Test: (int a, long result) -> descriptor: "(IJ)V"
            // int: slot 0, long: slots 1-2
            String descriptor = "(IJ)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1); // long starts at slot 1
        }
    }

    // ========== Bi-Entity Parameter Tests ==========

    @Nested
    @DisplayName("Bi-Entity Parameter Slot Calculation")
    class BiEntityTests {

        @Test
        @DisplayName("Mixed wide types before bi-entity parameters")
        void calculateBiEntityParameterSlotIndices_withMixedWideTypes_returnsCorrectSlots() {
            // Test: (long id, double score, Person p, Phone ph) -> descriptor: "(JDLPerson;LPhone;)V"
            // long: slots 0-1, double: slots 2-3, Person: slot 4, Phone: slot 5
            String descriptor = "(JDLPerson;LPhone;)V";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).containsExactly(4, 5); // Person at slot 4, Phone at slot 5
        }

        @Test
        @DisplayName("Simple bi-entity without captured variables")
        void calculateBiEntityParameterSlotIndices_simpleCase_returnsCorrectSlots() {
            // Test: (Person p, Phone ph) -> descriptor: "(LPerson;LPhone;)Z"
            String descriptor = "(LPerson;LPhone;)Z";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).containsExactly(0, 1);
        }

        @Test
        @DisplayName("Bi-entity with primitive captured variable")
        void calculateBiEntityParameterSlotIndices_withCapturedInt_returnsCorrectSlots() {
            // Test: (int minAge, Person p, Phone ph) -> descriptor: "(ILPerson;LPhone;)Z"
            String descriptor = "(ILPerson;LPhone;)Z";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).containsExactly(1, 2); // Person at slot 1, Phone at slot 2
        }

        @Test
        @DisplayName("Single parameter returns null")
        void calculateBiEntityParameterSlotIndices_singleParam_returnsNull() {
            String descriptor = "(LPerson;)Z";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).isNull();
        }

        @Test
        @DisplayName("First entity slot index calculation")
        void calculateFirstEntityParameterSlotIndex_withCapturedVariables_returnsCorrectSlot() {
            // (int a, long b, Person p, Phone ph) -> int:0, long:1-2, Person:3, Phone:4
            String descriptor = "(IJLPerson;LPhone;)Z";
            int slot = DescriptorParser.calculateFirstEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(3);
        }

        @Test
        @DisplayName("Second entity slot index calculation")
        void calculateSecondEntityParameterSlotIndex_withCapturedVariables_returnsCorrectSlot() {
            // (int a, long b, Person p, Phone ph) -> int:0, long:1-2, Person:3, Phone:4
            String descriptor = "(IJLPerson;LPhone;)Z";
            int slot = DescriptorParser.calculateSecondEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(4);
        }
    }

    // ========== Primitive Type Tests ==========

    @Nested
    @DisplayName("Primitive Type Handling")
    class PrimitiveTypeTests {

        @Test
        @DisplayName("All primitives occupy 1 slot (except long/double)")
        void calculateEntityParameterSlotIndex_withOnlyPrimitives_returnsCorrectSlot() {
            // Test: (int a, float b, boolean c, Person p) -> descriptor: "(IFZLPerson;)V"
            String descriptor = "(IFZLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(3); // Each primitive takes 1 slot
        }

        @Test
        @DisplayName("All single-slot primitives")
        void calculateEntityParameterSlotIndex_allSingleSlotPrimitives_returnsCorrectSlot() {
            // (byte, char, short, int, float, boolean, Person)
            // B=byte, C=char, S=short, I=int, F=float, Z=boolean
            String descriptor = "(BCSIFZLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(6); // 6 single-slot primitives
        }

        @Test
        @DisplayName("Mixed primitives and wide types")
        void calculateEntityParameterSlotIndex_mixedPrimitivesAndWideTypes_returnsCorrectSlot() {
            // (int, long, float, double, boolean, Person) -> I, J, F, D, Z, L
            // int:0, long:1-2, float:3, double:4-5, boolean:6, Person:7
            String descriptor = "(IJFDZLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(7);
        }
    }

    // ========== Parameter Iterator Tests ==========

    @Nested
    @DisplayName("ParameterIterator Functionality")
    class ParameterIteratorTests {

        @Test
        @DisplayName("Iterates captured variables before entity")
        void parameterIterator_handlesCapturedVariablesBeforeEntity() {
            // Lambda with captured variables before entity parameter
            // Descriptor: "(Ljava/lang/String;ILPerson;)V" - captured String, captured int, entity Person
            String descriptor = "(Ljava/lang/String;ILPerson;)V";

            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            List<Integer> slots = new ArrayList<>();

            while (iter.hasNext()) {
                iter.next();
                slots.add(iter.getCurrentParamSlotStart());
            }

            assertThat(slots).containsExactly(0, 1, 2); // String:0, int:1, Person:2
        }

        @Test
        @DisplayName("Handles Group query descriptor")
        void parameterIterator_handlesGroupQueryDescriptor() {
            // GroupQuerySpec descriptor: (Group<Person, String>)
            // Descriptor: "(LGroup;)J" - Group parameter returns long
            String descriptor = "(LGroup;)J";

            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(0);
        }

        @Test
        @DisplayName("Tracks parameter index correctly")
        void parameterIterator_tracksParamIndex() {
            String descriptor = "(IJDLPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<Integer> indices = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                indices.add(iter.getParamIndex());
            }

            assertThat(indices).containsExactly(0, 1, 2, 3);
        }

        @Test
        @DisplayName("Returns correct type descriptors")
        void parameterIterator_returnsCorrectTypeDescriptors() {
            String descriptor = "(ILjava/lang/String;D)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<String> typeDescriptors = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeDescriptors.add(iter.getTypeDescriptor());
            }

            assertThat(typeDescriptors).containsExactly("I", "Ljava/lang/String;", "D");
        }

        @Test
        @DisplayName("Returns correct type characters")
        void parameterIterator_returnsCorrectTypeChars() {
            String descriptor = "(IJDFLPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<Character> typeChars = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeChars.add(iter.getTypeChar());
            }

            assertThat(typeChars).containsExactly('I', 'J', 'D', 'F', 'L');
        }

        @Test
        @DisplayName("Throws exception when no more parameters")
        void parameterIterator_throwsWhenExhausted() {
            String descriptor = "(I)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // consume the only parameter

            assertThatThrownBy(iter::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more parameters");
        }

        @Test
        @DisplayName("getSlotIndex returns non-zero after advancing past regular type")
        void parameterIterator_getSlotIndex_afterRegularType_returnsNonZero() {
            String descriptor = "(IJ)V"; // int takes slot 0, long takes slots 1-2
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // move to first param (int)
            assertThat(iter.getSlotIndex())
                    .as("After int parameter, slotIndex should be 1 (not 0)")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("getSlotIndex returns correct value after wide type")
        void parameterIterator_getSlotIndex_afterWideType_returnsNonZero() {
            String descriptor = "(JI)V"; // long takes slots 0-1, int takes slot 2
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // move to first param (long)
            assertThat(iter.getSlotIndex())
                    .as("After long parameter, slotIndex should be 2")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("getCurrentParamSlotStart returns correct slot for long parameter")
        void parameterIterator_getCurrentParamSlotStart_longParam_returnsCorrectSlot() {
            String descriptor = "(IJ)V"; // int:0, long:1-2
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // int
            iter.next(); // long
            assertThat(iter.getCurrentParamSlotStart())
                    .as("Long parameter starting at slot 1 should return 1")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("getCurrentParamSlotStart returns correct slot for double parameter")
        void parameterIterator_getCurrentParamSlotStart_doubleParam_returnsCorrectSlot() {
            String descriptor = "(ID)V"; // int:0, double:1-2
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // int
            iter.next(); // double
            assertThat(iter.getCurrentParamSlotStart())
                    .as("Double parameter starting at slot 1 should return 1")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("getCurrentParamSlotStart returns slotIndex-1 for regular types")
        void parameterIterator_getCurrentParamSlotStart_regularType_returnsSlotMinusOne() {
            String descriptor = "(JI)V"; // long:0-1, int:2
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // long
            iter.next(); // int (regular type)
            assertThat(iter.getCurrentParamSlotStart())
                    .as("Int parameter after long should start at slot 2")
                    .isEqualTo(2);
        }
    }

    // ========== Array Type Tests ==========

    @Nested
    @DisplayName("Array Type Handling")
    class ArrayTypeTests {

        // --- calculateEntityParameterSlotIndex with arrays ---

        @Test
        @DisplayName("Primitive array occupies 1 slot")
        void calculateEntityParameterSlotIndex_withPrimitiveArray_returnsCorrectSlot() {
            // (int[], Person) -> "[I" for int array
            String descriptor = "([ILPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("Object array occupies 1 slot")
        void calculateEntityParameterSlotIndex_withObjectArray_returnsCorrectSlot() {
            // (String[], Person) -> "[Ljava/lang/String;"
            String descriptor = "([Ljava/lang/String;LPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("Multi-dimensional array occupies 1 slot")
        void calculateEntityParameterSlotIndex_withMultiDimArray_returnsCorrectSlot() {
            // (int[][], Person) -> "[[I"
            String descriptor = "([[ILPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("3D array occupies 1 slot")
        void calculateEntityParameterSlotIndex_with3DArray_returnsCorrectSlot() {
            // (int[][][], Person) -> "[[[I"
            String descriptor = "([[[ILPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("Long array occupies 1 slot (not 2 like long primitive)")
        void calculateEntityParameterSlotIndex_withLongArray_returnsCorrectSlot() {
            // (long[], Person) -> "[J"
            String descriptor = "([JLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1); // Array of long is still 1 slot, not 2
        }

        @Test
        @DisplayName("Double array occupies 1 slot (not 2 like double primitive)")
        void calculateEntityParameterSlotIndex_withDoubleArray_returnsCorrectSlot() {
            // (double[], Person) -> "[D"
            String descriptor = "([DLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1); // Array of double is still 1 slot, not 2
        }

        @Test
        @DisplayName("Multi-dimensional object array occupies 1 slot")
        void calculateEntityParameterSlotIndex_with2DObjectArray_returnsCorrectSlot() {
            // (String[][], Person) -> "[[Ljava/lang/String;"
            String descriptor = "([[Ljava/lang/String;LPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("Multiple arrays before entity")
        void calculateEntityParameterSlotIndex_withMultipleArrays_returnsCorrectSlot() {
            // (int[], String[], Person) -> "[I", "[Ljava/lang/String;", "LPerson;"
            String descriptor = "([I[Ljava/lang/String;LPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(2); // int[]:0, String[]:1, Person:2
        }

        @Test
        @DisplayName("Array mixed with wide types")
        void calculateEntityParameterSlotIndex_withArrayAndWideTypes_returnsCorrectSlot() {
            // (int[], long, double[], Person) -> "[I", "J", "[D", "LPerson;"
            // int[]:0, long:1-2, double[]:3, Person:4
            String descriptor = "([IJ[DLPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(4);
        }

        // --- countMethodArguments with arrays ---

        @Test
        @DisplayName("Counts primitive array as 1 parameter")
        void countMethodArguments_withPrimitiveArray_countsAsOne() {
            // (int[], String) -> 2 parameters
            assertThat(DescriptorParser.countMethodArguments("([ILjava/lang/String;)V")).isEqualTo(2);
        }

        @Test
        @DisplayName("Counts multi-dimensional array as 1 parameter")
        void countMethodArguments_withMultiDimArray_countsAsOne() {
            // (int[][]) -> 1 parameter
            assertThat(DescriptorParser.countMethodArguments("([[I)V")).isEqualTo(1);
        }

        @Test
        @DisplayName("Counts multiple arrays correctly")
        void countMethodArguments_withMultipleArrays_countsCorrectly() {
            // (int[], String[], double[][]) -> 3 parameters
            assertThat(DescriptorParser.countMethodArguments("([I[Ljava/lang/String;[[D)V")).isEqualTo(3);
        }

        @Test
        @DisplayName("Counts arrays mixed with primitives correctly")
        void countMethodArguments_withArraysAndPrimitives_countsCorrectly() {
            // (int, int[], long, String[]) -> 4 parameters
            assertThat(DescriptorParser.countMethodArguments("(I[IJ[Ljava/lang/String;)V")).isEqualTo(4);
        }

        // --- slotIndexToParameterIndex with arrays ---

        @Test
        @DisplayName("Slot to param index with primitive array")
        void slotIndexToParameterIndex_withPrimitiveArray_returnsCorrectIndex() {
            // (int[], String) -> int[]:slot0, String:slot1
            String descriptor = "([ILjava/lang/String;)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0)).isEqualTo(0);
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 1)).isEqualTo(1);
        }

        @Test
        @DisplayName("Slot to param index with multi-dim array")
        void slotIndexToParameterIndex_withMultiDimArray_returnsCorrectIndex() {
            // (int[][], String) -> int[][]:slot0, String:slot1
            String descriptor = "([[ILjava/lang/String;)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0)).isEqualTo(0);
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 1)).isEqualTo(1);
        }

        @Test
        @DisplayName("Slot to param index with array after wide type")
        void slotIndexToParameterIndex_withArrayAfterWide_returnsCorrectIndex() {
            // (long, int[], String) -> long:slots0-1, int[]:slot2, String:slot3
            String descriptor = "(J[ILjava/lang/String;)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0)).isEqualTo(0);
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 2)).isEqualTo(1);
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 3)).isEqualTo(2);
        }

        // --- getParameterType with arrays ---

        @Test
        @DisplayName("Get parameter type after primitive array")
        void getParameterType_afterPrimitiveArray_returnsCorrectType() {
            // (int[], String) -> String is param index 1
            String descriptor = "([ILjava/lang/String;)V";
            assertThat(DescriptorParser.getParameterType(descriptor, 1)).isEqualTo(String.class);
        }

        @Test
        @DisplayName("Get parameter type for array returns Object")
        void getParameterType_forArray_returnsObject() {
            // (int[]) -> array is param index 0, returns Object.class
            String descriptor = "([I)V";
            assertThat(DescriptorParser.getParameterType(descriptor, 0)).isEqualTo(Object.class);
        }

        @Test
        @DisplayName("Get parameter type after multi-dim array")
        void getParameterType_afterMultiDimArray_returnsCorrectType() {
            // (int[][], long, String) -> String is param index 2
            String descriptor = "([[IJLjava/lang/String;)V";
            assertThat(DescriptorParser.getParameterType(descriptor, 0)).isEqualTo(Object.class); // array
            assertThat(DescriptorParser.getParameterType(descriptor, 1)).isEqualTo(long.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 2)).isEqualTo(String.class);
        }

        // --- calculateBiEntityParameterSlotIndices with arrays ---

        @Test
        @DisplayName("Bi-entity with array captured variable")
        void calculateBiEntityParameterSlotIndices_withArrayCaptured_returnsCorrectSlots() {
            // (int[], Person, Phone) -> int[]:0, Person:1, Phone:2
            String descriptor = "([ILPerson;LPhone;)Z";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).containsExactly(1, 2);
        }

        @Test
        @DisplayName("Bi-entity with multi-dim array captured variable")
        void calculateBiEntityParameterSlotIndices_withMultiDimArrayCaptured_returnsCorrectSlots() {
            // (int[][], Person, Phone) -> int[][]:0, Person:1, Phone:2
            String descriptor = "([[ILPerson;LPhone;)Z";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).containsExactly(1, 2);
        }

        @Test
        @DisplayName("Bi-entity with multiple array captured variables")
        void calculateBiEntityParameterSlotIndices_withMultipleArraysCaptured_returnsCorrectSlots() {
            // (int[], String[], Person, Phone) -> int[]:0, String[]:1, Person:2, Phone:3
            String descriptor = "([I[Ljava/lang/String;LPerson;LPhone;)Z";
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
            assertThat(slots).containsExactly(2, 3);
        }

        // --- ParameterIterator with arrays ---

        @Test
        @DisplayName("Iterator returns correct type descriptor for primitive array")
        void parameterIterator_withPrimitiveArray_returnsCorrectTypeDescriptor() {
            String descriptor = "([ILPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<String> typeDescriptors = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeDescriptors.add(iter.getTypeDescriptor());
            }

            assertThat(typeDescriptors).containsExactly("[I", "LPerson;");
        }

        @Test
        @DisplayName("Iterator returns correct type descriptor for multi-dim array")
        void parameterIterator_withMultiDimArray_returnsCorrectTypeDescriptor() {
            String descriptor = "([[DLPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<String> typeDescriptors = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeDescriptors.add(iter.getTypeDescriptor());
            }

            assertThat(typeDescriptors).containsExactly("[[D", "LPerson;");
        }

        @Test
        @DisplayName("Iterator returns correct type descriptor for object array")
        void parameterIterator_withObjectArray_returnsCorrectTypeDescriptor() {
            String descriptor = "([Ljava/lang/String;LPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<String> typeDescriptors = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeDescriptors.add(iter.getTypeDescriptor());
            }

            assertThat(typeDescriptors).containsExactly("[Ljava/lang/String;", "LPerson;");
        }

        @Test
        @DisplayName("Iterator returns '[' as type char for arrays")
        void parameterIterator_withArray_returnsArrayTypeChar() {
            String descriptor = "([I[[DLPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<Character> typeChars = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeChars.add(iter.getTypeChar());
            }

            assertThat(typeChars).containsExactly('[', '[', 'L');
        }

        @Test
        @DisplayName("Iterator tracks correct slots with arrays")
        void parameterIterator_withArrays_tracksCorrectSlots() {
            // (int[], long, double[][], String) -> int[]:0, long:1-2, double[][]:3, String:4
            String descriptor = "([IJ[[DLjava/lang/String;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<Integer> slots = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                slots.add(iter.getCurrentParamSlotStart());
            }

            assertThat(slots).containsExactly(0, 1, 3, 4);
        }

        @Test
        @DisplayName("Iterator tracks correct param indices with arrays")
        void parameterIterator_withArrays_tracksCorrectParamIndices() {
            // (int[], long, double[][], String) -> 4 params, indices 0,1,2,3
            String descriptor = "([IJ[[DLjava/lang/String;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            List<Integer> indices = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                indices.add(iter.getParamIndex());
            }

            assertThat(indices).containsExactly(0, 1, 2, 3);
        }
    }

    // ========== Method Argument Count Tests ==========

    @Nested
    @DisplayName("Method Argument Counting")
    class CountMethodArgumentsTests {

        @Test
        @DisplayName("Counts simple parameters")
        void countMethodArguments_simpleParams_returnsCorrectCount() {
            assertThat(DescriptorParser.countMethodArguments("(LPerson;)Z")).isEqualTo(1);
            assertThat(DescriptorParser.countMethodArguments("(LPerson;LPhone;)Z")).isEqualTo(2);
            assertThat(DescriptorParser.countMethodArguments("(IJDLPerson;)Z")).isEqualTo(4);
        }

        @Test
        @DisplayName("Counts zero parameters")
        void countMethodArguments_noParams_returnsZero() {
            assertThat(DescriptorParser.countMethodArguments("()V")).isZero();
        }

        @Test
        @DisplayName("Wide types count as single parameters")
        void countMethodArguments_wideTypes_countsAsOneEach() {
            // long and double are 2 slots but 1 parameter each
            assertThat(DescriptorParser.countMethodArguments("(JD)V")).isEqualTo(2);
            assertThat(DescriptorParser.countMethodArguments("(JJDD)V")).isEqualTo(4);
        }
    }

    // ========== Slot-to-Parameter Index Conversion Tests ==========

    @Nested
    @DisplayName("Slot to Parameter Index Conversion")
    class SlotToParameterIndexTests {

        @Test
        @DisplayName("Converts slot index with wide types")
        void slotIndexToParameterIndex_withWideTypes_returnsCorrectIndex() {
            // (int, long, Person) -> int:slot0, long:slots1-2, Person:slot3
            String descriptor = "(IJLPerson;)V";

            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0)).isEqualTo(0);
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 1)).isEqualTo(1);
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 3)).isEqualTo(2);
        }

        @Test
        @DisplayName("Returns -1 for invalid slot")
        void slotIndexToParameterIndex_invalidSlot_returnsNegativeOne() {
            String descriptor = "(I)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 99)).isEqualTo(-1);
        }
    }

    // ========== Slot Index Edge Cases ==========

    @Nested
    @DisplayName("slotIndexToParameterIndex Edge Cases")
    class SlotIndexToParameterIndexEdgeCases {

        @Test
        @DisplayName("Returns -1 for slot 0 when parameters exist")
        void slotIndexToParameterIndex_slot0WithParams_returnsNegativeOneOrCorrect() {
            // (int) -> slot 0 should be param 0
            String descriptor = "(I)V";
            // getSlotIndex() before next() is 0, but getSlotIndex matches slotIndex
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0))
                    .isEqualTo(0);  // or 1 depending on logic
        }

        @Test
        @DisplayName("Returns param index for first slot of long parameter")
        void slotIndexToParameterIndex_longFirstSlot_returnsCorrectIndex() {
            // (long) -> slot 0 is first slot of long (which takes 0-1)
            String descriptor = "(J)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("Returns -1 for second slot of long parameter")
        void slotIndexToParameterIndex_longSecondSlot_returnsNegativeOne() {
            // (long) -> slot 1 is second slot of long
            String descriptor = "(J)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 1))
                    .isEqualTo(-1);  // slot 1 doesn't start a param
        }

        @Test
        @DisplayName("Returns param index for first slot of double parameter")
        void slotIndexToParameterIndex_doubleFirstSlot_returnsCorrectIndex() {
            String descriptor = "(D)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("Returns correct index for slot after wide type")
        void slotIndexToParameterIndex_slotAfterWide_returnsCorrectIndex() {
            // (long, int) -> long:0-1, int:2
            String descriptor = "(JI)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 2))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Returns correct index for array parameter")
        void slotIndexToParameterIndex_arrayParam_returnsCorrectIndex() {
            // (int[], String) -> int[]:0, String:1
            String descriptor = "([ILjava/lang/String;)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 0))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("Returns -1 for very large slot index")
        void slotIndexToParameterIndex_veryLargeSlot_returnsNegativeOne() {
            String descriptor = "(I)V";
            assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, 1000))
                    .isEqualTo(-1);
        }
    }

    // ========== Edge Cases ==========

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles fully qualified class names")
        void calculateEntityParameterSlotIndex_fullyQualifiedClassName_returnsCorrectSlot() {
            String descriptor = "(Ljava/lang/String;Lio/quarkiverse/qubit/runtime/Person;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("Handles inner class references")
        void calculateEntityParameterSlotIndex_innerClass_returnsCorrectSlot() {
            // Inner class uses $ in descriptor
            String descriptor = "(LOuterClass$InnerClass;LPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(1);
        }

        @Test
        @DisplayName("Single parameter returns slot 0")
        void calculateEntityParameterSlotIndex_singleParam_returnsZero() {
            String descriptor = "(LPerson;)V";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isZero();
        }

        @Test
        @DisplayName("Handles complex real-world descriptor")
        void calculateEntityParameterSlotIndex_realWorldComplexDescriptor_returnsCorrectSlot() {
            // Simulating: (String captured1, int captured2, long captured3, Person entity)
            String descriptor = "(Ljava/lang/String;IJLio/quarkiverse/qubit/model/Person;)Z";
            // String:0, int:1, long:2-3, Person:4
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(4);
        }
    }

    // ========== Parameter Type Resolution Tests ==========

    @Nested
    @DisplayName("Parameter Type Resolution")
    class ParameterTypeTests {

        @Test
        @DisplayName("Resolves primitive types")
        void getParameterType_primitives_returnsCorrectClass() {
            String descriptor = "(IJDFZBCS)V";

            assertThat(DescriptorParser.getParameterType(descriptor, 0)).isEqualTo(int.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 1)).isEqualTo(long.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 2)).isEqualTo(double.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 3)).isEqualTo(float.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 4)).isEqualTo(boolean.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 5)).isEqualTo(byte.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 6)).isEqualTo(char.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 7)).isEqualTo(short.class);
        }

        @Test
        @DisplayName("Resolves known class types")
        void getParameterType_knownClass_returnsCorrectClass() {
            String descriptor = "(Ljava/lang/String;)V";
            assertThat(DescriptorParser.getParameterType(descriptor, 0)).isEqualTo(String.class);
        }

        @Test
        @DisplayName("Returns Object for unknown class")
        void getParameterType_unknownClass_returnsObject() {
            String descriptor = "(Lcom/unknown/NonExistentClass;)V";
            assertThat(DescriptorParser.getParameterType(descriptor, 0)).isEqualTo(Object.class);
        }

        @Test
        @DisplayName("Returns Object for invalid parameter index")
        void getParameterType_invalidIndex_returnsObject() {
            String descriptor = "(I)V";
            assertThat(DescriptorParser.getParameterType(descriptor, 99)).isEqualTo(Object.class);
        }
    }

    // ========== Boundary Condition Tests ==========

    @Nested
    @DisplayName("Boundary Condition Tests (Mutation Killing)")
    class BoundaryConditionTests {

        @Test
        @DisplayName("hasNext returns false at exact boundary")
        void parameterIterator_hasNext_atExactBoundary_returnsFalse() {
            // After consuming all params, position should be at ')' and hasNext() = false
            String descriptor = "(I)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next(); // consume int
            assertThat(iter.hasNext())
                    .as("hasNext should be false when position is at ')'")
                    .isFalse();
        }

        @Test
        @DisplayName("hasNext with empty parameters")
        void parameterIterator_hasNext_emptyParams_returnsFalse() {
            String descriptor = "()V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            assertThat(iter.hasNext())
                    .as("hasNext should be false for empty parameters")
                    .isFalse();
        }

        @Test
        @DisplayName("next handles object array at descriptor boundary")
        void parameterIterator_next_objectArrayAtBoundary_handlesCorrectly() {
            // Object array as last parameter - tests position < length boundary
            String descriptor = "([Ljava/lang/String;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next();
            assertThat(iter.getTypeDescriptor())
                    .as("Should correctly parse object array at boundary")
                    .isEqualTo("[Ljava/lang/String;");
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("next handles class reference at descriptor end")
        void parameterIterator_next_classAtEnd_handlesCorrectly() {
            String descriptor = "(Ljava/lang/String;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next();
            assertThat(iter.getTypeDescriptor()).isEqualTo("Ljava/lang/String;");
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("slotIndexToParameterIndex matches slot before next() call")
        void slotIndexToParameterIndex_matchesSlotBeforeNext() {
            String descriptor = "(I)V";
            int result = DescriptorParser.slotIndexToParameterIndex(descriptor, 0);
            assertThat(result)
                    .as("Slot 0 should map to parameter 0")
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("slotIndexToParameterIndex matches slot after processing wide type")
        void slotIndexToParameterIndex_matchesAfterWideType() {
            String descriptor = "(JI)V";
            int result = DescriptorParser.slotIndexToParameterIndex(descriptor, 1);
            assertThat(result)
                    .as("Slot 1 (middle of long) should return -1")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("getParameterType correctly matches paramIndex")
        void getParameterType_matchesParamIndex() {
            String descriptor = "(IJD)V"; // int, long, double

            assertThat(DescriptorParser.getParameterType(descriptor, 0))
                    .as("Param 0 should be int.class")
                    .isEqualTo(int.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 1))
                    .as("Param 1 should be long.class")
                    .isEqualTo(long.class);
            assertThat(DescriptorParser.getParameterType(descriptor, 2))
                    .as("Param 2 should be double.class")
                    .isEqualTo(double.class);
        }

        @Test
        @DisplayName("Multi-dimensional array bracket processing boundary")
        void parameterIterator_multiDimArray_bracketBoundary() {
            String descriptor = "([[[I)V"; // 3D int array
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next();
            assertThat(iter.getTypeDescriptor())
                    .as("Should correctly skip all array brackets")
                    .isEqualTo("[[[I");
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("Multi-dimensional object array at end")
        void parameterIterator_multiDimObjectArray_atEnd() {
            String descriptor = "([[Ljava/util/List;)V"; // 2D List array
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next();
            assertThat(iter.getTypeDescriptor())
                    .as("Should correctly parse 2D object array")
                    .isEqualTo("[[Ljava/util/List;");
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("Single primitive at boundary")
        void parameterIterator_singlePrimitive_boundary() {
            String descriptor = "(Z)V"; // boolean only
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            assertThat(iter.hasNext()).isTrue();
            iter.next();
            assertThat(iter.getTypeChar()).isEqualTo('Z');
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("Primitive array element at boundary")
        void parameterIterator_primitiveArrayElement_boundary() {
            String descriptor = "([Z)V"; // boolean array
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);

            iter.next();
            assertThat(iter.getTypeDescriptor()).isEqualTo("[Z");
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("slotIndexToParameterIndex returns -1 for non-existent slot")
        void slotIndexToParameterIndex_nonExistentSlot_returnsNegativeOne() {
            String descriptor = "(IJ)V"; // int:0, long:1-2
            // Slot 5 doesn't exist - after iterating all params, should return -1
            int result = DescriptorParser.slotIndexToParameterIndex(descriptor, 5);
            assertThat(result)
                    .as("Non-existent slot should return -1")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("slotIndexToParameterIndex returns -1 for empty descriptor")
        void slotIndexToParameterIndex_emptyDescriptor_returnsNegativeOne() {
            String descriptor = "()V";
            int result = DescriptorParser.slotIndexToParameterIndex(descriptor, 0);
            assertThat(result)
                    .as("Empty params should return -1 for any slot")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("getParameterType specific paramIndex match")
        void getParameterType_specificParamIndex_matchesCorrectly() {
            String descriptor = "(Ljava/lang/String;I)V"; // String:0, int:1

            // Must return different types for different indices
            Class<?> type0 = DescriptorParser.getParameterType(descriptor, 0);
            Class<?> type1 = DescriptorParser.getParameterType(descriptor, 1);

            assertThat(type0)
                    .as("Param 0 must be String")
                    .isEqualTo(String.class);
            assertThat(type1)
                    .as("Param 1 must be int")
                    .isEqualTo(int.class);
            assertThat(type0).isNotEqualTo(type1);
        }

        @Test
        @DisplayName("calculateFirstEntityParameterSlotIndex returns -1 for single param")
        void calculateFirstEntityParameterSlotIndex_singleParam_returnsNegativeOne() {
            String descriptor = "(LPerson;)Z";
            int slot = DescriptorParser.calculateFirstEntityParameterSlotIndex(descriptor);
            assertThat(slot)
                    .as("Single param should return -1")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("calculateSecondEntityParameterSlotIndex returns -1 for single param")
        void calculateSecondEntityParameterSlotIndex_singleParam_returnsNegativeOne() {
            String descriptor = "(LPerson;)Z";
            int slot = DescriptorParser.calculateSecondEntityParameterSlotIndex(descriptor);
            assertThat(slot)
                    .as("Single param should return -1")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("calculateFirstEntityParameterSlotIndex returns -1 for empty params")
        void calculateFirstEntityParameterSlotIndex_emptyParams_returnsNegativeOne() {
            String descriptor = "()Z";
            int slot = DescriptorParser.calculateFirstEntityParameterSlotIndex(descriptor);
            assertThat(slot)
                    .as("Empty params should return -1")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("calculateSecondEntityParameterSlotIndex returns -1 for empty params")
        void calculateSecondEntityParameterSlotIndex_emptyParams_returnsNegativeOne() {
            String descriptor = "()Z";
            int slot = DescriptorParser.calculateSecondEntityParameterSlotIndex(descriptor);
            assertThat(slot)
                    .as("Empty params should return -1")
                    .isEqualTo(-1);
        }
    }

    // ========== Null and Malformed Descriptor Tests ==========

    @Nested
    @DisplayName("Null and Malformed Descriptor Handling")
    class NullAndMalformedDescriptorTests {

        @Test
        @DisplayName("hasNext returns false for null descriptor")
        void parameterIterator_hasNext_nullDescriptor_returnsFalse() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(null);
            assertThat(iter.hasNext())
                    .as("hasNext should return false for null descriptor")
                    .isFalse();
        }

        @Test
        @DisplayName("countMethodArguments returns 0 for null descriptor")
        void countMethodArguments_nullDescriptor_returnsZero() {
            int count = DescriptorParser.countMethodArguments(null);
            assertThat(count)
                    .as("Null descriptor should have 0 parameters")
                    .isZero();
        }

        @Test
        @DisplayName("calculateEntityParameterSlotIndex returns 0 for null descriptor")
        void calculateEntityParameterSlotIndex_nullDescriptor_returnsZero() {
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(null);
            assertThat(slot)
                    .as("Null descriptor should return slot 0")
                    .isZero();
        }

        @Test
        @DisplayName("slotIndexToParameterIndex returns -1 for null descriptor")
        void slotIndexToParameterIndex_nullDescriptor_returnsNegativeOne() {
            int result = DescriptorParser.slotIndexToParameterIndex(null, 0);
            assertThat(result)
                    .as("Null descriptor should return -1")
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("getParameterType returns Object for null descriptor")
        void getParameterType_nullDescriptor_returnsObject() {
            Class<?> type = DescriptorParser.getParameterType(null, 0);
            assertThat(type)
                    .as("Null descriptor should return Object.class")
                    .isEqualTo(Object.class);
        }

        @Test
        @DisplayName("calculateBiEntityParameterSlotIndices returns null for null descriptor")
        void calculateBiEntityParameterSlotIndices_nullDescriptor_returnsNull() {
            int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(null);
            assertThat(slots)
                    .as("Null descriptor should return null")
                    .isNull();
        }

        @Test
        @DisplayName("Iterator with descriptor not starting with parenthesis")
        void parameterIterator_noParenthesis_startsAtPositionZero() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator("IJ)V");
            assertThat(iter.hasNext())
                    .as("Should have parameters even without leading parenthesis")
                    .isTrue();
            iter.next();
            assertThat(iter.getTypeChar())
                    .as("First type should be I (int)")
                    .isEqualTo('I');
        }

        @Test
        @DisplayName("Iterator with empty string descriptor")
        void parameterIterator_emptyString_hasNextFalse() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator("");
            assertThat(iter.hasNext())
                    .as("Empty string should have no parameters")
                    .isFalse();
        }

        @Test
        @DisplayName("Iterator with single parenthesis only")
        void parameterIterator_singleParenthesis_hasNextFalse() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator("(");
            assertThat(iter.hasNext())
                    .as("Single '(' should have no parameters (position >= length)")
                    .isFalse();
        }

        @Test
        @DisplayName("Iterator at exact position boundary - length equals position")
        void parameterIterator_positionEqualsLength_hasNextFalse() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator("()");
            assertThat(iter.hasNext())
                    .as("Empty params should have hasNext false")
                    .isFalse();
        }
    }
}
