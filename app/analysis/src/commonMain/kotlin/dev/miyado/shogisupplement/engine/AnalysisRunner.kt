package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.crash.AlreadyReportedException
import dev.miyado.shogisupplement.crash.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 局面リストをworkers並列で解析するオーケストレーター。
 * Mutexでエンジンを排他し、局の終了時にdisposeEngineを実行する。異常終了は記録して再送出する。
 * iOSの常駐エンジンはdisposeをno-opにし、Engine.newGameで局を区切る。
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
        val positions = (0..moves.size).toList()
        val total = positions.size
        val results = arrayOfNulls<List<PvInfo>>(total)
        var doneCount = 0
        val counterMutex = Mutex()

        val semaphore = Semaphore(workers)
        // エンジンプールを Mutex で排他するキューとして管理
        val enginePool = ArrayDeque<Engine>()
        val poolMutex = Mutex()

        suspend fun acquireEngine(): Engine = poolMutex.withLock {
            if (enginePool.isNotEmpty()) enginePool.removeLast()
            else engineFactory()
        }

        suspend fun releaseEngine(engine: Engine) = poolMutex.withLock {
            enginePool.addLast(engine)
        }

        val jobs = positions.map { posIdx ->
            async(analysisIoDispatcher) {
                semaphore.withPermit {
                    val engine = acquireEngine()
                    try {
                        val prefix = moves.take(posIdx)
                        val pvList = engine.analyze(prefix)
                        results[posIdx] = pvList
                        onPositionResult?.invoke(posIdx, pvList)
                        val done = counterMutex.withLock { doneCount += 1; doneCount }
                        onProgress?.invoke(done, total)
                        releaseEngine(engine)
                    } catch (e: CancellationException) {
                        // 親スコープのキャンセルによる正常な停止。CrashReporter には送らない
                        try { disposeEngine(engine) } catch (_: Exception) {}
                        throw e
                    } catch (e: Exception) {
                        // エンジン異常終了：プールに戻さずクラッシュレポートを送信
                        try { disposeEngine(engine) } catch (_: Exception) {}
                        val done = counterMutex.withLock { doneCount }
                        val extras = buildMap {
                            put("done", done.toString())
                            put("total", total.toString())
                            put("workerId", posIdx.toString())
                            if (e is EngineAbnormalExitException) {
                                put("lastCommandName", e.lastCommandName)
                                e.exitCode?.let { code -> put("exitCode", code.toString()) }
                            }
                        }
                        crashReporter.captureException(e, extras)
                        // 送信済みマーカーで包む（上位のAnalysisService/AnalysisOrchestratorが
                        // 二重送信しないため）
                        throw AlreadyReportedException(e)
                    }
                }
            }
        }

        jobs.awaitAll()

        // プールに残っている（=異常終了せず正常に返却された）エンジンを全て解放
        withContext(analysisIoDispatcher) {
            poolMutex.withLock {
                enginePool.forEach { disposeEngine(it) }
                enginePool.clear()
            }
        }

        results.map { it ?: emptyList() }
    }
}
