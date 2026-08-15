package dev.miyado.shogisupplement.webApp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.miyado.shogisupplement.ui.theme.preloadShogiWebFonts
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// Why not コンポーズ後にフォントを読む: 書体を非同期に差し替えると再構成が要る。
// ComposeViewport自体を取得完了まで遅らせ、常にロード済みを前提にできる形にする。
// Why not スコープを絞る: main()はフレームワークが1回だけ呼ぶ入口で、
// 起動するコルーチンの寿命はページと一致する。
@OptIn(ExperimentalComposeUiApi::class, DelicateCoroutinesApi::class)
fun main() {
    GlobalScope.launch {
        preloadShogiWebFonts()
        ComposeViewport(document.getElementById("composeApp")!!) {
            KentoRoot()
        }
    }
}

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
