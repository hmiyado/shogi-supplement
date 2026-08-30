package dev.miyado.shogisupplement.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import dev.miyado.shogisupplement.ui.common.ShogiSecondaryButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Why not 保存後の値をここで組み立てる: 優先順位（環境変数＞保存値＞本番）は
 * プラットフォーム側が持つ。更新後の値を渡し直してもらう。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    siteBaseUrlInputInitial: String,
    effectiveSiteBaseUrl: String,
    effectiveSiteBaseUrlSource: String,
    onSave: (String) -> Boolean,
    onClear: () -> Unit,
    /** null = 削除の導線を出さない（この画面を消さずに機能だけ隠すため）。 */
    onWipeLocalData: (() -> Unit)? = null,
) {
    var showWipeConfirm by remember { mutableStateOf(false) }
    var wiped by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(siteBaseUrlInputInitial) }
    // 保存直後の一時フィードバック用。表示種別だけを持ち、テキストはStatusSlotが解決する
    // （エラー/成功どちらでもスロットの高さを変えないため。DESIGN.mdのNo-jitter原則）。
    var status by remember { mutableStateOf<SaveStatus?>(null) }

    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.DEBUG_SCREEN_TITLE) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                AppStrings.debugWasmSiteEffective(effectiveSiteBaseUrl, effectiveSiteBaseUrlSource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.shogiColors.ink2,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    status = null
                },
                label = { Text(AppStrings.DEBUG_WASM_SITE_FIELD_LABEL) },
                placeholder = { Text(AppStrings.DEBUG_WASM_SITE_PLACEHOLDER) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 固定高さのステータス表示スロット（保存成否でレイアウトの高さを変えない）。
            Column(modifier = Modifier.height(24.dp).padding(top = 4.dp)) {
                when (status) {
                    SaveStatus.INVALID -> Text(
                        AppStrings.DEBUG_WASM_SITE_INVALID,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.shogiColors.loss,
                    )
                    SaveStatus.SAVED -> Text(
                        AppStrings.DEBUG_WASM_SITE_SAVED,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    null -> Unit
                }
            }

            Spacer(Modifier.height(8.dp))

            Row {
                Button(
                    onClick = { status = if (onSave(input)) SaveStatus.SAVED else SaveStatus.INVALID },
                ) { Text(AppStrings.DEBUG_WASM_SITE_SAVE) }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        onClear()
                        input = ""
                        status = null
                    },
                ) { Text(AppStrings.DEBUG_WASM_SITE_CLEAR) }
            }

            if (onWipeLocalData != null) {
                Spacer(Modifier.height(24.dp))
                ShogiSecondaryButton(onClick = { showWipeConfirm = true }) {
                    Text(AppStrings.DEBUG_WIPE_LOCAL_DATA)
                }
                if (wiped) {
                    Text(
                        text = AppStrings.DEBUG_WIPE_DONE,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showWipeConfirm && onWipeLocalData != null) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text(AppStrings.DEBUG_WIPE_DIALOG_TITLE) },
            text = { Text(AppStrings.DEBUG_WIPE_DIALOG_TEXT) },
            confirmButton = {
                TextButton(onClick = {
                    onWipeLocalData()
                    showWipeConfirm = false
                    wiped = true
                }) { Text(AppStrings.ACCOUNT_DELETE_CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text(AppStrings.CANCEL) }
            },
        )
    }
}

private enum class SaveStatus { INVALID, SAVED }

@Preview
@Composable
private fun PreviewDebugScreen() {
    ShogiTheme {
        Surface {
            DebugScreen(
                onBack = {},
                siteBaseUrlInputInitial = "",
                effectiveSiteBaseUrl = "https://shogi-supplement.miyado.dev/",
                effectiveSiteBaseUrlSource = AppStrings.DEBUG_WASM_SITE_SOURCE_PRODUCTION,
                onSave = { true },
                onClear = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewDebugScreenOverridden() {
    ShogiTheme {
        Surface {
            DebugScreen(
                onBack = {},
                siteBaseUrlInputInitial = "http://127.0.0.1:8925/",
                effectiveSiteBaseUrl = "http://127.0.0.1:8925/",
                effectiveSiteBaseUrlSource = AppStrings.DEBUG_WASM_SITE_SOURCE_SAVED,
                onSave = { true },
                onClear = {},
            )
        }
    }
}
