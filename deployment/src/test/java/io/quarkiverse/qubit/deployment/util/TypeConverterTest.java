package io.quarkiverse.qubit.deployment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TypeConverter}.
 *
 * <p>These tests target surviving mutations in TypeConverter,
 * particularly the getBoxedType() method which has conditional
 * checks for each primitive type.
 */
class TypeConverterTest {

    // ==================== getBoxedType Tests ====================

    @Nested
    @DisplayName("getBoxedType")
    class GetBoxedTypeTests {

        @Test
        void getBoxedType_int_returnsInteger() {
            assertThat(TypeConverter.getBoxedType(int.class)).isEqualTo(Integer.class);
        }

        @Test
        void getBoxedType_long_returnsLong() {
            assertThat(TypeConverter.getBoxedType(long.class)).isEqualTo(Long.class);
        }

        @Test
        void getBoxedType_double_returnsDouble() {
            assertThat(TypeConverter.getBoxedType(double.class)).isEqualTo(Double.class);
        }

        @Test
        void getBoxedType_float_returnsFloat() {
            // Kill mutation on line 66: type == float.class
            assertThat(TypeConverter.getBoxedType(float.class)).isEqualTo(Float.class);
        }

        @Test
        void getBoxedType_boolean_returnsBoolean() {
            assertThat(TypeConverter.getBoxedType(boolean.class)).isEqualTo(Boolean.class);
        }

        @Test
        void getBoxedType_byte_returnsByte() {
            // Kill mutation on line 70: type == byte.class
            assertThat(TypeConverter.getBoxedType(byte.class)).isEqualTo(Byte.class);
        }

        @Test
        void getBoxedType_short_returnsShort() {
            // Kill mutation on line 72: type == short.class
            assertThat(TypeConverter.getBoxedType(short.class)).isEqualTo(Short.class);
        }

        @Test
        void getBoxedType_char_returnsCharacter() {
            // Kill mutation on line 74: type == char.class
            assertThat(TypeConverter.getBoxedType(char.class)).isEqualTo(Character.class);
        }

        @Test
        void getBoxedType_String_returnsSameType() {
            // Non-primitive types should return as-is
            assertThat(TypeConverter.getBoxedType(String.class)).isEqualTo(String.class);
        }

        @Test
        void getBoxedType_Integer_returnsSameType() {
            // Already boxed types should return as-is
            assertThat(TypeConverter.getBoxedType(Integer.class)).isEqualTo(Integer.class);
        }
    }

    // ==================== isBooleanType Tests ====================

    @Nested
    @DisplayName("isBooleanType")
    class IsBooleanTypeTests {

        @Test
        void isBooleanType_primitiveBoolean_returnsTrue() {
            assertThat(TypeConverter.isBooleanType(boolean.class)).isTrue();
        }

        @Test
        void isBooleanType_boxedBoolean_returnsTrue() {
            assertThat(TypeConverter.isBooleanType(Boolean.class)).isTrue();
        }

        @Test
        void isBooleanType_int_returnsFalse() {
            assertThat(TypeConverter.isBooleanType(int.class)).isFalse();
        }

        @Test
        void isBooleanType_String_returnsFalse() {
            assertThat(TypeConverter.isBooleanType(String.class)).isFalse();
        }
    }

    // ==================== primitiveCharToClass Tests ====================

    @Nested
    @DisplayName("primitiveCharToClass")
    class PrimitiveCharToClassTests {

        @Test
        void primitiveCharToClass_I_returnsInt() {
            assertThat(TypeConverter.primitiveCharToClass('I')).isEqualTo(int.class);
        }

        @Test
        void primitiveCharToClass_J_returnsLong() {
            assertThat(TypeConverter.primitiveCharToClass('J')).isEqualTo(long.class);
        }

        @Test
        void primitiveCharToClass_D_returnsDouble() {
            assertThat(TypeConverter.primitiveCharToClass('D')).isEqualTo(double.class);
        }

        @Test
        void primitiveCharToClass_F_returnsFloat() {
            assertThat(TypeConverter.primitiveCharToClass('F')).isEqualTo(float.class);
        }

        @Test
        void primitiveCharToClass_Z_returnsBoolean() {
            assertThat(TypeConverter.primitiveCharToClass('Z')).isEqualTo(boolean.class);
        }

        @Test
        void primitiveCharToClass_B_returnsByte() {
            assertThat(TypeConverter.primitiveCharToClass('B')).isEqualTo(byte.class);
        }

        @Test
        void primitiveCharToClass_S_returnsShort() {
            assertThat(TypeConverter.primitiveCharToClass('S')).isEqualTo(short.class);
        }

        @Test
        void primitiveCharToClass_C_returnsChar() {
            assertThat(TypeConverter.primitiveCharToClass('C')).isEqualTo(char.class);
        }

        @Test
        void primitiveCharToClass_unknown_returnsObject() {
            assertThat(TypeConverter.primitiveCharToClass('X')).isEqualTo(Object.class);
        }
    }

    // ==================== isNumericType Tests ====================

    @Nested
    @DisplayName("isNumericType")
    class IsNumericTypeTests {

        @Test
        void isNumericType_int_returnsTrue() {
            assertThat(TypeConverter.isNumericType(int.class)).isTrue();
        }

        @Test
        void isNumericType_Integer_returnsTrue() {
            assertThat(TypeConverter.isNumericType(Integer.class)).isTrue();
        }

        @Test
        void isNumericType_long_returnsTrue() {
            assertThat(TypeConverter.isNumericType(long.class)).isTrue();
        }

        @Test
        void isNumericType_Long_returnsTrue() {
            assertThat(TypeConverter.isNumericType(Long.class)).isTrue();
        }

        @Test
        void isNumericType_double_returnsTrue() {
            assertThat(TypeConverter.isNumericType(double.class)).isTrue();
        }

        @Test
        void isNumericType_Double_returnsTrue() {
            assertThat(TypeConverter.isNumericType(Double.class)).isTrue();
        }

        @Test
        void isNumericType_float_returnsTrue() {
            assertThat(TypeConverter.isNumericType(float.class)).isTrue();
        }

        @Test
        void isNumericType_Float_returnsTrue() {
            assertThat(TypeConverter.isNumericType(Float.class)).isTrue();
        }

        @Test
        void isNumericType_byte_returnsTrue() {
            assertThat(TypeConverter.isNumericType(byte.class)).isTrue();
        }

        @Test
        void isNumericType_Byte_returnsTrue() {
            assertThat(TypeConverter.isNumericType(Byte.class)).isTrue();
        }

        @Test
        void isNumericType_short_returnsTrue() {
            assertThat(TypeConverter.isNumericType(short.class)).isTrue();
        }

        @Test
        void isNumericType_Short_returnsTrue() {
            assertThat(TypeConverter.isNumericType(Short.class)).isTrue();
        }

        @Test
        void isNumericType_boolean_returnsFalse() {
            assertThat(TypeConverter.isNumericType(boolean.class)).isFalse();
        }

        @Test
        void isNumericType_String_returnsFalse() {
            assertThat(TypeConverter.isNumericType(String.class)).isFalse();
        }

        @Test
        void isNumericType_char_returnsFalse() {
            assertThat(TypeConverter.isNumericType(char.class)).isFalse();
        }

        @Test
        void isNumericType_BigDecimal_returnsTrue() {
            // TEST-006: Kill mutation on Number.isAssignableFrom check
            assertThat(TypeConverter.isNumericType(java.math.BigDecimal.class)).isTrue();
        }

        @Test
        void isNumericType_BigInteger_returnsTrue() {
            // TEST-006: Kill mutation on Number.isAssignableFrom check
            assertThat(TypeConverter.isNumericType(java.math.BigInteger.class)).isTrue();
        }
    }

    // ==================== isTemporalType Tests ====================

    @Nested
    @DisplayName("isTemporalType")
    class IsTemporalTypeTests {

        @Test
        void isTemporalType_LocalDate_returnsTrue() {
            // TEST-006: Kill mutation on LocalDate.class check
            assertThat(TypeConverter.isTemporalType(java.time.LocalDate.class)).isTrue();
        }

        @Test
        void isTemporalType_LocalDateTime_returnsTrue() {
            // TEST-006: Kill mutation on LocalDateTime.class check
            assertThat(TypeConverter.isTemporalType(java.time.LocalDateTime.class)).isTrue();
        }

        @Test
        void isTemporalType_LocalTime_returnsTrue() {
            // TEST-006: Kill mutation on LocalTime.class check
            assertThat(TypeConverter.isTemporalType(java.time.LocalTime.class)).isTrue();
        }

        @Test
        void isTemporalType_String_returnsFalse() {
            // TEST-006: Kill mutation that replaces return with true
            assertThat(TypeConverter.isTemporalType(String.class)).isFalse();
        }

        @Test
        void isTemporalType_Date_returnsFalse() {
            // Old-style Date is not considered temporal type
            assertThat(TypeConverter.isTemporalType(java.util.Date.class)).isFalse();
        }

        @Test
        void isTemporalType_Instant_returnsFalse() {
            // Instant is not in the current temporal type list
            assertThat(TypeConverter.isTemporalType(java.time.Instant.class)).isFalse();
        }
    }

    // ==================== descriptorToClass Tests ====================

    @Nested
    @DisplayName("descriptorToClass")
    class DescriptorToClassTests {

        @Test
        void descriptorToClass_singleCharPrimitive_delegatesToPrimitiveCharToClass() {
            // TEST-006: Kill mutation on length == 1 check
            assertThat(TypeConverter.descriptorToClass("I")).isEqualTo(int.class);
            assertThat(TypeConverter.descriptorToClass("J")).isEqualTo(long.class);
            assertThat(TypeConverter.descriptorToClass("D")).isEqualTo(double.class);
            assertThat(TypeConverter.descriptorToClass("Z")).isEqualTo(boolean.class);
        }

        @Test
        void descriptorToClass_String_returnsStringClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/lang/String;")).isEqualTo(String.class);
        }

        @Test
        void descriptorToClass_Integer_returnsIntegerClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/lang/Integer;")).isEqualTo(Integer.class);
        }

        @Test
        void descriptorToClass_Long_returnsLongClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/lang/Long;")).isEqualTo(Long.class);
        }

        @Test
        void descriptorToClass_Boolean_returnsBooleanClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/lang/Boolean;")).isEqualTo(Boolean.class);
        }

        @Test
        void descriptorToClass_Double_returnsDoubleClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/lang/Double;")).isEqualTo(Double.class);
        }

        @Test
        void descriptorToClass_Float_returnsFloatClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/lang/Float;")).isEqualTo(Float.class);
        }

        @Test
        void descriptorToClass_BigDecimal_returnsBigDecimalClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/math/BigDecimal;")).isEqualTo(java.math.BigDecimal.class);
        }

        @Test
        void descriptorToClass_LocalDate_returnsLocalDateClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/time/LocalDate;")).isEqualTo(java.time.LocalDate.class);
        }

        @Test
        void descriptorToClass_LocalDateTime_returnsLocalDateTimeClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/time/LocalDateTime;")).isEqualTo(java.time.LocalDateTime.class);
        }

        @Test
        void descriptorToClass_LocalTime_returnsLocalTimeClass() {
            assertThat(TypeConverter.descriptorToClass("Ljava/time/LocalTime;")).isEqualTo(java.time.LocalTime.class);
        }

        @Test
        void descriptorToClass_unknown_returnsObjectClass() {
            // TEST-006: Kill mutation on default case
            assertThat(TypeConverter.descriptorToClass("Lcom/unknown/Type;")).isEqualTo(Object.class);
        }
    }
}
