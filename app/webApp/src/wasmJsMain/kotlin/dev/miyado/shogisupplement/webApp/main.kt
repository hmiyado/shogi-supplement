package dev.miyado.shogisupplement.webApp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.miyado.shogisupplement.ui.theme.preloadShogiWebFonts
import dev.miyado.shogisupplement.webApp.mypage.MyPageRoot
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// Why not コンポーズ後にフォントを読む: 書体を非同期に差し替えると再構成が要る。
// ComposeViewport自体を取得完了まで遅らせ、常にロード済みを前提にできる形にする。
// Why not スコープを絞る: main()はフレームワークが1回だけ呼ぶ入口で、
// 起動するコルーチンの寿命はページと一致する。
// Why not バンドルを2本ビルドする: 十数MBのwasm成果物の重複配信を避け、パスで出し分ける。
@OptIn(ExperimentalComposeUiApi::class, DelicateCoroutinesApi::class)
fun main() {
    GlobalScope.launch {
        preloadShogiWebFonts()
        ComposeViewport(
            document.getElementById("composeApp")!!,
            configure = { isA11YEnabled = debugSemanticsRequested() },
        ) {
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
/** セマンティクスツリーのDOMへの同期を求めているか。 */
private fun debugSemanticsRequested(): Boolean =
    window.location.search.contains("debug=1")

@Composable
private fun KentoRoot() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { KentoViewModel(scope) }
    App(
        state = viewModel.state,
        onBack = viewModel::goHome,
        onKifTextChange = viewModel::setKifText,
        onStart = viewModel::startAnalysis,
        onCancel = viewModel::cancelAnalysis,
        onConfirmSide = viewModel::confirmUserSide,
        onCancelSideSelection = viewModel::cancelSideSelection,
        studyActions = viewModel,
    )
}
