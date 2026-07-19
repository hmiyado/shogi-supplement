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
 * 局面リストを最大 [workers] プロセス/インスタンス並列で解析するオーケストレーター。
 *
 * Android専用の構築（android.content.pm.ApplicationInfo・java.io.File を使う本番用コンストラクタ）は
 * androidApp の `createAndroidAnalysisRunner()`（engine/AndroidAnalysisRunnerFactory.kt）が担う。
 * 排他制御には `kotlinx.coroutines.sync.Mutex`（全プラットフォーム共通）を使う
 * （`kotlin.synchronized` は Kotlin/Native の commonMain では使えないため）。
 *
 * 各ワーカーは [engineFactory] が返す [Engine] を取得・プールし、Semaphore でアクセスを排他する。
 * 局の解析が終わったらプール中のエンジン全てに対して [disposeEngine] を呼ぶ:
 * - Android（デフォルト）: `{ it.quit() }`（毎局プロセスを終了する既存挙動を保存）
 * - iOS: 常駐インスタンスを維持するため no-op（quitしない。局の区切りは
 *   [Engine.newGame] を使う。呼び出し側=iOS用エンジンファクトリ側の責務）
 *
 * エンジンが異常終了した場合（[EngineAbnormalExitException] またはその他の例外）は
 * [crashReporter] にイベントを送信し、例外を再スローする。
 */
class AnalysisRunner(
    private val workers: Int = 4,
    private val crashReporter: CrashReporter,
    private val engineFactory: () -> Engine,
    private val disposeEngine: (Engine) -> Unit = { it.quit() },
) {

    /**
     * 1局の全局面（0手目=初期局面〜N手目）を解析し、局面ごとの結果を返す。
     *
     * @param moves 棋譜の USI 手列
     * @param onProgress (done, total) の進捗コールバック
     * @return 局面インデックス順の結果リスト（各要素 = その局面の MultiPV 結果）
     */
    suspend fun analyzeGame(
        moves: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
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
