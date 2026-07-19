package dev.miyado.shogisupplement

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.report.ReportScreen

// KIFコピーの ClipboardManager 取得のみ、この関数内で LocalContext.current を取得している。
// CompositionLocal のため呼び出し元と同一の値が返る。

/**
 * レポート画面（棋譜ビューア）への MainViewModel 配線。
 * 読み筋延長・検討モードのコールバックはすべて vm（MainViewModel）へ委譲する。
 */
@Composable
fun ReportHost(vm: MainViewModel, state: MainUiState.ShowReport) {
    // 検討モード中はシステムバックを「終了」扱いにするため、
    // ここでは検討モード中かどうかに関わらず loadHome() のままにし、
    // 検討モード中の割り込みは ReportScreen 内部の BackHandler（enabled=検討中）に委譲する
    // （ReportScreen 側で先に消費されるため、こちらは検討モード外のときだけ効く）。
    BackHandler { vm.loadHome() }
    val pvExtState by vm.pvExtState.collectAsState()
    val studyState by vm.studyState.collectAsState()
    val context = LocalContext.current
    ReportScreen(
        game = state.game,
        reports = state.reports,
        flip = state.flip,
        strengthDisplayText = state.strengthDisplayText,
        evalDisplay = state.evalDisplay,
        positionEvals = state.positionEvals,
        onBack = { vm.loadHome() },
        pvExtState = pvExtState,
        onExtendBestPv = { blunderId, sfenAtEnd, currentPv ->
            vm.extendBestPv(blunderId, sfenAtEnd, currentPv)
        },
        studyState = studyState,
        onStartStudy = { baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, tappedSquare ->
            vm.startStudy(
                baseSfen, flip, originIsBestPv, originPlyIndex,
                originSelectedIdx, originAbsolutePly, tappedSquare,
            )
        },
        onStudySquareTapped = { sq -> vm.onStudySquareTapped(sq) },
        onStudyHandPieceTapped = { pt -> vm.onStudyHandPieceTapped(pt) },
        onStudyPromoteDecision = { promote -> vm.onStudyPromoteDecision(promote) },
        onStudyStepBack = { vm.studyStepBack() },
        onStudyResetToStart = { vm.studyResetToStart() },
        onStudyEnd = { vm.endStudy() },
        // KIFコピー（ClipboardManager/Context 依存）の Android 実装。ReportScreen（共通コード）は
        // Android専用APIに依存できないためこちらにホイストしている。snackbar表示自体は
        // ReportScreen 側が行う。
        onCopyKif = { kifText ->
            val clip = ClipData.newPlainText("棋譜", kifText)
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        },
    )
}
