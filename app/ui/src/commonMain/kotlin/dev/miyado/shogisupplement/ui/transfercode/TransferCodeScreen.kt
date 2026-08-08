package dev.miyado.shogisupplement.ui.transfercode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val TRANSFER_CODE_MASK_CHAR = '*'
private const val TRANSFER_CODE_GROUPS_PER_LINE = 3

/**
 * 表示用にグループ単位の明示改行で整形する（マスク時は値だけ伏せ、区切り・行構造は不変）。
 *
 * Why not ソフトラップに任せる: 折返し位置が字幅に依存し、フォント解決の差
 * （マスク文字のフォールバック・プラットフォーム差）でマスク⇔生値の切替時に
 * 行構成がズレる（•(U+2022)・*とも実機で再現）。明示改行なら字幅と無関係に
 * 両状態の行構成が構造的に一致する（DESIGN.md No-jitter）。
 */
private fun formatTransferCodeForDisplay(rawCode: String, mask: Boolean): String =
    rawCode.split('-')
        .map { group -> if (mask) TRANSFER_CODE_MASK_CHAR.toString().repeat(group.length) else group }
        .chunked(TRANSFER_CODE_GROUPS_PER_LINE)
        .joinToString("\n") { it.joinToString("-") }

/**
 * 引き継ぎコード表示画面（設定→引き継ぎコード）。
 *
 * 設計書 付録「引き継ぎコードの詳細仕様」節が仕様の正。コード自体（[TransferCode.encode] の
 * 出力）は端末シークレットSの人間可読表現で、他人に渡ると棋譜・アカウントへアクセスできる
 * ため、注意文言を必ず併記する。入力（復元）フローはこのタスクの範囲外（別タスク）。
 *
 * @param code 表示するコード文字列（ハイフン区切り済み）。null = 読み込み中
 *   （S生成・派生はsuspendのため、呼び出し側がLaunchedEffectで非同期に用意する）。
 * @param onCopy コピー操作。コード文字列を渡すのでプラットフォーム側クリップボードへ書き込む
 *   （ReportScreen の onCopyKif と同じパターン）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferCodeScreen(
    code: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit = {},
) {
    var justCopied by remember { mutableStateOf(false) }
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
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.TRANSFER_CODE_TITLE) },
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
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = AppStrings.TRANSFER_CODE_DESCRIPTION,
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
                                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (revealed) {
                                    AppStrings.TRANSFER_CODE_HIDE_ICON_DESC
                                } else {
                                    AppStrings.TRANSFER_CODE_REVEAL_ICON_DESC
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
                                AppStrings.TRANSFER_CODE_COPIED
                            } else {
                                AppStrings.TRANSFER_CODE_COPY_BUTTON
                            },
                        )
                    }
                }
            }
        }
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
