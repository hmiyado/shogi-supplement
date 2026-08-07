package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val wasmResultJson = Json { ignoreUnknownKeys = true }

/**
 * WKWebView内JS（docs/kento/analysis-worker.js）が出す1局面ぶんの生の結果。
 *
 * docs/kento/webapp-bridge.js・analysis-worker.js 自体は変更しない（Web版=:webAppと
 * プロトコルを共有し続けるため）ので、この型はそちら側の出力形式（USI風の{cp}/{mate}フラット
 * スコア）にそのまま合わせる。サーバー版ワイヤ形式（[dev.miyado.shogisupplement.api.analysis.PvInfoJson]
 * の{type,value}形式）とは別物で、無理に揃えない。
 */
@Serializable
internal data class RawWasmScore(val cp: Int? = null, val mate: Int? = null) {
    fun toScore(): Score? = when {
        cp != null -> Score.Cp(cp)
        mate != null -> Score.Mate(mate)
        else -> null
    }
}

@Serializable
internal data class RawWasmPv2(val score: RawWasmScore? = null, val pv: List<String> = emptyList())

@Serializable
internal data class RawWasmPositionResult(
    val ply: Int,
    val score: RawWasmScore? = null,
    val nodes: Long? = null,
    val pv: List<String> = emptyList(),
    val multipv2: RawWasmPv2? = null,
)

/** [ply] とその局面の MultiPV 結果（[PvInfo] リスト）。 */
internal data class WasmPositionResult(val ply: Int, val pvs: List<PvInfo>)

/**
 * WKWebView内JSの "position" メッセージの result フィールド（JSON文字列）をパースする。
 *
 * @param resultJson [RawWasmPositionResult] と同じ形の JSON 文字列
 *
 * multipv=2側の nodes は常に 0 を積む: analysis-worker.js の result.multipv2 は
 * {score, pv} のみで nodes を持たない（Web版=:webApp の RawPv2 と同じ形。
 * [dev.miyado.shogisupplement.pipeline.PositionEval] 変換では pv2 の nodes を
 * 参照しないため、この欠落は既存の Web 版と同じ扱いで実害がない）。
 */
internal fun parseWasmPositionResult(resultJson: String): WasmPositionResult {
    val raw = wasmResultJson.decodeFromString(RawWasmPositionResult.serializer(), resultJson)
    val pvs = buildList {
        raw.score?.toScore()?.let { score1 ->
            add(PvInfo(multipv = 1, score = score1, pv = raw.pv, nodes = raw.nodes ?: 0L))
        }
        raw.multipv2?.score?.toScore()?.let { score2 ->
            add(PvInfo(multipv = 2, score = score2, pv = raw.multipv2.pv, nodes = 0L))
        }
    }
    return WasmPositionResult(ply = raw.ply, pvs = pvs)
}
