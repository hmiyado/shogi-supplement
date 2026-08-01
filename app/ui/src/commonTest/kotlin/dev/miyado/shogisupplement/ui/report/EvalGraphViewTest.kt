package dev.miyado.shogisupplement.ui.report

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * plyFromX（グラフのx座標→ply変換）の単体テスト。
 * タップ・ドラッグ双方のポインタ処理が共有する純粋関数のため、Composeのポインタ入力
 * シミュレーションなしで境界値を直接検証できる。
 */
class EvalGraphViewTest {

    @Test
    fun `左端は0`() {
        assertEquals(0, plyFromX(0f, 360, 40))
    }

    @Test
    fun `右端はeffectiveMaxPly`() {
        assertEquals(40, plyFromX(360f, 360, 40))
    }

    @Test
    fun `中央は概ね半分のply`() {
        assertEquals(20, plyFromX(180f, 360, 40))
    }

    @Test
    fun `幅を超える座標はeffectiveMaxPlyにクランプする`() {
        assertEquals(40, plyFromX(500f, 360, 40))
    }

    @Test
    fun `負の座標は0にクランプする`() {
        assertEquals(0, plyFromX(-50f, 360, 40))
    }

    @Test
    fun `幅0のときは常に0を返す`() {
        assertEquals(0, plyFromX(100f, 0, 40))
    }

    @Test
    fun `端数は四捨五入する`() {
        // 360px幅・40plyのとき1plyあたり9px。x=13pxは1.44ply→四捨五入で1。
        assertEquals(1, plyFromX(13f, 360, 40))
        // x=5pxは0.56ply→四捨五入で1。
        assertEquals(1, plyFromX(5f, 360, 40))
        // x=4pxは0.44ply→四捨五入で0。
        assertEquals(0, plyFromX(4f, 360, 40))
    }
}
