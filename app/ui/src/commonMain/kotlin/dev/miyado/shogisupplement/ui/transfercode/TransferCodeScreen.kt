package dev.miyado.shogisupplement.ui.transfercode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

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
                    Text(
                        text = currentCode,
                        fontFamily = IbmPlexMonoFamily,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onCopy(currentCode)
                            justCopied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
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
