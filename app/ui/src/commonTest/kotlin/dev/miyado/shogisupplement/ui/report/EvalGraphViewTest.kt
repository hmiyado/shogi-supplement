package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.db.PositionEvalRow
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

    // mate_in=0（手番側が詰まされている局面）はエンジンが終局局面に対して返す符号なしの値。
    // 符号だけでは勝敗が決まらないため、plyの偶奇から勝敗を判定する規約（PositionEvalDisplayと
    // 同じ）をグラフでも守れているかを検証する。

    @Test
    fun `mate_in0で手番側が後手のときは先手が詰ませた勝ちとして上端にクランプする`() {
        // ply=1（奇数）=後手番=後手が詰まされている=先手の勝ち。
        val points = buildEvalGraphPoints(listOf(PositionEvalRow(ply = 1, scoreCp = null, mateIn = 0)))
        assertEquals(EVAL_GRAPH_CLAMP_CP, points.single().clampedCp)
    }

    @Test
    fun `mate_in0で手番側が先手のときは先手が詰まされた負けとして下端にクランプする`() {
        // ply=0（偶数）=先手番=先手が詰まされている=後手の勝ち。
        val points = buildEvalGraphPoints(listOf(PositionEvalRow(ply = 0, scoreCp = null, mateIn = 0)))
        assertEquals(-EVAL_GRAPH_CLAMP_CP, points.single().clampedCp)
    }

    @Test
    fun `mate_in0でも自分視点への反転は通常のmateInと同じ規約に従う`() {
        // ply=1（後手が詰まされ先手の勝ち）をユーザー後手視点で見ると自分の負け=下端。
        val points = buildEvalGraphPoints(
            listOf(PositionEvalRow(ply = 1, scoreCp = null, mateIn = 0)),
            userIsGote = true,
        )
        assertEquals(-EVAL_GRAPH_CLAMP_CP, points.single().clampedCp)
    }
}
