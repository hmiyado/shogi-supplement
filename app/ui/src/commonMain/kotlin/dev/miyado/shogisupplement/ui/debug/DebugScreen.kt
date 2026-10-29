package dev.miyado.shogisupplement.ui.debug

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
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
    backendHostInitial: String = "",
    backendIsLan: Boolean = false,
    onSaveBackend: (suspend (String?) -> Boolean)? = null,
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
                title = { Text(stringResource(Res.string.debug_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back),
                            contentDescription = stringResource(Res.string.back),
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .adaptiveContentWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (onSaveBackend != null) {
                DebugBackendSection(backendHostInitial, backendIsLan, onSaveBackend)
            }
            Text(
                UiStrings.debugWasmSiteEffective(effectiveSiteBaseUrl, effectiveSiteBaseUrlSource),
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
                label = { Text(stringResource(Res.string.debug_wasm_site_field_label)) },
                placeholder = { Text(stringResource(Res.string.debug_wasm_site_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 固定高さのステータス表示スロット（保存成否でレイアウトの高さを変えない）。
            Column(modifier = Modifier.height(24.dp).padding(top = 4.dp)) {
                when (status) {
                    SaveStatus.INVALID -> Text(
                        stringResource(Res.string.debug_wasm_site_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.shogiColors.loss,
                    )
                    SaveStatus.SAVED -> Text(
                        stringResource(Res.string.debug_wasm_site_saved),
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
                ) { Text(stringResource(Res.string.debug_wasm_site_save)) }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        onClear()
                        input = ""
                        status = null
                    },
                ) { Text(stringResource(Res.string.debug_wasm_site_clear)) }
            }

            if (onWipeLocalData != null) {
                Spacer(Modifier.height(24.dp))
                ShogiSecondaryButton(onClick = { showWipeConfirm = true }) {
                    Text(stringResource(Res.string.debug_wipe_local_data))
                }
                if (wiped) {
                    Text(
                        text = stringResource(Res.string.debug_wipe_done),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showWipeConfirm && onWipeLocalData != null) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text(stringResource(Res.string.debug_wipe_dialog_title)) },
            text = { Text(UiStrings.DEBUG_WIPE_DIALOG_TEXT) },
            confirmButton = {
                TextButton(onClick = {
                    onWipeLocalData()
                    showWipeConfirm = false
                    wiped = true
                }) { Text(stringResource(Res.string.account_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text(stringResource(Res.string.cancel)) }
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
                effectiveSiteBaseUrlSource = stringResource(Res.string.debug_wasm_site_source_production),
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
                effectiveSiteBaseUrlSource = stringResource(Res.string.debug_wasm_site_source_saved),
                onSave = { true },
                onClear = {},
            )
        }
    }
}

@Composable
private fun DebugBackendSection(hostInitial: String, activeLan: Boolean, onSave: suspend (String?) -> Boolean) {
    var lan by remember { mutableStateOf(hostInitial.isNotBlank()) }
    var host by remember { mutableStateOf(hostInitial) }
    var saving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    Text(stringResource(Res.string.debug_backend_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(if (activeLan) Res.string.debug_backend_current_lan else Res.string.debug_backend_current_production))
    Row {
        RadioButton(selected = !lan, onClick = { lan = false; result = null }, enabled = !saving)
        TextButton(onClick = { lan = false; result = null }, enabled = !saving) { Text(stringResource(Res.string.debug_backend_production)) }
        RadioButton(selected = lan, onClick = { lan = true; result = null }, enabled = !saving)
        TextButton(onClick = { lan = true; result = null }, enabled = !saving) { Text(stringResource(Res.string.debug_backend_lan)) }
    }
    OutlinedTextField(
        value = host, onValueChange = { host = it; result = null },
        label = { Text(stringResource(Res.string.debug_backend_host)) },
        enabled = lan && !saving, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(Res.string.debug_backend_restart), style = MaterialTheme.typography.bodySmall)
    Column(Modifier.height(48.dp)) {
        result?.let { Text(stringResource(if (it) Res.string.debug_backend_saved else Res.string.debug_backend_failed),
            style = MaterialTheme.typography.bodySmall, color = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.shogiColors.loss) }
    }
    Button(enabled = !saving, onClick = {
        saving = true
        scope.launch { try { result = onSave(if (lan) host else null) } finally { saving = false } }
    }) { Text(stringResource(Res.string.debug_wasm_site_save)) }
    Spacer(Modifier.height(24.dp))
}
