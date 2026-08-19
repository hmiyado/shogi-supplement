package dev.miyado.shogisupplement.ui.strength

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * nearestPointIndex（推移グラフのx座標→最も近い点のインデックス変換）の単体テスト。
 * [dev.miyado.shogisupplement.ui.report.EvalGraphViewTest]（plyFromX）と同じ理由で
 * Composeのポインタ入力シミュレーションなしで境界値を直接検証できる。
 */
class EstimatedStrengthDetailScreenTest {

    @Test
    fun `左端は0番目`() {
        assertEquals(0, nearestPointIndex(0f, 350, 8))
    }

    @Test
    fun `右端は最後の点`() {
        assertEquals(7, nearestPointIndex(350f, 350, 8))
    }

    @Test
    fun `中央は概ね中間の点`() {
        assertEquals(4, nearestPointIndex(175f, 350, 8))
    }

    @Test
    fun `幅を超える座標は最後の点にクランプする`() {
        assertEquals(7, nearestPointIndex(999f, 350, 8))
    }

    @Test
    fun `負の座標は0番目にクランプする`() {
        assertEquals(0, nearestPointIndex(-50f, 350, 8))
    }

    @Test
    fun `点が1つ以下のときは常に0を返す`() {
        assertEquals(0, nearestPointIndex(100f, 350, 1))
        assertEquals(0, nearestPointIndex(100f, 350, 0))
    }

    @Test
    fun `幅0のときは常に0を返す`() {
        assertEquals(0, nearestPointIndex(10f, 0, 8))
    }
}
