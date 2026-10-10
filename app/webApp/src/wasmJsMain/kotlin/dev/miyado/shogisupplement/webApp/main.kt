package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.miyado.shogisupplement.webApp.mypage.MyPageRoot
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// 共通フォントの準備が終わってから画面を出し、初回描画での書体変更を防ぐ。
// Why not スコープを絞る: main()はフレームワークが1回だけ呼ぶ入口で、
// 起動するコルーチンの寿命はページと一致する。
// Why not バンドルを2本ビルドする: 十数MBのwasm成果物の重複配信を避け、パスで出し分ける。
@OptIn(ExperimentalComposeUiApi::class, DelicateCoroutinesApi::class, org.jetbrains.compose.resources.ExperimentalResourceApi::class)
fun main() {
    GlobalScope.launch {
        ComposeViewport(
            document.getElementById("composeApp")!!,
            configure = { isA11YEnabled = debugSemanticsRequested() },
        ) {
            val fonts = listOf(
                Res.font.shippori_mincho_bold to androidx.compose.ui.text.font.FontWeight.Bold,
                Res.font.shippori_mincho_bold to androidx.compose.ui.text.font.FontWeight.Normal,
                Res.font.ibm_plex_sans_jp_regular to androidx.compose.ui.text.font.FontWeight.Normal,
                Res.font.ibm_plex_sans_jp_regular to androidx.compose.ui.text.font.FontWeight.Medium,
                Res.font.ibm_plex_sans_jp_bold to androidx.compose.ui.text.font.FontWeight.Bold,
                Res.font.ibm_plex_mono_regular to androidx.compose.ui.text.font.FontWeight.Normal,
                Res.font.ibm_plex_mono_regular to androidx.compose.ui.text.font.FontWeight.Medium,
                Res.font.ibm_plex_mono_semi_bold to androidx.compose.ui.text.font.FontWeight.SemiBold,
            ).map { (resource, weight) -> org.jetbrains.compose.resources.preloadFont(resource, weight).value }
            if (fonts.any { it == null }) return@ComposeViewport
            if (window.location.pathname.endsWith("mypage.html")) {
                MyPageRoot()
            } else {
                KentoRoot()
            }
        }
    }
}

// canvasに描くため、これを有効にしないとDOMから画面の中身が見えず、要素を指した操作もできない。
// Why not 常に有効にする: ノードの増減のたびにDOMを同期する費用を利用者に払わせない。
private fun debugSemanticsRequested(): Boolean =
    window.location.search.contains("debug=1")

@Composable
private fun KentoRoot() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { KentoViewModel(scope) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    App(
        state = viewModel.state,
        onBack = viewModel::goHome,
        onKifTextChange = viewModel::setKifText,
        onStart = viewModel::startAnalysis,
        onCancel = viewModel::cancelAnalysis,
        onConfirmSide = viewModel::confirmUserSide,
        onCancelSideSelection = viewModel::cancelSideSelection,
        onReanalyze = viewModel::reanalyze,
        onResumeSavedReport = viewModel::resumeSavedReport,
        onShowSavedReports = viewModel::showSavedReports,
        onCloseSavedReports = viewModel::closeSavedReports,
        onOpenSavedReport = viewModel::openSavedReport,
        onDeleteSavedReport = viewModel::deleteSavedReport,
        onCopyKif = { dev.miyado.shogisupplement.webApp.mypage.copyTextToClipboard(it) },
        onDiscardStudy = viewModel::discardStudyAndContinue,
        onCancelDiscardStudy = viewModel::cancelDiscardStudy,
        studyActions = viewModel,
    )
}
