package dev.miyado.shogisupplement.webApp.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.pipeline.PositionEval
import dev.miyado.shogisupplement.webApp.js.kentoBridge
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class RawScore(val cp: Int? = null, val mate: Int? = null) {
    fun toScore(): Score? = when {
        cp != null -> Score.Cp(cp)
        mate != null -> Score.Mate(mate)
        else -> null
    }
}

@Serializable
private data class RawPv2(val score: RawScore? = null, val pv: List<String> = emptyList())

@Serializable
private data class RawPositionResult(
    val ply: Int,
    val score: RawScore? = null,
    val pv: List<String> = emptyList(),
    val multipv2: RawPv2? = null,
)

suspend fun checkEngineAssetsAvailable(): Boolean = suspendCancellableCoroutine { cont ->
    kentoBridge().checkAssetsAvailable(ASSET_BASE_URL) { available -> cont.resume(available) }
}

suspend fun fetchTextAsset(url: String): String = suspendCancellableCoroutine { cont ->
    kentoBridge().fetchText(
        url,
        onOk = { cont.resume(it) },
        onError = { message -> cont.resumeWithException(IllegalStateException(message)) },
    )
}

/**
 * moves.size+1局面（0手目=開始局面を含む）を解析し、ply昇順の [PositionEval] リストを返す。
 * 呼び出し元のコルーチンがキャンセルされたら、進行中のWorkerも即terminateする
 * （invokeOnCancellationでキャンセルハンドルを解放する）。
 */
suspend fun runEngineAnalysis(
    baseSfenArg: String,
    moves: List<String>,
    onProgress: (done: Int, total: Int) -> Unit,
): List<PositionEval> {
    val assetDirUrl = suspendCancellableCoroutine<String> { cont ->
        kentoBridge().resolveAssetDirUrl(
            ASSET_BASE_URL,
            onOk = { cont.resume(it) },
            onError = { message -> cont.resumeWithException(IllegalStateException(message)) },
        )
    }

    val total = moves.size + 1
    val results = arrayOfNulls<PositionEval>(total)
    var done = 0
    val movesJson = json.encodeToString(moves)

    suspendCancellableCoroutine<Unit> { cont ->
        val handle = kentoBridge().runAnalysis(
            baseSfenArg = baseSfenArg,
            movesJson = movesJson,
            assetDirUrl = assetDirUrl,
            onPosition = { resultJson ->
                val raw = json.decodeFromString<RawPositionResult>(resultJson)
                results[raw.ply] = PositionEval(
                    score = raw.score?.toScore(),
                    pv = raw.pv,
                    pv2Score = raw.multipv2?.score?.toScore(),
                    pv2MoveUsi = raw.multipv2?.pv?.firstOrNull(),
                )
                done += 1
                onProgress(done, total)
            },
            onDone = { cont.resume(Unit) },
            onError = { message -> cont.resumeWithException(IllegalStateException(message)) },
        )
        cont.invokeOnCancellation { handle.cancel() }
    }

    return results.mapIndexed { ply, value ->
        value ?: PositionEval(score = null, pv = emptyList())
    }
}
