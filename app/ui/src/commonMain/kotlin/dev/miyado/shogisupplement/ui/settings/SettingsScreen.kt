package dev.miyado.shogisupplement.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors

/** Why not no-opのラムダ: 未実装の機能はプラットフォームで違う。nullで行ごと消す。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    themeMode: String = "system",
    evalDisplay: String = "cp",
    onBack: () -> Unit,
    /** null = アカウント行・「データ」節ごと非表示（Supabase未設定ビルド等）。 */
    onOpenAccount: (() -> Unit)?,
    /** null = 非表示。[onOpenAccount] が非nullのときだけ意味を持つ。 */
    onOpenTransferCode: (() -> Unit)? = null,
    /** null = 非表示。[onOpenTransferCode] と同じ規約。 */
    onOpenTransferCodeInput: (() -> Unit)? = null,
    /** null = 非表示。アカウントを作らずに使っている端末にだけ、あとから作る入口を出す。 */
    onCreateAccount: (() -> Unit)? = null,
    onThemeChange: (String) -> Unit = {},
    onEvalDisplayChange: (String) -> Unit = {},
    /** 先後確認の省略設定（アカウント名一致時にダイアログを出さない）。 */
    skipSideConfirm: Boolean = false,
    onSkipSideConfirmChange: (Boolean) -> Unit = {},
    onOpenHelp: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onOpenLicenses: () -> Unit,
    /** DEBUG ビルドのみ非 null。null のときデバッグセクションは非表示。 */
    onOpenDebug: (() -> Unit)? = null,
    /** 駒台配置を左右にする実験トグル。null = 非表示（[onOpenDebug] と同じ規約）。 */
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEvalDisplayDialog by remember { mutableStateOf(false) }
    if (showThemeDialog) {
        ThemeDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                onThemeChange(mode)
                showThemeDialog = false
            },
        )
    }
    if (showEvalDisplayDialog) {
        EvalDisplayDialog(
            currentMode = evalDisplay,
            onDismiss = { showEvalDisplayDialog = false },
            onSelect = { mode ->
                onEvalDisplayChange(mode)
                showEvalDisplayDialog = false
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.SETTINGS_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(AppStrings.SETTINGS_SECTION_PROFILE)
            // 先後確認の省略トグル（OFFに戻す導線）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSkipSideConfirmChange(!skipSideConfirm) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        AppStrings.SETTINGS_ROW_SKIP_SIDE_CONFIRM,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        AppStrings.SETTINGS_ROW_SKIP_SIDE_CONFIRM_SUB,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.shogiColors.ink3,
                    )
                }
                Switch(
                    checked = skipSideConfirm,
                    onCheckedChange = onSkipSideConfirmChange,
                )
            }
            HorizontalDivider(color = MaterialTheme.shogiColors.line)

            // onOpenAccount = null（Supabase未設定ビルド等）のときは節ごと非表示にする
            if (onOpenAccount != null) {
                SettingsSectionHeader(AppStrings.SETTINGS_SECTION_DATA)
                if (onCreateAccount != null) {
                    SettingsRow(
                        label = AppStrings.SETTINGS_CREATE_ACCOUNT,
                        sub = AppStrings.SETTINGS_CREATE_ACCOUNT_SUB,
                        onClick = onCreateAccount,
                    )
                }
                SettingsRow(
                    label = AppStrings.SETTINGS_ROW_ACCOUNT,
                    sub = AppStrings.SETTINGS_ROW_ACCOUNT_SUB,
                    onClick = onOpenAccount,
                )
                if (onOpenTransferCode != null) {
                    SettingsRow(
                        label = AppStrings.SETTINGS_ROW_TRANSFER_CODE,
                        sub = AppStrings.SETTINGS_ROW_TRANSFER_CODE_SUB,
                        onClick = onOpenTransferCode,
                    )
                }
                if (onOpenTransferCodeInput != null) {
                    SettingsRow(
                        label = AppStrings.SETTINGS_ROW_TRANSFER_CODE_INPUT,
                        sub = AppStrings.SETTINGS_ROW_TRANSFER_CODE_INPUT_SUB,
                        onClick = onOpenTransferCodeInput,
                    )
                }
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
            }

            SettingsSectionHeader(AppStrings.SETTINGS_SECTION_DISPLAY)
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_THEME,
                sub = AppStrings.themeLabel(themeMode),
                onClick = { showThemeDialog = true },
            )
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_EVAL_DISPLAY,
                sub = AppStrings.evalDisplayLabel(evalDisplay),
                onClick = { showEvalDisplayDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.shogiColors.line)

            SettingsSectionHeader(AppStrings.SETTINGS_SECTION_ABOUT)
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_HELP,
                onClick = onOpenHelp,
            )
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_FEEDBACK,
                onClick = onOpenFeedback,
            )
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_TERMS,
                onClick = onOpenTerms,
            )
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_RELEASE_NOTES,
                onClick = onOpenReleaseNotes,
            )
            SettingsRow(
                label = AppStrings.SETTINGS_ROW_LICENSES,
                onClick = onOpenLicenses,
            )
            // バージョン行（タップ不可・値表示のみ）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    AppStrings.SETTINGS_ROW_VERSION,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.shogiColors.ink3,
                )
            }

            // デバッグセクション（onOpenDebug が非 null = DEBUG ビルドのみ表示）
            if (onOpenDebug != null) {
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
                SettingsSectionHeader(AppStrings.SETTINGS_DEBUG_SECTION)
                SettingsRow(
                    label = AppStrings.SETTINGS_DEBUG_ROW,
                    onClick = onOpenDebug,
                )
            }
        }
    }
}

/** テーマ選択ダイアログ（システムに従う／ライト／ダーク）。 */
@Composable
private fun ThemeDialog(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "system" to AppStrings.THEME_SYSTEM,
        "light" to AppStrings.THEME_LIGHT,
        "dark" to AppStrings.THEME_DARK,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.THEME_DIALOG_TITLE) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) },
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onSelect(mode) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

/**
 * 形勢の表示選択ダイアログ（評価値 / 勝率）。
 * テーマダイアログと同じ流儀: 選択即確定。
 */
@Composable
private fun EvalDisplayDialog(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "cp" to AppStrings.EVAL_DISPLAY_CP,
        "wp" to AppStrings.EVAL_DISPLAY_WP,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.EVAL_DISPLAY_DIALOG_TITLE) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) },
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onSelect(mode) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

/** セクション見出し（小さめのラベル・ink2）。 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.shogiColors.ink2,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** 設定行（ラベル＋任意のサブテキスト＋「›」シェブロン）。 */
@Composable
private fun SettingsRow(
    label: String,
    sub: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.shogiColors.ink3,
                )
            }
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.shogiColors.ink3,
        )
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewSettingsScreen() {
    ShogiTheme {
        Surface {
            SettingsScreen(
                versionName = "0.1.0",
                themeMode = "system",
                evalDisplay = "cp",
                onBack = {},
                onOpenAccount = {},
                onThemeChange = {},
                onEvalDisplayChange = {},
                onOpenHelp = {},
                onOpenFeedback = {},
                onOpenTerms = {},
                onOpenReleaseNotes = {},
                onOpenLicenses = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSettingsScreenDark() {
    ShogiTheme(themeMode = "dark") {
        Surface {
            SettingsScreen(
                versionName = "0.1.0",
                themeMode = "dark",
                evalDisplay = "wp",
                onBack = {},
                onOpenAccount = {},
                onThemeChange = {},
                onEvalDisplayChange = {},
                onOpenHelp = {},
                onOpenFeedback = {},
                onOpenTerms = {},
                onOpenReleaseNotes = {},
                onOpenLicenses = {},
            )
        }
    }
}
