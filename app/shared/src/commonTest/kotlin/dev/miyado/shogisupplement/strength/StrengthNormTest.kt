package dev.miyado.shogisupplement.strength

import kotlin.test.Test
import kotlin.test.assertEquals

class StrengthNormTest {

    @Test
    fun 平均レート付近は偏差値50() {
        assertEquals(50, StrengthNorm.deviationScore(1718))
    }

    @Test
    fun 帯v2境界レートの換算() {
        assertEquals(31, StrengthNorm.deviationScore(1604))
        assertEquals(48, StrengthNorm.deviationScore(1703))
        assertEquals(63, StrengthNorm.deviationScore(1801))
        assertEquals(80, StrengthNorm.deviationScore(1900))
    }

    @Test
    fun 誤差幅の換算() {
        assertEquals(27, StrengthNorm.deviationWidth(700))
        assertEquals(25, StrengthNorm.deviationWidth(650))
        assertEquals(23, StrengthNorm.deviationWidth(600))
        assertEquals(22, StrengthNorm.deviationWidth(560))
    }

    @Test
    fun 表示文字列() {
        assertEquals("50 ±25", StrengthEstimate(1718, ClampState.NONE, 650, 800).toDisplayString())
    }

    @Test
    fun 表示文字列_clampedは現在使われないが値としては保持される() {
        // v2は常にNONEを返すが、型としてはCLAMPED_HIGH/LOWを引き続き許容する。
        assertEquals("80+ ±22", StrengthEstimate(1900, ClampState.CLAMPED_HIGH, 560, 2500).toDisplayString())
        assertEquals("31未満 ±27", StrengthEstimate(1604, ClampState.CLAMPED_LOW, 700, 200).toDisplayString())
    }
}
