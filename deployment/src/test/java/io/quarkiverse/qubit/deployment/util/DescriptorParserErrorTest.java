package io.quarkiverse.qubit.deployment.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Error path tests for DescriptorParser.
 * Tests exception handling when parsing method descriptors.
 */
@DisplayName("DescriptorParser Error Path Tests")
class DescriptorParserErrorTest {

    @Nested
    @DisplayName("ParameterIterator.next() error cases")
    class ParameterIteratorNextTests {

        @Test
        @DisplayName("J1: next() throws IllegalStateException when exhausted")
        void parseNext_exhausted_throws() {
            // Given: a descriptor with one parameter, already consumed
            DescriptorParser.ParameterIterator iterator = new DescriptorParser.ParameterIterator("(I)V");

            // Consume the only parameter
            assertThat(iterator.hasNext()).isTrue();
            iterator.next();

            // When/Then: calling next() again throws
            assertThat(iterator.hasNext()).isFalse();
            assertThatThrownBy(iterator::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more parameters");
        }

        @Test
        @DisplayName("next() throws on empty descriptor")
        void next_onEmptyDescriptor_throws() {
            // Given: descriptor with no parameters
            DescriptorParser.ParameterIterator iterator = new DescriptorParser.ParameterIterator("()V");

            // When/Then
            assertThat(iterator.hasNext()).isFalse();
            assertThatThrownBy(iterator::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more parameters");
        }

        @Test
        @DisplayName("next() throws after consuming all parameters in multi-param descriptor")
        void next_afterConsumingAllParams_throws() {
            // Given: descriptor with multiple parameters
            DescriptorParser.ParameterIterator iterator = new DescriptorParser.ParameterIterator("(IJD)V");

            // Consume all 3 parameters (int, long, double)
            iterator.next(); // I (int)
            iterator.next(); // J (long)
            iterator.next(); // D (double)

            // When/Then
            assertThat(iterator.hasNext()).isFalse();
            assertThatThrownBy(iterator::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more parameters");
        }
    }

    @Nested
    @DisplayName("ParameterIterator edge cases")
    class ParameterIteratorEdgeCases {

        @Test
        @DisplayName("hasNext returns false for null descriptor")
        void hasNext_withNullDescriptor_returnsFalse() {
            DescriptorParser.ParameterIterator iterator = new DescriptorParser.ParameterIterator(null);

            assertThat(iterator.hasNext()).isFalse();
        }

        @Test
        @DisplayName("hasNext returns false for descriptor without opening paren")
        void hasNext_withoutOpeningParen_startsAtPositionZero() {
            // Non-standard descriptor (no opening paren)
            DescriptorParser.ParameterIterator iterator = new DescriptorParser.ParameterIterator("I)V");

            // Position starts at 0, so 'I' is treated as a parameter
            assertThat(iterator.hasNext()).isTrue();
            iterator.next();
            assertThat(iterator.getTypeChar()).isEqualTo('I');
        }

        @Test
        @DisplayName("next() on null descriptor after hasNext check throws")
        void next_onNullDescriptor_throws() {
            DescriptorParser.ParameterIterator iterator = new DescriptorParser.ParameterIterator(null);

            assertThatThrownBy(iterator::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more parameters");
        }
    }

    @Nested
    @DisplayName("Static method edge cases")
    class StaticMethodEdgeCases {

        @Test
        @DisplayName("countMethodArguments returns 0 for empty params")
        void countMethodArguments_emptyParams_returnsZero() {
            int count = DescriptorParser.countMethodArguments("()V");
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("calculateBiEntityParameterSlotIndices returns null for single param")
        void calculateBiEntitySlots_singleParam_returnsNull() {
            int[] result = DescriptorParser.calculateBiEntityParameterSlotIndices("(I)V");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("calculateFirstEntityParameterSlotIndex returns -1 for single param")
        void calculateFirstEntitySlot_singleParam_returnsNegativeOne() {
            int result = DescriptorParser.calculateFirstEntityParameterSlotIndex("(I)V");
            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("calculateSecondEntityParameterSlotIndex returns -1 for single param")
        void calculateSecondEntitySlot_singleParam_returnsNegativeOne() {
            int result = DescriptorParser.calculateSecondEntityParameterSlotIndex("(I)V");
            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("slotIndexToParameterIndex returns -1 for non-existent slot")
        void slotIndexToParameterIndex_nonExistentSlot_returnsNegativeOne() {
            // For "(I)V", only slot 0 is valid
            int result = DescriptorParser.slotIndexToParameterIndex("(I)V", 99);
            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("getParameterType returns Object.class for invalid param index")
        void getParameterType_invalidIndex_returnsObjectClass() {
            Class<?> result = DescriptorParser.getParameterType("(I)V", 99);
            assertThat(result).isEqualTo(Object.class);
        }
    }
}
