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
        assertEquals(114, StrengthNorm.deviationWidth(700))
        assertEquals(106, StrengthNorm.deviationWidth(650))
        assertEquals(98, StrengthNorm.deviationWidth(600))
        assertEquals(91, StrengthNorm.deviationWidth(560))
    }

    @Test
    fun 表示文字列() {
        assertEquals("50 ±106", StrengthEstimate(1718, ClampState.NONE, 650, 800).toDisplayString())
    }

    @Test
    fun 表示文字列_clampedは現在使われないが値としては保持される() {
        // v2の線形式は値域を持たないため estimate()/aggregate() は常に NONE を返すが、
        // 型としては引き続き CLAMPED_HIGH/LOW を許容する（表示フォーマット自体は変えない）。
        assertEquals("80+ ±91", StrengthEstimate(1900, ClampState.CLAMPED_HIGH, 560, 2500).toDisplayString())
        assertEquals("31未満 ±114", StrengthEstimate(1604, ClampState.CLAMPED_LOW, 700, 200).toDisplayString())
    }
}
