package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 解析リクエストの入力検証（上限と形式）が、エンジンへ渡す前に不正な入力を弾くことを保証する。 */
class AnalysisInputTest {

    private val initialSfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"

    @Test
    fun `moves_usi と sfen のどちらも無ければ不正`() {
        assertIs<EngineInputResult.Invalid>(AnalysisRequest().toEngineInput())
    }

    @Test
    fun `通常の指し手・成り・打ちを受け付ける`() {
        val request = AnalysisRequest(movesUsi = listOf("7g7f", "8h2b+", "P*5e"))
        val result = assertIs<EngineInputResult.Valid>(request.toEngineInput())
        assertEquals(EngineInput.Game(listOf("7g7f", "8h2b+", "P*5e")), result.input)
    }

    @Test
    fun `手数が上限ちょうどなら通り、1手超えると不正`() {
        val limit = AnalysisInputLimits.MAX_MOVES
        assertIs<EngineInputResult.Valid>(AnalysisRequest(movesUsi = List(limit) { "7g7f" }).toEngineInput())
        assertIs<EngineInputResult.Invalid>(AnalysisRequest(movesUsi = List(limit + 1) { "7g7f" }).toEngineInput())
    }

    @Test
    fun `USI指し手として読めない文字列は不正`() {
        listOf("resign", "7g7f7f", "0a1b", "7g7f++", "K*5e", "７六歩", "").forEach { move ->
            assertIs<EngineInputResult.Invalid>(
                AnalysisRequest(movesUsi = listOf(move)).toEngineInput(),
                "$move は弾かれるはず",
            )
        }
    }

    @Test
    fun `初期局面のSFENを受け付ける`() {
        val request = AnalysisRequest(sfen = initialSfen, moves = listOf("7g7f"))
        val result = assertIs<EngineInputResult.Valid>(request.toEngineInput())
        assertEquals(EngineInput.Position(initialSfen, listOf("7g7f")), result.input)
    }

    @Test
    fun `持ち駒つき・手数省略のSFENも受け付ける`() {
        val withHands = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w 2P3p"
        assertIs<EngineInputResult.Valid>(AnalysisRequest(sfen = withHands).toEngineInput())
    }

    @Test
    fun `段数・手番・持ち駒の形式が崩れたSFENは不正`() {
        listOf(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1 b - 1",
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL x - 1",
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b ? 1",
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - x",
            "lnsgkgsnl b -",
            "",
        ).forEach { sfen ->
            assertIs<EngineInputResult.Invalid>(AnalysisRequest(sfen = sfen).toEngineInput(), "$sfen は弾かれるはず")
        }
    }

    @Test
    fun `SFENが上限を超える長さなら不正`() {
        val long = "l".repeat(AnalysisInputLimits.MAX_SFEN_LENGTH + 1)
        assertIs<EngineInputResult.Invalid>(AnalysisRequest(sfen = long).toEngineInput())
    }

    @Test
    fun `単発局面に付ける追加手も指し手として検証する`() {
        val request = AnalysisRequest(sfen = initialSfen, moves = listOf("resign"))
        assertIs<EngineInputResult.Invalid>(request.toEngineInput())
    }
}
