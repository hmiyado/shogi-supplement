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

// 初回コンポーズより前に実書体のfetchを読み終える。ComposeViewport自体を
// フォント取得の完了まで遅らせることで、Theme.kt・書体を直接参照する呼び出し側は
// 「常にロード済み」を前提にでき、Composable化しての再構成をせずに済む。main()自体は
// フレームワークが1回だけ呼ぶエントリポイントであり、ページの寿命全体で生きるコルーチンを
// 起動するのがここでの用途と一致するため GlobalScope を使う。
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
    )
}
