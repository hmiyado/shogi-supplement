package dev.miyado.shogisupplement.board

import dev.miyado.shogisupplement.kifu.KifParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ShogiBoard.toSfen() のゴールデン照合テスト。
 *
 * scripts/ または scratchpad で python-shogi を使って生成した
 * sfen_golden_gameN.json（ply ごとの SFEN 列）と全局面で一致することを確認する。
 *
 * ゴールデンファイル形式: [{"ply":0,"sfen":"..."},{"ply":1,"sfen":"..."},...]
 */
class ShogiBoardSfenTest {

    @Serializable
    private data class SfenEntry(val ply: Int, val sfen: String)

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "resource not found: $name" }
            .readBytes().decodeToString()

    private fun loadGolden(name: String): List<SfenEntry> =
        json.decodeFromString<List<SfenEntry>>(resource(name))

    // ── ゴールデン照合本体 ────────────────────────────────────────────────────

    @Test
    fun `game1 - toSfen() が python-shogi の全局面と一致する`() {
        runSfenGoldenTest(
            kifName = "miyado_game1.kif",
            goldenName = "sfen_golden_game1.json",
        )
    }

    @Test
    fun `game2 - toSfen() が python-shogi の全局面と一致する`() {
        runSfenGoldenTest(
            kifName = "miyado_game2.kif",
            goldenName = "sfen_golden_game2.json",
        )
    }

    @Test
    fun `初期局面 SFEN が正しい`() {
        val board = ShogiBoard()
        assertEquals(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
            board.toSfen(),
        )
    }

    @Test
    fun `game1 41手目直前 ply=40 の SFEN が仕様通り`() {
        val moves = KifParser().parse(resource("miyado_game1.kif")).moves
        val board = ShogiBoard()
        for (i in 0 until 40) {
            board.push(ShogiMove.fromUsi(moves[i]))
        }
        // 仕様書で指定された SFEN と完全一致
        assertEquals(
            "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
            board.toSfen(),
        )
    }

    // ── 共通ヘルパー ──────────────────────────────────────────────────────────

    private fun runSfenGoldenTest(kifName: String, goldenName: String) {
        val moves = KifParser().parse(resource(kifName)).moves
        val golden = loadGolden(goldenName)

        val board = ShogiBoard()

        // ply=0: 初期局面
        val ply0 = golden.first { it.ply == 0 }
        assertEquals(ply0.sfen, board.toSfen(), "[$kifName] ply=0")

        // ply=1..N: 各指し手後の局面
        for (i in moves.indices) {
            board.push(ShogiMove.fromUsi(moves[i]))
            val expected = golden.first { it.ply == i + 1 }
            assertEquals(
                expected.sfen,
                board.toSfen(),
                "[$kifName] ply=${i + 1} (move=${moves[i]})",
            )
        }

        // 全件数確認: ply 0..N = moves.size+1 エントリ
        assertEquals(moves.size + 1, golden.size, "[$kifName] golden entry count")
    }
}
