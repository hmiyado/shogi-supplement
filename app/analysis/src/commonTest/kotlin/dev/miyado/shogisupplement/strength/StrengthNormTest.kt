package dev.miyado.shogisupplement.strength

import kotlin.test.Test
import kotlin.test.assertEquals

class StrengthNormTest {

    @Test
    fun 平均レート付近は偏差値50() {
        assertEquals("50.0", formatStrengthDecimal(StrengthNorm.deviationScore(1718)))
    }

    @Test
    fun 帯v2境界レートの換算() {
        assertEquals("31.4", formatStrengthDecimal(StrengthNorm.deviationScore(1604)))
        assertEquals("47.5", formatStrengthDecimal(StrengthNorm.deviationScore(1703)))
        assertEquals("63.5", formatStrengthDecimal(StrengthNorm.deviationScore(1801)))
        assertEquals("79.6", formatStrengthDecimal(StrengthNorm.deviationScore(1900)))
    }

    @Test
    fun 誤差幅の換算() {
        assertEquals("27.3", formatStrengthDecimal(StrengthNorm.deviationWidth(700)))
        assertEquals("25.4", formatStrengthDecimal(StrengthNorm.deviationWidth(650)))
        assertEquals("23.4", formatStrengthDecimal(StrengthNorm.deviationWidth(600)))
        assertEquals("21.9", formatStrengthDecimal(StrengthNorm.deviationWidth(560)))
    }

    @Test
    fun 表示文字列() {
        assertEquals("50.0 ±25.4", StrengthEstimate(1718, ClampState.NONE, 650, 800).toDisplayString())
    }

    @Test
    fun 表示文字列_clampedは現在使われないが値としては保持される() {
        // v2は常にNONEを返すが、型としてはCLAMPED_HIGH/LOWを引き続き許容する。
        assertEquals("79.6+ ±21.9", StrengthEstimate(1900, ClampState.CLAMPED_HIGH, 560, 2500).toDisplayString())
        assertEquals("31.4未満 ±27.3", StrengthEstimate(1604, ClampState.CLAMPED_LOW, 700, 200).toDisplayString())
    }

    @Test
    fun 桁と符号と繰り上がり() {
        assertEquals("50.0", formatStrengthDecimal(50.0))
        assertEquals("50.0", formatStrengthDecimal(49.96))
        assertEquals("-1.3", formatStrengthDecimal(-1.25))
        assertEquals("0.0", formatStrengthDecimal(-0.01))
    }

    @Test
    fun 推定範囲は丸め前の値から算出する() {
        val score = StrengthNorm.deviationScore(1750)
        val width = StrengthNorm.deviationWidth(280)
        assertEquals(10.9375, width)
        assertEquals("推定範囲 44.3–66.1", dev.miyado.shogisupplement.text.AppStrings.strengthDetailRange(score - width, score + width))
    }
}
