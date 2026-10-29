package dev.miyado.shogisupplement.ui.transfercode

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import dev.miyado.shogisupplement.ui.common.ShogiSecondaryButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val TRANSFER_CODE_MASK_CHAR = '*'
private const val TRANSFER_CODE_GROUPS_PER_LINE = 3

/**
 * Why not ソフトラップ: 折返しが字幅に依存し、伏字と生値の切替で行構成がズレる。
 * 明示改行なら字幅と無関係に両状態の行構成が一致する（DESIGN.md No-jitter）。
 */
private fun formatTransferCodeForDisplay(rawCode: String, mask: Boolean): String =
    rawCode.split('-')
        .map { group -> if (mask) TRANSFER_CODE_MASK_CHAR.toString().repeat(group.length) else group }
        .chunked(TRANSFER_CODE_GROUPS_PER_LINE)
        .joinToString("\n") { it.joinToString("-") }

/** 引き継ぎコード表示画面（作り直しの導線を含む）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferCodeScreen(
    code: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit = {},
    /** null = 作り直しの導線を出さない（サーバー未設定ビルド）。 */
    onRegenerate: (() -> Unit)? = null,
    regenerateError: String? = null,
    showRegenerateDialogInitially: Boolean = false,
) {
    var justCopied by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(showRegenerateDialogInitially) }
    // パスワード同様の秘密のため既定で伏せる。コピー操作はこのフラグを条件にしない
    // ——伏字のままでも安全な場所への控えができる必要があるため。
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(2000)
            justCopied = false
        }
    }

    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transfer_code_title)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val currentCode = code
            if (currentCode == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .adaptiveContentWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = UiStrings.TRANSFER_CODE_DESCRIPTION,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = formatTransferCodeForDisplay(currentCode, mask = !revealed),
                            fontFamily = IbmPlexMonoFamily,
                            fontSize = 20.sp,
                            lineHeight = 30.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).testTag("transfer_code_value"),
                        )
                        IconButton(
                            onClick = { revealed = !revealed },
                            modifier = Modifier.testTag("transfer_code_reveal_toggle"),
                        ) {
                            Icon(
                                imageVector = if (revealed) vectorResource(Res.drawable.ic_filled_visibility_off) else vectorResource(Res.drawable.ic_filled_visibility),
                                contentDescription = if (revealed) {
                                    stringResource(Res.string.transfer_code_hide_icon_desc)
                                } else {
                                    stringResource(Res.string.transfer_code_reveal_icon_desc)
                                },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onCopy(currentCode)
                            justCopied = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("transfer_code_copy_button"),
                    ) {
                        Text(
                            if (justCopied) {
                                stringResource(Res.string.transfer_code_copied)
                            } else {
                                stringResource(Res.string.transfer_code_copy_button)
                            },
                        )
                    }

                    if (onRegenerate != null) {
                        ShogiSecondaryButton(
                            onClick = { showRegenerateConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.transfer_code_regenerate_button))
                        }
                    }
                    if (regenerateError != null) {
                        Text(
                            text = regenerateError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showRegenerateConfirm && onRegenerate != null) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text(stringResource(Res.string.transfer_code_regenerate_dialog_title)) },
            text = { Text(UiStrings.TRANSFER_CODE_REGENERATE_DIALOG_TEXT) },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateConfirm = false
                    onRegenerate()
                }) { Text(stringResource(Res.string.transfer_code_regenerate_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewTransferCodeScreen() {
    ShogiTheme {
        TransferCodeScreen(code = "8QZKM-2XRTN-P9VCB-H4WLD-A7YFE-J3", onBack = {})
    }
}

@Preview
@Composable
private fun PreviewTransferCodeScreenLoading() {
    ShogiTheme {
        TransferCodeScreen(code = null, onBack = {})
    }
}
