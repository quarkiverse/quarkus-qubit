package io.quarkiverse.qubit.deployment.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * Unit tests for {@link DescriptorParser}.
 * <p>
 * Tests JVM method descriptor parsing with special attention to:
 * <ul>
 * <li>Wide types (long/double) that occupy 2 slots instead of 1</li>
 * <li>Object references and arrays</li>
 * <li>Mixed parameter combinations</li>
 * <li>Bi-entity and group query descriptors</li>
 * </ul>
 * <p>
 * JVM Type Descriptors:
 * <ul>
 * <li>B = byte, C = char, D = double, F = float</li>
 * <li>I = int, J = long, S = short, Z = boolean</li>
 * <li>L...;= reference type, [ = array</li>
 * </ul>
 */
class DescriptorParserTest {

    /** Test data for calculateEntityParameterSlotIndex tests. */
    static Stream<Arguments> entitySlotIndexTestData() {
        return Stream.of(
                // Wide type tests
                Arguments.of("(JLjava/lang/String;)V", 2, "Long parameter occupies 2 slots"),
                Arguments.of("(DLjava/lang/String;)V", 2, "Double parameter occupies 2 slots"),
                Arguments.of("(JDLPerson;)V", 4, "Multiple wide types accumulate correctly"),
                Arguments.of("(IJ)V", 1, "Wide type at end occupies 2 slots"),
                // Primitive type tests
                Arguments.of("(IFZLPerson;)V", 3, "All primitives occupy 1 slot (except long/double)"),
                Arguments.of("(BCSIFZLPerson;)V", 6, "All single-slot primitives"),
                Arguments.of("(IJFDZLPerson;)V", 7, "Mixed primitives and wide types"),
                // Array type tests
                Arguments.of("([ILPerson;)V", 1, "Primitive array occupies 1 slot"),
                Arguments.of("([Ljava/lang/String;LPerson;)V", 1, "Object array occupies 1 slot"),
                Arguments.of("([[ILPerson;)V", 1, "Multi-dimensional array occupies 1 slot"),
                Arguments.of("([[[ILPerson;)V", 1, "3D array occupies 1 slot"),
                Arguments.of("([JLPerson;)V", 1, "Long array occupies 1 slot (not 2 like long primitive)"),
                Arguments.of("([DLPerson;)V", 1, "Double array occupies 1 slot (not 2 like double primitive)"),
                Arguments.of("([[Ljava/lang/String;LPerson;)V", 1, "Multi-dimensional object array occupies 1 slot"),
                Arguments.of("([I[Ljava/lang/String;LPerson;)V", 2, "Multiple arrays before entity"),
                Arguments.of("([IJ[DLPerson;)V", 4, "Array mixed with wide types"),
                // Edge case tests
                Arguments.of("(Ljava/lang/String;Lio/quarkiverse/qubit/runtime/Person;)V", 1, "Fully qualified class names"),
                Arguments.of("(LOuterClass$InnerClass;LPerson;)V", 1, "Inner class references"),
                Arguments.of("(LPerson;)V", 0, "Single parameter returns slot 0"),
                Arguments.of("(Ljava/lang/String;IJLio/quarkiverse/qubit/model/Person;)Z", 4, "Complex real-world descriptor"));
    }

    /** Test data for countMethodArguments tests. */
    static Stream<Arguments> countMethodArgumentsTestData() {
        return Stream.of(
                Arguments.of("(LPerson;)Z", 1),
                Arguments.of("(LPerson;LPhone;)Z", 2),
                Arguments.of("(IJDLPerson;)Z", 4),
                Arguments.of("()V", 0),
                Arguments.of("(JD)V", 2),
                Arguments.of("(JJDD)V", 4),
                Arguments.of("([ILjava/lang/String;)V", 2),
                Arguments.of("([[I)V", 1),
                Arguments.of("([I[Ljava/lang/String;[[D)V", 3),
                Arguments.of("(I[IJ[Ljava/lang/String;)V", 4));
    }

    /** Test data for slotIndexToParameterIndex tests. */
    static Stream<Arguments> slotToParamIndexTestData() {
        return Stream.of(
                // Basic tests
                Arguments.of("(IJLPerson;)V", 0, 0),
                Arguments.of("(IJLPerson;)V", 1, 1),
                Arguments.of("(IJLPerson;)V", 3, 2),
                Arguments.of("(I)V", 99, -1),
                // Edge cases
                Arguments.of("(I)V", 0, 0),
                Arguments.of("(J)V", 0, 0),
                Arguments.of("(J)V", 1, -1),
                Arguments.of("(D)V", 0, 0),
                Arguments.of("(JI)V", 2, 1),
                Arguments.of("([ILjava/lang/String;)V", 0, 0),
                Arguments.of("([ILjava/lang/String;)V", 1, 1),
                Arguments.of("([[ILjava/lang/String;)V", 0, 0),
                Arguments.of("([[ILjava/lang/String;)V", 1, 1),
                Arguments.of("(J[ILjava/lang/String;)V", 0, 0),
                Arguments.of("(J[ILjava/lang/String;)V", 2, 1),
                Arguments.of("(J[ILjava/lang/String;)V", 3, 2),
                Arguments.of("(I)V", 1000, -1),
                Arguments.of("(IJ)V", 5, -1),
                Arguments.of("()V", 0, -1));
    }

    /** Test data for getParameterType primitive tests. */
    static Stream<Arguments> parameterTypePrimitiveTestData() {
        return Stream.of(
                Arguments.of("(IJDFZBCS)V", 0, int.class),
                Arguments.of("(IJDFZBCS)V", 1, long.class),
                Arguments.of("(IJDFZBCS)V", 2, double.class),
                Arguments.of("(IJDFZBCS)V", 3, float.class),
                Arguments.of("(IJDFZBCS)V", 4, boolean.class),
                Arguments.of("(IJDFZBCS)V", 5, byte.class),
                Arguments.of("(IJDFZBCS)V", 6, char.class),
                Arguments.of("(IJDFZBCS)V", 7, short.class),
                Arguments.of("(Ljava/lang/String;)V", 0, String.class),
                Arguments.of("(Lcom/unknown/NonExistentClass;)V", 0, Object.class),
                Arguments.of("(I)V", 99, Object.class),
                Arguments.of("([ILjava/lang/String;)V", 1, String.class),
                Arguments.of("([I)V", 0, Object.class),
                Arguments.of("([[IJLjava/lang/String;)V", 0, Object.class),
                Arguments.of("([[IJLjava/lang/String;)V", 1, long.class),
                Arguments.of("([[IJLjava/lang/String;)V", 2, String.class));
    }

    /** Test data for calculateBiEntityParameterSlotIndices tests. */
    static Stream<Arguments> biEntitySlotIndicesTestData() {
        return Stream.of(
                Arguments.of("(JDLPerson;LPhone;)V", new int[] { 4, 5 }, "Mixed wide types before bi-entity"),
                Arguments.of("(LPerson;LPhone;)Z", new int[] { 0, 1 }, "Simple bi-entity without captured variables"),
                Arguments.of("(ILPerson;LPhone;)Z", new int[] { 1, 2 }, "Bi-entity with primitive captured variable"),
                Arguments.of("(LPerson;)Z", null, "Single parameter returns null"),
                Arguments.of("([ILPerson;LPhone;)Z", new int[] { 1, 2 }, "Bi-entity with array captured variable"),
                Arguments.of("([[ILPerson;LPhone;)Z", new int[] { 1, 2 }, "Bi-entity with multi-dim array captured"),
                Arguments.of("([I[Ljava/lang/String;LPerson;LPhone;)Z", new int[] { 2, 3 },
                        "Bi-entity with multiple arrays captured"));
    }

    /** Test data for getReturnTypeDescriptor tests. */
    static Stream<Arguments> returnTypeDescriptorTestData() {
        return Stream.of(
                Arguments.of("(I)V", "V"),
                Arguments.of("(I)I", "I"),
                Arguments.of("(I)J", "J"),
                Arguments.of("(I)D", "D"),
                Arguments.of("(I)Z", "Z"),
                Arguments.of("(I)Ljava/lang/String;", "Ljava/lang/String;"),
                Arguments.of("()Z", "Z"),
                Arguments.of(null, ""),
                Arguments.of("(I", ""),
                Arguments.of("(I)", ""));
    }

    /** Test data for returnsBooleanType tests. */
    static Stream<Arguments> returnsBooleanTypeTestData() {
        return Stream.of(
                Arguments.of("(I)Z", true),
                Arguments.of("(I)Ljava/lang/Boolean;", true),
                Arguments.of("(I)I", false),
                Arguments.of("(I)Ljava/lang/String;", false),
                Arguments.of("(I)V", false));
    }

    /** Test data for returnsIntType tests. */
    static Stream<Arguments> returnsIntTypeTestData() {
        return Stream.of(
                Arguments.of("(Ljava/lang/String;)I", true),
                Arguments.of("(Ljava/lang/String;)J", false),
                Arguments.of("(I)Ljava/lang/Integer;", false),
                Arguments.of("(I)V", false));
    }

    /** Test data for returnsType tests. */
    static Stream<Arguments> returnsTypeTestData() {
        return Stream.of(
                Arguments.of("(I)Ljava/lang/String;", "java/lang/String", true),
                Arguments.of("(I)Ljava/lang/String;", "java/lang/Integer", false),
                Arguments.of("(I)I", "java/lang/Integer", false),
                Arguments.of("(I)Lio/quarkiverse/qubit/Person;", "io/quarkiverse/qubit/Person", true));
    }

    /** Test data for returnTypeContains tests. */
    static Stream<Arguments> returnTypeContainsTestData() {
        return Stream.of(
                Arguments.of("(I)Ljava/lang/String;", "String", true),
                Arguments.of("(I)Ljava/lang/String;", "java/lang", true),
                Arguments.of("(I)Ljava/lang/String;", "Integer", false),
                Arguments.of("(I)I", "Integer", false));
    }

    /** Test data for getEntityClassName tests. */
    static Stream<Arguments> entityClassNameTestData() {
        return Stream.of(
                Arguments.of("(Ljava/lang/String;)V", "java.lang.String"),
                Arguments.of("(ILjava/lang/Person;)Z", "java.lang.Person"),
                Arguments.of("()V", "java.lang.Object"),
                Arguments.of("(Lio/quarkiverse/qubit/model/Person;)Z", "io.quarkiverse.qubit.model.Person"),
                Arguments.of("(I)V", "int"));
    }

    /** Test data for getParameterTypeName tests. */
    static Stream<Arguments> parameterTypeNameTestData() {
        return Stream.of(
                Arguments.of("(Ljava/lang/String;I)V", 0, "java.lang.String"),
                Arguments.of("(Ljava/lang/String;I)V", 1, "int"),
                Arguments.of("(I)V", 99, "java.lang.Object"),
                Arguments.of("([I)V", 0, "java.lang.Object"));
    }

    /** Test data for getParameterTypeName all primitives. */
    static Stream<Arguments> parameterTypeNamePrimitivesTestData() {
        return Stream.of(
                Arguments.of("(ZBCSIJFD)V", 0, "boolean"),
                Arguments.of("(ZBCSIJFD)V", 1, "byte"),
                Arguments.of("(ZBCSIJFD)V", 2, "char"),
                Arguments.of("(ZBCSIJFD)V", 3, "short"),
                Arguments.of("(ZBCSIJFD)V", 4, "int"),
                Arguments.of("(ZBCSIJFD)V", 5, "long"),
                Arguments.of("(ZBCSIJFD)V", 6, "float"),
                Arguments.of("(ZBCSIJFD)V", 7, "double"));
    }

    /** Test data for getEntityClass tests. */
    static Stream<Arguments> entityClassTestData() {
        return Stream.of(
                Arguments.of("()V", Object.class),
                Arguments.of("(Ljava/lang/String;)V", String.class),
                Arguments.of("(I)V", int.class),
                Arguments.of("(Lcom/unknown/NonExistent;)V", Object.class));
    }

    @ParameterizedTest(name = "{2}: {0} → slot {1}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#entitySlotIndexTestData")
    @DisplayName("calculateEntityParameterSlotIndex returns correct slot")
    void calculateEntityParameterSlotIndex_returnsCorrectSlot(String descriptor, int expectedSlot, String description) {
        int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
        assertThat(slot).isEqualTo(expectedSlot);
    }

    @ParameterizedTest(name = "{0} → {1} arguments")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#countMethodArgumentsTestData")
    @DisplayName("countMethodArguments returns correct count")
    void countMethodArguments_returnsCorrectCount(String descriptor, int expectedCount) {
        assertThat(DescriptorParser.countMethodArguments(descriptor)).isEqualTo(expectedCount);
    }

    @ParameterizedTest(name = "{0}, slot {1} → param {2}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#slotToParamIndexTestData")
    @DisplayName("slotIndexToParameterIndex returns correct index")
    void slotIndexToParameterIndex_returnsCorrectIndex(String descriptor, int slot, int expectedIndex) {
        assertThat(DescriptorParser.slotIndexToParameterIndex(descriptor, slot)).isEqualTo(expectedIndex);
    }

    @ParameterizedTest(name = "{0}, param {1} → {2}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#parameterTypePrimitiveTestData")
    @DisplayName("getParameterType returns correct class")
    void getParameterType_returnsCorrectClass(String descriptor, int paramIndex, Class<?> expectedClass) {
        assertThat(DescriptorParser.getParameterType(descriptor, paramIndex)).isEqualTo(expectedClass);
    }

    @ParameterizedTest(name = "{2}: {0} → {1}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#biEntitySlotIndicesTestData")
    @DisplayName("calculateBiEntityParameterSlotIndices returns correct slots")
    void calculateBiEntityParameterSlotIndices_returnsCorrectSlots(String descriptor, int[] expectedSlots, String description) {
        int[] slots = DescriptorParser.calculateBiEntityParameterSlotIndices(descriptor);
        if (expectedSlots == null) {
            assertThat(slots).isNull();
        } else {
            assertThat(slots).containsExactly(expectedSlots);
        }
    }

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#returnTypeDescriptorTestData")
    @DisplayName("getReturnTypeDescriptor extracts return type")
    void getReturnTypeDescriptor_extractsReturnType(String descriptor, String expected) {
        assertThat(DescriptorParser.getReturnTypeDescriptor(descriptor)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#returnsBooleanTypeTestData")
    @DisplayName("returnsBooleanType returns correct result")
    void returnsBooleanType_returnsCorrectResult(String descriptor, boolean expected) {
        assertThat(DescriptorParser.returnsBooleanType(descriptor)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#returnsIntTypeTestData")
    @DisplayName("returnsIntType returns correct result")
    void returnsIntType_returnsCorrectResult(String descriptor, boolean expected) {
        assertThat(DescriptorParser.returnsIntType(descriptor)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} contains \"{1}\" → {2}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#returnsTypeTestData")
    @DisplayName("returnsType returns correct result")
    void returnsType_returnsCorrectResult(String descriptor, String className, boolean expected) {
        assertThat(DescriptorParser.returnsType(descriptor, className)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} contains \"{1}\" → {2}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#returnTypeContainsTestData")
    @DisplayName("returnTypeContains returns correct result")
    void returnTypeContains_returnsCorrectResult(String descriptor, String substring, boolean expected) {
        assertThat(DescriptorParser.returnTypeContains(descriptor, substring)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#entityClassNameTestData")
    @DisplayName("getEntityClassName returns correct name")
    void getEntityClassName_returnsCorrectName(String descriptor, String expectedName) {
        assertThat(DescriptorParser.getEntityClassName(descriptor)).isEqualTo(expectedName);
    }

    @ParameterizedTest(name = "{0}, param {1} → \"{2}\"")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#parameterTypeNameTestData")
    @DisplayName("getParameterTypeName returns correct name")
    void getParameterTypeName_returnsCorrectName(String descriptor, int paramIndex, String expectedName) {
        assertThat(DescriptorParser.getParameterTypeName(descriptor, paramIndex)).isEqualTo(expectedName);
    }

    @ParameterizedTest(name = "{0}, param {1} → \"{2}\"")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#parameterTypeNamePrimitivesTestData")
    @DisplayName("getParameterTypeName handles all primitives")
    void getParameterTypeName_allPrimitives_returnsCorrectNames(String descriptor, int paramIndex, String expectedName) {
        assertThat(DescriptorParser.getParameterTypeName(descriptor, paramIndex)).isEqualTo(expectedName);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("io.quarkiverse.qubit.deployment.util.DescriptorParserTest#entityClassTestData")
    @DisplayName("getEntityClass returns correct class")
    void getEntityClass_returnsCorrectClass(String descriptor, Class<?> expectedClass) {
        assertThat(DescriptorParser.getEntityClass(descriptor)).isEqualTo(expectedClass);
    }

    @Nested
    @DisplayName("Bi-Entity Specific Slot Calculations")
    class BiEntitySpecificTests {

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
            String descriptor = "(IJLPerson;LPhone;)Z";
            int slot = DescriptorParser.calculateSecondEntityParameterSlotIndex(descriptor);
            assertThat(slot).isEqualTo(4);
        }

        @ParameterizedTest
        @CsvSource({
                "(LPerson;)Z, -1",
                "()Z, -1"
        })
        @DisplayName("calculateFirstEntityParameterSlotIndex returns -1 for insufficient params")
        void calculateFirstEntityParameterSlotIndex_insufficientParams_returnsNegativeOne(String descriptor, int expected) {
            assertThat(DescriptorParser.calculateFirstEntityParameterSlotIndex(descriptor)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "(LPerson;)Z, -1",
                "()Z, -1"
        })
        @DisplayName("calculateSecondEntityParameterSlotIndex returns -1 for insufficient params")
        void calculateSecondEntityParameterSlotIndex_insufficientParams_returnsNegativeOne(String descriptor, int expected) {
            assertThat(DescriptorParser.calculateSecondEntityParameterSlotIndex(descriptor)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("ParameterIterator Functionality")
    class ParameterIteratorTests {

        @Test
        @DisplayName("Iterates captured variables before entity")
        void parameterIterator_handlesCapturedVariablesBeforeEntity() {
            String descriptor = "(Ljava/lang/String;ILPerson;)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            List<Integer> slots = new ArrayList<>();

            while (iter.hasNext()) {
                iter.next();
                slots.add(iter.getCurrentParamSlotStart());
            }

            assertThat(slots).containsExactly(0, 1, 2);
        }

        @Test
        @DisplayName("Handles Group query descriptor")
        void parameterIterator_handlesGroupQueryDescriptor() {
            String descriptor = "(LGroup;)J";
            int slot = DescriptorParser.calculateEntityParameterSlotIndex(descriptor);
            assertThat(slot).isZero();
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

            iter.next();

            assertThatThrownBy(iter::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more parameters");
        }

        @ParameterizedTest
        @CsvSource({
                "(IJ)V, 1",
                "(JI)V, 2",
                "(ID)V, 1"
        })
        @DisplayName("getSlotIndex returns correct value after processing")
        void parameterIterator_getSlotIndex_returnsCorrectValue(String descriptor, int expectedAfterFirst) {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            iter.next();
            assertThat(iter.getSlotIndex()).isEqualTo(expectedAfterFirst);
        }

        @ParameterizedTest
        @CsvSource({
                "(IJ)V, 1",
                "(ID)V, 1",
                "(JI)V, 2"
        })
        @DisplayName("getCurrentParamSlotStart returns correct slot")
        void parameterIterator_getCurrentParamSlotStart_returnsCorrectSlot(String descriptor, int expectedSecondParamSlot) {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            iter.next();
            iter.next();
            assertThat(iter.getCurrentParamSlotStart()).isEqualTo(expectedSecondParamSlot);
        }
    }

    @Nested
    @DisplayName("ParameterIterator Array Handling")
    class ArrayTypeIteratorTests {

        @ParameterizedTest
        @CsvSource({
                "([ILPerson;)V, '[I', 'LPerson;'",
                "([[DLPerson;)V, '[[D', 'LPerson;'",
                "([Ljava/lang/String;LPerson;)V, '[Ljava/lang/String;', 'LPerson;'"
        })
        @DisplayName("Iterator returns correct type descriptors for arrays")
        void parameterIterator_withArrays_returnsCorrectTypeDescriptors(String descriptor,
                String expectedFirst, String expectedSecond) {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            List<String> typeDescriptors = new ArrayList<>();
            while (iter.hasNext()) {
                iter.next();
                typeDescriptors.add(iter.getTypeDescriptor());
            }
            assertThat(typeDescriptors).containsExactly(expectedFirst, expectedSecond);
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

    @Nested
    @DisplayName("Boundary Condition Tests (Mutation Killing)")
    class BoundaryConditionTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "([Ljava/lang/String;)V, '[Ljava/lang/String;'",
                "(Ljava/lang/String;)V, 'Ljava/lang/String;'",
                "([[[I)V, '[[[I'",
                "([[Ljava/util/List;)V, '[[Ljava/util/List;'",
                "([Z)V, '[Z'"
        })
        @DisplayName("Single-parameter descriptor boundary handling")
        void parameterIterator_singleParam_handlesCorrectly(String descriptor, String expectedType) {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            iter.next();
            assertThat(iter.getTypeDescriptor()).isEqualTo(expectedType);
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("hasNext returns false at exact boundary")
        void parameterIterator_hasNext_atExactBoundary_returnsFalse() {
            String descriptor = "(I)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            iter.next();
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("hasNext with empty parameters")
        void parameterIterator_hasNext_emptyParams_returnsFalse() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator("()V");
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("Single primitive at boundary verifies typeChar")
        void parameterIterator_singlePrimitive_boundary() {
            String descriptor = "(Z)V";
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            assertThat(iter.hasNext()).isTrue();
            iter.next();
            assertThat(iter.getTypeChar()).isEqualTo('Z');
            assertThat(iter.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("Null and Malformed Descriptor Handling")
    class NullAndMalformedDescriptorTests {

        @Test
        @DisplayName("hasNext returns false for null descriptor")
        void parameterIterator_hasNext_nullDescriptor_returnsFalse() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(null);
            assertThat(iter.hasNext()).isFalse();
        }

        @Test
        @DisplayName("countMethodArguments returns 0 for null descriptor")
        void countMethodArguments_nullDescriptor_returnsZero() {
            assertThat(DescriptorParser.countMethodArguments(null)).isZero();
        }

        @Test
        @DisplayName("calculateEntityParameterSlotIndex returns 0 for null descriptor")
        void calculateEntityParameterSlotIndex_nullDescriptor_returnsZero() {
            assertThat(DescriptorParser.calculateEntityParameterSlotIndex(null)).isZero();
        }

        @Test
        @DisplayName("slotIndexToParameterIndex returns -1 for null descriptor")
        void slotIndexToParameterIndex_nullDescriptor_returnsNegativeOne() {
            assertThat(DescriptorParser.slotIndexToParameterIndex(null, 0)).isEqualTo(-1);
        }

        @Test
        @DisplayName("getParameterType returns Object for null descriptor")
        void getParameterType_nullDescriptor_returnsObject() {
            assertThat(DescriptorParser.getParameterType(null, 0)).isEqualTo(Object.class);
        }

        @Test
        @DisplayName("calculateBiEntityParameterSlotIndices returns null for null descriptor")
        void calculateBiEntityParameterSlotIndices_nullDescriptor_returnsNull() {
            assertThat(DescriptorParser.calculateBiEntityParameterSlotIndices(null)).isNull();
        }

        @Test
        @DisplayName("Iterator with descriptor not starting with parenthesis")
        void parameterIterator_noParenthesis_startsAtPositionZero() {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator("IJ)V");
            assertThat(iter.hasNext()).isTrue();
            iter.next();
            assertThat(iter.getTypeChar()).isEqualTo('I');
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Iterator with null or empty string has no parameters")
        void parameterIterator_nullOrEmpty_hasNextFalse(String descriptor) {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            assertThat(iter.hasNext()).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                "(, false",
                "(), false"
        })
        @DisplayName("Iterator with malformed descriptors")
        void parameterIterator_malformed_hasNextFalse(String descriptor, boolean expected) {
            DescriptorParser.ParameterIterator iter = new DescriptorParser.ParameterIterator(descriptor);
            assertThat(iter.hasNext()).isEqualTo(expected);
        }
    }
}
