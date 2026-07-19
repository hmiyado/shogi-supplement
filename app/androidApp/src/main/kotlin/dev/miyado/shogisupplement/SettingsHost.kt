package dev.miyado.shogisupplement

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.miyado.shogisupplement.ui.LegalLinks
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.settings.SettingsScreen

/** 設定画面（棋力・アカウント・規約・ライセンスの集約ハブ）への VM 配線。 */
@Composable
fun SettingsHost(vm: MainViewModel, onOpenRatingSettings: () -> Unit) {
    BackHandler { vm.loadHome() }
    val currentThemeMode by vm.themeMode.collectAsState()
    val currentEvalDisplay by vm.evalDisplay.collectAsState()
    val currentSkipSideConfirm by vm.skipSideConfirm.collectAsState()
    val context = LocalContext.current
    SettingsScreen(
        versionName = BuildConfig.VERSION_NAME,
        themeMode = currentThemeMode,
        evalDisplay = currentEvalDisplay,
        onBack = { vm.loadHome() },
        onOpenRatingSettings = onOpenRatingSettings,
        onOpenAccount = { vm.openAccount() },
        onThemeChange = { vm.saveThemeMode(it) },
        onEvalDisplayChange = { vm.saveEvalDisplay(it) },
        skipSideConfirm = currentSkipSideConfirm,
        onSkipSideConfirmChange = { vm.saveSkipSideConfirm(it) },
        onOpenHelp = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.HELP_WEB_URL)),
            )
        },
        onOpenFeedback = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.FEEDBACK_URL)),
            )
        },
        onOpenTerms = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.TERMS_URL)),
            )
        },
        onOpenLicenses = { vm.openLicenses() },
        // DEBUG ビルドのみデバッグセクションを設定画面最下部に表示
        onOpenDebug = if (BuildConfig.DEBUG) { { vm.openDebug() } } else null,
    )
}
