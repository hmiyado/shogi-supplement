package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.util.Logger
import kotlinx.coroutines.CancellationException

/**
 * [primary] が失敗したとき [secondary] で同じ呼び出しをやり直す [Engine] デコレータ。
 * [FailoverAnalyzer] の合成方針（[GameAnalyzer] 向け）を [Engine] の同期インターフェース向けに
 * 焼き直したもの。
 *
 * [GameAnalyzer] 版と異なり例外型を問わない（[Engine] 実装は個々に異なる例外を投げるため
 * ——[WasmAnalysisException] 等——型で絞り込む代わりに「[primary] が何であれ失敗したら
 * [secondary] を試す」というシンプルな契約にする）。[secondary] 自体が失敗した場合は
 * [secondary] の例外をそのまま伝播させる（[FailoverAnalyzer] と違い、[primary] の例外に
 * 戻さない: 呼び出し側は同期呼び出しの結果だけを見るため、最終的に起きた例外を見せたほうが
 * 診断しやすい）。
 */
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
