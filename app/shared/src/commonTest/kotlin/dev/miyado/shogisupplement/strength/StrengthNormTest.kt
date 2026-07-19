package dev.miyado.shogisupplement.strength

import kotlin.test.Test
import kotlin.test.assertEquals

class StrengthNormTest {

    @Test
    fun 平均レートは偏差値50() {
        assertEquals(50, StrengthNorm.deviationScore(1666))
    }

    @Test
    fun 帯端レートの換算() {
        assertEquals(36, StrengthNorm.deviationScore(1300))
        assertEquals(47, StrengthNorm.deviationScore(1600))
        assertEquals(59, StrengthNorm.deviationScore(1900))
        assertEquals(71, StrengthNorm.deviationScore(2200))
    }

    @Test
    fun 誤差幅の換算() {
        assertEquals(27, StrengthNorm.deviationWidth(700))
        assertEquals(25, StrengthNorm.deviationWidth(650))
        assertEquals(23, StrengthNorm.deviationWidth(600))
        assertEquals(22, StrengthNorm.deviationWidth(560))
    }

    @Test
    fun 表示文字列_補間範囲内() {
        assertEquals("51 ±25", StrengthEstimate(1680, ClampState.NONE, 650, 800).toDisplayString())
    }

    @Test
    fun 表示文字列_最強側クランプ() {
        assertEquals("77+ ±22", StrengthEstimate(2350, ClampState.CLAMPED_HIGH, 560, 2500).toDisplayString())
    }

    @Test
    fun 表示文字列_最弱側クランプ() {
        assertEquals("30未満 ±27", StrengthEstimate(1150, ClampState.CLAMPED_LOW, 700, 200).toDisplayString())
    }
}
