package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.miyado.shogisupplement.ui.report.GameShareLink
import dev.miyado.shogisupplement.ui.report.GameShareParse
import dev.miyado.shogisupplement.ui.report.playersLine
import dev.miyado.shogisupplement.ui.report.PositionShare
import dev.miyado.shogisupplement.ui.report.PositionShareParse
import dev.miyado.shogisupplement.ui.report.SharedPosition
import dev.miyado.shogisupplement.webApp.js.kentoBridge
import dev.miyado.shogisupplement.webApp.mypage.MyPageDependencies
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
                val game = GameShareLink.parseQuery(window.location.search).let {
                    if (it == GameShareParse.Absent) GameShareLink.parseFragment(window.location.hash) else it
                }
                when (game) {
                    is GameShareParse.Valid -> SharedGameRoot(game.id)
                    GameShareParse.Invalid -> SharedGameStatusApp(
                        state = SharedGameLoadState.Invalid,
                        onBack = { kentoBridge().goHome() },
                        onRetry = {},
                    )
                    GameShareParse.Absent -> when (val parsed = PositionShare.parseFragment(window.location.hash)) {
                        is PositionShareParse.Valid -> SharedPositionRoot(parsed.position)
                        PositionShareParse.Invalid -> SharedPositionErrorApp(onBack = { kentoBridge().goHome() })
                        PositionShareParse.Absent -> KentoRoot()
                    }
                }
            }
        }
    }
}

// canvasに描くため、これを有効にしないとDOMから画面の中身が見えず、要素を指した操作もできない。
// Why not 常に有効にする: ノードの増減のたびにDOMを同期する費用を利用者に払わせない。
private fun debugSemanticsRequested(): Boolean =
    window.location.search.contains("debug=1")

@Composable
private fun SharedPositionRoot(position: SharedPosition) {
    val scope = rememberCoroutineScope()
    val title = org.jetbrains.compose.resources.stringResource(Res.string.shared_position_title)
    val viewModel = remember(position) { SharedPositionViewModel(scope, position, title) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    val state by viewModel.studyState.collectAsState()
    SharedPositionApp(
        studyState = state,
        actions = viewModel,
        onBack = { kentoBridge().goHome() },
    )
}

@Composable
private fun SharedGameRoot(id: String) {
    val scope = rememberCoroutineScope()
    val loadViewModel = remember(id) {
        SharedGameReceiverViewModel(scope, dev.miyado.shogisupplement.gameshare.StaticGameShareReader(
            dev.miyado.shogisupplement.webApp.mypage.SUPABASE_URL, io.ktor.client.HttpClient(),
        ), id)
    }
    val loadState by loadViewModel.state.collectAsState()
    when (val state = loadState) {
        is SharedGameLoadState.Ready -> SharedGameLoaded(state.snapshot.id, state.snapshot)
        else -> SharedGameStatusApp(
            state = state,
            onBack = { kentoBridge().goHome() },
            onRetry = loadViewModel::load,
        )
    }
}

@Composable
private fun SharedGameLoaded(
    id: String,
    snapshot: dev.miyado.shogisupplement.gameshare.GameShareSnapshot,
) {
    val scope = rememberCoroutineScope()
    val title = org.jetbrains.compose.resources.stringResource(Res.string.shared_game_title)
    val self = org.jetbrains.compose.resources.stringResource(Res.string.shared_game_player_self)
    val opponent = org.jetbrains.compose.resources.stringResource(Res.string.shared_game_player_opponent)
    val playersLine = snapshot.playersLine(self, opponent)
    val viewModel = remember(id) { SharedPositionViewModel(scope, snapshot, title) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    val state by viewModel.studyState.collectAsState()
    SharedPositionApp(
        studyState = state,
        actions = viewModel,
        onBack = { kentoBridge().goHome() },
        titleOverride = title,
        playersLine = playersLine,
        gameMoves = snapshot.mainline,
        onSelectGamePly = viewModel::onSelectGamePly,
    )
}

@Composable
private fun KentoRoot() {
    val scope = rememberCoroutineScope()
    val dependencies = remember {
        if (dev.miyado.shogisupplement.webApp.mypage.SUPABASE_URL.isNotBlank() &&
            dev.miyado.shogisupplement.webApp.mypage.SUPABASE_ANON_KEY.isNotBlank()) MyPageDependencies() else null
    }
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
        onShareGameUrl = { dev.miyado.shogisupplement.webApp.mypage.shareText(it) },
        gameShareService = dependencies?.gameShareService,
        onDiscardStudy = viewModel::discardStudyAndContinue,
        onCancelDiscardStudy = viewModel::cancelDiscardStudy,
        studyActions = viewModel,
    )
}
