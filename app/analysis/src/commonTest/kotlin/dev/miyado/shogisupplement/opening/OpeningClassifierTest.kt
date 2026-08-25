package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 定義が持つ手順の例で、その形が成立する／近い形では成立しないことを確かめる。
 * 例は資料（app/docs/opening）にもそのまま載るので、テストと資料が同じものを見る。
 */
class OpeningClassifierTest {

    @Test
    fun 定義の手順の例が期待どおりに判定される() {
        val failures = mutableListOf<String>()
        PLACEMENT_DEFS.forEach { def ->
            def.samples.forEach { sample ->
                val result = OpeningClassifier.classify(sample.usiMoves).of(Side.BLACK)
                val hit = when (def.kind) {
                    OpeningKind.CASTLE -> def.name in result.achievedCastles
                    OpeningKind.STRATEGY -> def.name in result.tags
                }
                if (hit != sample.matches) {
                    failures += "${def.name}: ${sample.label} は ${sample.matches} を期待したが $hit（$result）"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun 出来事で判定する戦型の手順の例が期待どおりに判定される() {
        val failures = mutableListOf<String>()
        EVENT_STRATEGY_DEFS.forEach { def ->
            def.samples.forEach { sample ->
                val result = OpeningClassifier.classify(sample.usiMoves)
                val hit = def.name in result.black.tags || def.name in result.white.tags
                if (hit != sample.matches) {
                    failures += "${def.name}: ${sample.label} は ${sample.matches} を期待したが $hit（$result）"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun すべての定義に成立と不成立の手順がある() {
        val missing = mutableListOf<String>()
        fun check(name: String, samples: List<OpeningSample>) {
            if (samples.none { it.matches }) missing += "$name: 成立する手順が無い"
            if (samples.none { !it.matches }) missing += "$name: 成立しない手順が無い"
        }
        PLACEMENT_DEFS.forEach { check(it.name, it.samples) }
        EVENT_STRATEGY_DEFS.forEach { check(it.name, it.samples) }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }

    @Test
    fun 美濃は発展した形を表示し達成の記録は残る() {
        val takamino = PLACEMENT_DEFS.first { it.name == "高美濃囲い" }.samples.first { it.matches }
        val result = OpeningClassifier.classify(takamino.usiMoves).of(Side.BLACK)
        assertEquals("高美濃囲い", result.castle)
    }

    @Test
    fun 発展形は元の囲いを経由していなくても単体で成立する() {
        // 左金を6九→6八→5七→4七と回して高美濃の形にする。本美濃の左金5八は経由しない。
        val direct = usi(
            "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d 3i3h 3c3d 4g4f 7c7d " +
                "5g5f 1d1e 6i6h 9d9e 6h5g 1e1f 5g4g 9e9f",
        )
        val result = OpeningClassifier.classify(direct).of(Side.BLACK)
        assertTrue("高美濃囲い" in result.achievedCastles, result.toString())
        assertFalse("本美濃囲い" in result.achievedCastles, "本美濃を経由していない")
    }

    @Test
    fun 振った筋で戦型が決まる() {
        fun styleOf(moves: String) = OpeningClassifier.classify(usi(moves)).black.style

        assertEquals("四間飛車", styleOf("2h6h 3c3d"))
        assertEquals("三間飛車", styleOf("2h7h 3c3d"))
        assertEquals("中飛車", styleOf("2h5h 3c3d"))
        assertEquals("向かい飛車", styleOf("2h8h 3c3d"))
        assertEquals("袖飛車", styleOf("3g3f 3c3d 2h3h 8c8d"))
    }

    @Test
    fun 居飛車のままの対局は判定しない() {
        val ibisha = usi("7g7f 3c3d 2g2f 8c8d 2f2e 8d8e 6i7h 4a3b")
        assertEquals("未分類", OpeningClassifier.classify(ibisha).black.style)
    }

    @Test
    fun 最初に振った筋で確定しその後の飛車回りでは変わらない() {
        // 6筋へ振ったあと8筋へ回しても四間飛車のまま。
        val moves = usi("2h6h 3c3d 6h8h 8c8d")
        assertEquals("四間飛車", OpeningClassifier.classify(moves).black.style)
    }

    @Test
    fun 序盤を過ぎてからの飛車回りは戦型として数えない() {
        val late = usi(
            "7g7f 3c3d 2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 3i3h 2b3c 3h2g 7a6b 2g3f 6c6d " +
                "1g1f 1c1d 9g9f 9c9d 5i5h 5c5d 5h6h 5a5b 4g4f 4c4d 2h6h 6b5c",
        )
        assertEquals("未分類", OpeningClassifier.classify(late).black.style, "26手目の飛車回りは数えない")
    }

    @Test
    fun 相居飛車の三大戦型と派生() {
        val aigakari = OpeningClassifier.classify(usi("2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d"))
        assertTrue("相掛かり" in aigakari.black.tags && "相掛かり" in aigakari.white.tags)

        val yokofudori = OpeningClassifier.classify(
            usi("7g7f 3c3d 2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d 8e8f 8g8f 8b8f 2d3d"),
        )
        assertTrue("横歩取り" in yokofudori.black.tags)
        assertTrue("相掛かり" in yokofudori.black.tags, "横歩取りは相掛かりの派生として両方残る")

        val kakugawari = OpeningClassifier.classify(
            usi("7g7f 8c8d 2g2f 4a3b 2f2e 8d8e 8h7g 3c3d 7i6h 2b7g+ 6h7g 3a2b"),
        )
        assertTrue("角換わり" in kakugawari.black.tags)
        assertFalse("相掛かり" in kakugawari.black.tags, "角交換した将棋は相掛かりにしない")
        assertFalse("一手損角換わり" in kakugawari.white.tags, "上がってきた角を取る形は手損ではない")

        val itteson = OpeningClassifier.classify(usi("7g7f 3c3d 2g2f 2b8h+ 7i8h 3a2b"))
        assertTrue("一手損角換わり" in itteson.white.tags)
        assertFalse("一手損角換わり" in itteson.black.tags, "手損した後手にだけ付ける")
    }
}
