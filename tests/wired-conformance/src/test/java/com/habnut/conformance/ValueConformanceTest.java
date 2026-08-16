package com.habnut.conformance;

import com.habnut.emulator.wired.WiredValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wired value model: three types, and the arithmetic, comparison, text and
 * conversion operators defined over them.
 *
 * Builders can combine any operator with any type, so the coercion rules are
 * the part most likely to surprise, and are covered explicitly here.
 */
@DisplayName("Wired value semantics")
class ValueConformanceTest {

    @Nested
    @DisplayName("conversion")
    class Conversion {

        @Test
        void numberConvertsAcrossTypes() {
            WiredValue n = WiredValue.ofNumber(42);
            assertEquals(42, n.asNumber());
            assertEquals("42", n.asText(), "whole numbers render without a decimal point");
            assertTrue(n.asBool(), "non-zero is true");
            assertFalse(WiredValue.ofNumber(0).asBool(), "zero is false");
        }

        @Test
        void fractionalNumbersKeepTheirDecimal() {
            assertEquals("2.5", WiredValue.ofNumber(2.5).asText());
        }

        @Test
        void textConvertsAcrossTypes() {
            assertEquals(7, WiredValue.ofText("7").asNumber());
            assertEquals(0, WiredValue.ofText("nonsense").asNumber(),
                "unparseable text reads as zero rather than throwing");
            assertTrue(WiredValue.ofText("anything").asBool());
        }

        @Test
        void textFalsinessFollowsTheSpecifiedWords() {
            assertFalse(WiredValue.ofText("").asBool());
            assertFalse(WiredValue.ofText("false").asBool());
            assertFalse(WiredValue.ofText("FALSE").asBool(), "case-insensitive");
            assertFalse(WiredValue.ofText("0").asBool());
            assertTrue(WiredValue.ofText("true").asBool());
        }

        @Test
        void boolConvertsAcrossTypes() {
            assertEquals(1, WiredValue.TRUE.asNumber());
            assertEquals(0, WiredValue.FALSE.asNumber());
            assertEquals("true", WiredValue.TRUE.asText());
            assertEquals("false", WiredValue.FALSE.asText());
        }

        @Test
        void nullTextBecomesEmptyRatherThanNull() {
            assertEquals("", WiredValue.ofText(null).asText());
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void basicOperators() {
            WiredValue six = WiredValue.ofNumber(6);
            WiredValue three = WiredValue.ofNumber(3);
            assertEquals(9, six.add(three).asNumber());
            assertEquals(3, six.sub(three).asNumber());
            assertEquals(18, six.mul(three).asNumber());
            assertEquals(2, six.div(three).asNumber());
            assertEquals(0, six.mod(three).asNumber());
            assertEquals(216, six.pow(three).asNumber());
        }

        @Test
        void divisionByZeroYieldsZeroInsteadOfFailing() {
            assertEquals(0, WiredValue.ofNumber(5).div(WiredValue.ZERO).asNumber(),
                "a builder dividing by zero must not break the stack");
            assertEquals(0, WiredValue.ofNumber(5).mod(WiredValue.ZERO).asNumber());
        }

        @Test
        void addConcatenatesWhenEitherSideIsText() {
            assertEquals("ab", WiredValue.ofText("a").add(WiredValue.ofText("b")).asText());
            assertEquals("score: 10",
                WiredValue.ofText("score: ").add(WiredValue.ofNumber(10)).asText());
            assertEquals("10 points",
                WiredValue.ofNumber(10).add(WiredValue.ofText(" points")).asText());
        }

        @Test
        void nonAddOperatorsCoerceTextToNumber() {
            assertEquals(6, WiredValue.ofText("2").mul(WiredValue.ofNumber(3)).asNumber());
        }
    }

    @Nested
    @DisplayName("comparison")
    class Comparison {

        @Test
        void numericComparison() {
            WiredValue five = WiredValue.ofNumber(5);
            WiredValue ten  = WiredValue.ofNumber(10);
            assertTrue(five.lt(ten));
            assertTrue(five.lte(ten));
            assertTrue(ten.gt(five));
            assertTrue(ten.gte(five));
            assertFalse(five.gt(ten));
        }

        @Test
        void boundariesAreInclusiveForLteAndGte() {
            WiredValue five = WiredValue.ofNumber(5);
            assertTrue(five.lte(WiredValue.ofNumber(5)));
            assertTrue(five.gte(WiredValue.ofNumber(5)));
            assertFalse(five.lt(WiredValue.ofNumber(5)));
            assertFalse(five.gt(WiredValue.ofNumber(5)));
        }

        @Test
        void equalityComparesAsTextWhenEitherSideIsText() {
            assertTrue(WiredValue.ofText("5").eq(WiredValue.ofNumber(5)),
                "a number typed into a text field still matches");
            assertTrue(WiredValue.ofText("abc").eq(WiredValue.ofText("abc")));
            assertFalse(WiredValue.ofText("abc").eq(WiredValue.ofText("ABC")),
                "text equality is case-sensitive");
        }

        @Test
        void equalityBetweenBoolsComparesTheFlag() {
            assertTrue(WiredValue.TRUE.eq(WiredValue.ofBool(true)));
            assertFalse(WiredValue.TRUE.eq(WiredValue.FALSE));
        }
    }

    @Nested
    @DisplayName("text operators")
    class Text {

        @Test
        void containsLengthAndCase() {
            WiredValue hello = WiredValue.ofText("Hello World");
            assertTrue(hello.contains(WiredValue.ofText("World")).asBool());
            assertFalse(hello.contains(WiredValue.ofText("world")).asBool(),
                "contains is case-sensitive");
            assertEquals(11, hello.length().asNumber());
            assertEquals("HELLO WORLD", hello.toUpper().asText());
            assertEquals("hello world", hello.toLower().asText());
        }

        @Test
        void trimRemovesSurroundingWhitespaceOnly() {
            assertEquals("a b", WiredValue.ofText("  a b  ").trim().asText());
        }

        @Test
        void everyValueContainsTheEmptyString() {
            assertTrue(WiredValue.ofText("anything").contains(WiredValue.EMPTY).asBool());
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        void constantsHoldTheirDocumentedValues() {
            assertEquals(0, WiredValue.ZERO.asNumber());
            assertEquals(1, WiredValue.ONE.asNumber());
            assertEquals("", WiredValue.EMPTY.asText());
            assertTrue(WiredValue.TRUE.asBool());
            assertFalse(WiredValue.FALSE.asBool());
        }

        @Test
        void equalsRequiresMatchingTypeAndValue() {
            assertEquals(WiredValue.ofNumber(5), WiredValue.ofNumber(5));
            assertNotEquals(WiredValue.ofNumber(5), WiredValue.ofText("5"),
                "same rendering, different type");
        }

        @Test
        void equalValuesShareAHashCode() {
            assertEquals(WiredValue.ofText("x").hashCode(), WiredValue.ofText("x").hashCode());
        }
    }
}
