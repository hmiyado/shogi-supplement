package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme

@Composable
fun App(
    state: KentoUiState,
    onBack: () -> Unit,
    onKifTextChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    ShogiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // 画面はスマホアプリのレイアウトをそのまま流用しており広幅では
            // 破綻するため、コンテンツ幅をモバイル相当に固定して中央寄せする。
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = 430.dp).fillMaxHeight()) {
                    AppContent(
                        state = state,
                        onBack = onBack,
                        onKifTextChange = onKifTextChange,
                        onStart = onStart,
                        onCancel = onCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppContent(
    state: KentoUiState,
    onBack: () -> Unit,
    onKifTextChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val report = state.report
    if (report != null) {
        ReportScreen(
            game = report.game,
            reports = report.reports,
            flip = report.game.userSide == "gote",
            strengthDisplayText = report.strengthText,
            positionEvals = report.positionEvals,
            matchRateDisplayText = report.matchRateText,
            blunderRateDisplayText = report.blunderRateText,
            onBack = onBack,
            // 検討モード（盤タップでの分岐検討）・読み筋延長はWeb版の対象外。
            // Web版のエンジン実行はKIF全体を1バッチとしてWorkerへ投げる方式のため、
            // 検討中の任意局面を都度エンジンへ問い合わせる経路を持たない
            // （engineFactoryを注入しないため、既定値のno-opコールバックのまま渡す）。
            pvExtensionEnabled = false,
        )
    } else {
        KentoInputScreen(
            state = state,
            onBack = onBack,
            onKifTextChange = onKifTextChange,
            onStart = onStart,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun KentoInputScreen(
    state: KentoUiState,
    onBack: () -> Unit,
    onKifTextChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    // 低い viewport でもカード全体（解析開始ボタンまで）へ届くようにする。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        KentoTopBar(onBack = onBack)
        when (state.assetsAvailable) {
            null -> Unit
            false -> Text(
                AppStrings.KENTO_ASSETS_UNAVAILABLE,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            true -> InputCard(
                    kifText = state.kifText,
                onKifTextChange = onKifTextChange,
                    inputError = state.inputError,
                analyzing = state.analyzing,
                progressDone = state.progressDone,
                progressTotal = state.progressTotal,
                onStart = onStart,
                onCancel = onCancel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
