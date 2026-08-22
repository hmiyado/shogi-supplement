package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.util.Logger
import kotlinx.coroutines.CancellationException

/** primaryが失敗したらsecondaryを試すEngineデコレータ。例外型を問わず切り替え、secondaryの例外を返す。 */
class FailoverEngine(
    private val primary: Engine,
    private val secondary: Engine,
) : Engine {
    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> =
        runOrSecondary { primary.analyze(moves, nodes) } ?: secondary.analyze(moves, nodes)

    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
        runOrSecondary { primary.analyzeSfen(sfen, additionalMoves, nodes) }
            ?: secondary.analyzeSfen(sfen, additionalMoves, nodes)

    override fun newGame() {
        primary.newGame()
        secondary.newGame()
    }

    override fun quit() {
        primary.quit()
        secondary.quit()
    }

    /** [block] を実行し、成功すればその結果を返す。失敗すればログを残し null を返す。 */
    private inline fun <T> runOrSecondary(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e(TAG, "primaryが失敗したためsecondaryへ切り替える", e)
        null
    }

    companion object {
        private const val TAG = "FailoverEngine"
    }
}
