package dev.miyado.shogisupplement.webApp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.webApp.engine.checkEngineAssetsAvailable
import dev.miyado.shogisupplement.webApp.engine.fetchTextAsset
import dev.miyado.shogisupplement.webApp.engine.runEngineAnalysis
import dev.miyado.shogisupplement.webApp.js.kentoBridge
import dev.miyado.shogisupplement.webApp.report.ParseOutcome
import dev.miyado.shogisupplement.webApp.report.buildWebReport
import dev.miyado.shogisupplement.webApp.report.parseKifInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 係数表の配置場所。docs/copy-kento-assets.sh がandroidApp/assetsの正本からコピーする。 */
private const val COEFFICIENTS_URL = "./kento/coefficients_hao_isolate_v1.json"

/**
 * 「棋譜を検討する」ページの状態遷移を担う。DBを持たないため、
 * 解析結果はこのインスタンスの寿命内（=タブを開いている間）だけ保持される。
 */
class KentoViewModel(private val scope: CoroutineScope) {
    var state by mutableStateOf(KentoUiState())
        private set

    private var coefTable: CoefficientTable? = null
    private var analysisJob: Job? = null

    init {
        scope.launch {
            val available = checkEngineAssetsAvailable()
            state = state.copy(assetsAvailable = available)
        }
    }

    fun setKifText(text: String) {
        state = state.copy(kifText = text)
    }

    fun goHome() {
        kentoBridge().goHome()
    }

    /** パース成功後、解析を即開始せず「自分の側」ダイアログ表示待ちへ遷移する。 */
    fun startAnalysis() {
        if (state.analyzing) return
        val outcome = parseKifInput(state.kifText)
        when (outcome) {
            is ParseOutcome.Error -> {
                state = state.copy(inputError = outcome.message)
            }
            is ParseOutcome.Ok -> {
                state = state.copy(inputError = null, pendingSideSelection = outcome.input)
            }
        }
    }

    /** 側選択ダイアログのキャンセル（外側タップ等）。入力カードへ戻る。 */
    fun cancelSideSelection() {
        state = state.copy(pendingSideSelection = null)
    }

    /** 側選択ダイアログの確定。userSide は null（Web専用の「指定しない」）も許容する。 */
    fun confirmUserSide(userSide: String?) {
        val input = state.pendingSideSelection ?: return
        state = state.copy(
            pendingSideSelection = null,
            analyzing = true,
            progressDone = 0,
            progressTotal = input.moves.size + 1,
        )
        analysisJob = scope.launch {
            try {
                val coef = coefTable ?: CoefficientTable.fromJson(fetchTextAsset(COEFFICIENTS_URL)).also {
                    coefTable = it
                }
                val evals = runEngineAnalysis(input.baseSfenArg, input.moves) { done, total ->
                    state = state.copy(progressDone = done, progressTotal = total)
                }
                val report = buildWebReport(
                    fileName = AppStrings.KENTO_PASTED_GAME_TITLE,
                    moves = input.moves,
                    headers = input.headers,
                    evals = evals,
                    endReason = input.endReason,
                    winner = input.winner,
                    kifText = input.kifText,
                    coef = coef,
                    userSide = userSide,
                )
                state = state.copy(analyzing = false, report = report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    analyzing = false,
                    inputError = AppStrings.KENTO_ERROR_GENERIC,
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        state = state.copy(analyzing = false)
    }
}
