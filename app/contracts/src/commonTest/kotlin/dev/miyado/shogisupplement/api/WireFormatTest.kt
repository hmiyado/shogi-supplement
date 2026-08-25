package dev.miyado.shogisupplement.api

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.analysis.PositionPayloadJson
import dev.miyado.shogisupplement.api.analysis.PositionResultJson
import dev.miyado.shogisupplement.api.analysis.ProgressJson
import dev.miyado.shogisupplement.api.analysis.PvInfoJson
import dev.miyado.shogisupplement.api.analysis.QuotaExceededJson
import dev.miyado.shogisupplement.api.analysis.ScoreJson
import dev.miyado.shogisupplement.api.transfer.TransferRequest
import dev.miyado.shogisupplement.api.transfer.TransferSessionJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ワイヤ形式のキー名と構造が変わっていないことを保証する。
 *
 * クライアントとWorkerは別々に配布されるため、片方だけ更新された状態で通信し得る。
 * ここが変わると、更新していない側が読めなくなる。キーを変えるときはこのテストを
 * 意図して書き換え、両側の配布順（サーバー先行）まで含めて判断する。
 */
class WireFormatTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun 解析リクエストのキー名() {
        assertEquals(
            """{"moves_usi":["7g7f","3c3d"],"sfen":null,"moves":null}""",
            json.encodeToString(AnalysisRequest(movesUsi = listOf("7g7f", "3c3d"))),
        )
    }

    @Test
    fun 解析結果のキー名と入れ子() {
        val result = AnalysisResultJson(
            result = listOf(
                listOf(PvInfoJson(multipv = 1, score = ScoreJson("cp", 42), pv = listOf("7g7f"), nodes = 400_000L)),
            ),
            engineMeta = EngineMetaJson(
                engineRev = "rev",
                evalSha256 = "sha",
                nodes = 400_000,
                threads = 1,
                multiPv = 2,
                usiHash = 128,
                fvScale = 20,
            ),
        )
        assertEquals(
            """{"result":[[{"multipv":1,"score":{"type":"cp","value":42},"pv":["7g7f"],"nodes":400000}]],""" +
                """"engine_meta":{"engine_rev":"rev","eval_sha256":"sha","nodes":400000,"threads":1,""" +
                """"multi_pv":2,"usi_hash":128,"fv_scale":20}}""",
            json.encodeToString(result),
        )
    }

    @Test
    fun NDJSONの進捗行と局面結果行のキー名() {
        assertEquals("""{"progress":3,"total":10}""", json.encodeToString(ProgressJson(3, 10)))
        assertEquals(
            """{"position":{"ply":5,"pvs":[]}}""",
            json.encodeToString(PositionResultJson(PositionPayloadJson(ply = 5, pvs = emptyList()))),
        )
    }

    @Test
    fun エラー応答のキー名() {
        assertEquals("""{"error":"boom"}""", json.encodeToString(ErrorJson("boom")))
        assertEquals(
            """{"error":"quota_exceeded","reset_at":"2026-08-26T00:00:00+09:00"}""",
            json.encodeToString(QuotaExceededJson(resetAt = "2026-08-26T00:00:00+09:00")),
        )
    }

    @Test
    fun 引き継ぎのキー名() {
        assertEquals("""{"k_auth":"secret"}""", json.encodeToString(TransferRequest(kAuth = "secret")))
        assertEquals(
            """{"access_token":"a","refresh_token":"r"}""",
            json.encodeToString(TransferSessionJson(accessToken = "a", refreshToken = "r")),
        )
    }

    @Test
    fun HTTPヘッダー名() {
        assertEquals("X-Firebase-AppCheck", ApiHeaders.APP_CHECK)
        assertEquals("X-App-Platform", ApiHeaders.APP_PLATFORM)
        assertEquals("X-App-Build", ApiHeaders.APP_BUILD)
    }
}
