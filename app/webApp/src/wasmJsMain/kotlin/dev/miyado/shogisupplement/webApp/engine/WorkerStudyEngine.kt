package dev.miyado.shogisupplement.webApp.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.engine.StudyEngine
import dev.miyado.shogisupplement.webApp.js.StudyEngineHandle
import dev.miyado.shogisupplement.webApp.js.kentoBridge
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val studyEngineJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class RawStudyScore(val cp: Int? = null, val mate: Int? = null) {
    fun toScore(): Score? = when {
        cp != null -> Score.Cp(cp)
        mate != null -> Score.Mate(mate)
        else -> null
    }
}

@Serializable
private data class RawStudyPv2(val score: RawStudyScore? = null, val pv: List<String> = emptyList())

@Serializable
private data class RawStudyPositionResult(
    val score: RawStudyScore? = null,
    val nodes: Long? = null,
    val pv: List<String> = emptyList(),
    val multipv2: RawStudyPv2? = null,
)

class WorkerStudyEngine(assetDirUrl: String) : StudyEngine {
    private val handle: StudyEngineHandle = kentoBridge().createStudyEngine(assetDirUrl)

    // Why not nodes をWorkerへ渡す: study-worker.js が解析条件を固定しており、変更できない。
    override suspend fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
        suspendCancellableCoroutine { cont ->
            handle.analyze(
                baseSfenArg = "sfen $sfen",
                movesJson = studyEngineJson.encodeToString(additionalMoves),
                onResult = { resultJson ->
                    try {
                        cont.resume(resultJson.toPvInfos())
                    } catch (error: Exception) {
                        cont.resumeWithException(error)
                    }
                },
                onError = { message -> cont.resumeWithException(IllegalStateException(message)) },
            )
            cont.invokeOnCancellation { handle.dispose() }
        }

    override fun quit() = handle.dispose()
}

private fun String.toPvInfos(): List<PvInfo> {
    val raw = studyEngineJson.decodeFromString<RawStudyPositionResult>(this)
    return buildList {
        raw.score?.toScore()?.let { score ->
            add(PvInfo(multipv = 1, score = score, pv = raw.pv, nodes = raw.nodes ?: 0L))
        }
        raw.multipv2?.score?.toScore()?.let { score ->
            add(PvInfo(multipv = 2, score = score, pv = raw.multipv2.pv, nodes = 0L))
        }
    }
}
