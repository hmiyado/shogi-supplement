package dev.miyado.shogisupplement.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// Why not Engineをsuspend化: 解析まるごと・ドリル・読み筋延長が同期契約に依存している。
// 検討だけが必要とする操作を別の契約として切り出す。
interface StudyEngine {
    suspend fun analyzeSfen(
        sfen: String,
        additionalMoves: List<String> = emptyList(),
        nodes: Int = Engine.DEFAULT_NODES,
    ): List<PvInfo>

    fun quit()
}

// Why not どこからでも呼べる形: 同期のanalyzeSfenを待つため、ブロックしてよい
// dispatcherが要る。ブラウザのメインスレッドは同期待ちできない。
class BlockingStudyEngine(
    private val engine: Engine,
    private val dispatcher: CoroutineDispatcher,
) : StudyEngine {
    override suspend fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
        withContext(dispatcher) { engine.analyzeSfen(sfen, additionalMoves, nodes) }

    override fun quit() = engine.quit()
}
