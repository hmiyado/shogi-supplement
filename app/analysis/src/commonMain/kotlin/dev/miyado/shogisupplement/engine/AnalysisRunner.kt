package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.crash.AlreadyReportedException
import dev.miyado.shogisupplement.crash.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 局面リストをworkers並列で解析するオーケストレーター。
 * ワーカーは固定数で、1本が1エンジンを持ち、キューから局面を取り出して解析する。
 * 生成したエンジンは正常終了・失敗・キャンセルのいずれでも必ず破棄する。異常終了は記録して再送出する。
 */
class AnalysisRunner(
    private val workers: Int = 4,
    private val crashReporter: CrashReporter,
    private val engineFactory: () -> Engine,
    private val disposeEngine: (Engine) -> Unit = { it.quit() },
) : GameAnalyzer {

    override suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> = coroutineScope {
        val total = moves.size + 1
        val results = arrayOfNulls<List<PvInfo>>(total)
        var doneCount = 0
        val counterMutex = Mutex()

        var nextPly = 0
        val queueMutex = Mutex()
        suspend fun takeNextPly(): Int? = queueMutex.withLock {
            if (nextPly < total) nextPly++ else null
        }

        val jobs = List(minOf(workers, total)) {
            async(analysisIoDispatcher) {
                val engine = engineFactory()
                var currentPly = 0
                try {
                    while (true) {
                        val ply = takeNextPly() ?: break
                        currentPly = ply
                        // Why not moves.take(ply): 局面数に対して二乗のコピーになる。
                        val pvList = engine.analyze(moves.subList(0, ply))
                        results[ply] = pvList
                        onPositionResult?.invoke(ply, pvList)
                        val done = counterMutex.withLock { doneCount += 1; doneCount }
                        onProgress?.invoke(done, total)
                    }
                } catch (e: CancellationException) {
                    // 親スコープのキャンセルによる正常な停止。CrashReporter には送らない
                    throw e
                } catch (e: Exception) {
                    val done = counterMutex.withLock { doneCount }
                    val extras = buildMap {
                        put("done", done.toString())
                        put("total", total.toString())
                        put("workerId", currentPly.toString())
                        if (e is EngineAbnormalExitException) {
                            put("lastCommandName", e.lastCommandName)
                            e.exitCode?.let { code -> put("exitCode", code.toString()) }
                        }
                    }
                    crashReporter.captureException(e, extras)
                    // 送信済みマーカーで包む（上位のAnalysisService/AnalysisOrchestratorが
                    // 二重送信しないため）
                    throw AlreadyReportedException(e)
                } finally {
                    // 兄弟ワーカーの失敗でキャンセルされた場合もここを通り、エンジンが残らない。
                    try { disposeEngine(engine) } catch (_: Exception) {}
                }
            }
        }

        jobs.awaitAll()

        results.map { it ?: emptyList() }
    }
}
