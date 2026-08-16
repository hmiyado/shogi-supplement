package dev.miyado.shogisupplement.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** アカウント画面。作成・削除が主で、棋譜の保存は切り替えとして分ける。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    onBack: () -> Unit,
    onSignInAnonymously: () -> Unit = {},
    onSetAutoUpload: (Boolean) -> Unit = {},
    onManualUpload: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.ACCOUNT_SECTION_TITLE) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is AccountUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AccountUiState.NotLoggedIn -> {
                    AccountNotProvidingContent(
                        error = s.error,
                        onSignInAnonymously = onSignInAnonymously,
                        onOpenTerms = onOpenTerms,
                    )
                }
                is AccountUiState.LoggedIn -> {
                    AccountProvidingContent(
                        uploadedCount = s.uploadedCount,
                        pendingCount = s.pendingCount,
                        autoUpload = s.autoUpload,
                        error = s.error,
                        isUploading = s.isUploading,
                        uploadResultMessage = s.uploadResultMessage,
                        onAutoUploadChange = onSetAutoUpload,
                        onManualUpload = onManualUpload,
                        onDeleteAccount = onDeleteAccount,
                        onOpenTerms = onOpenTerms,
                    )
                }
            }
        }
    }
}

/** 未作成の画面。メール・パスワードの入力欄は持たない。 */
@Composable
fun AccountNotProvidingContent(
    error: String? = null,
    onSignInAnonymously: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = AppStrings.ACCOUNT_SECTION_TITLE,
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = AppStrings.ACCOUNT_NOT_PROVIDING_DESCRIPTION,
            style = MaterialTheme.typography.bodyMedium,
        )

        // 作る前に読む内容が画面ごとに食い違わないよう、オンボーディングと同じ定数を引く。
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppStrings.CONSENT_WITH_ACCOUNT_POINTS.forEach { point ->
                    Row {
                        Text(text = "・", style = MaterialTheme.typography.bodySmall)
                        Text(text = point, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onSignInAnonymously,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(AppStrings.ACCOUNT_ENABLE_BUTTON)
        }

        LegalLinksRow(onOpenTerms = onOpenTerms)
    }
}

/** 作成済みの画面。uid・メールアドレスは表示しない。 */
@Composable
fun AccountProvidingContent(
    uploadedCount: Int = 0,
    pendingCount: Int = 0,
    autoUpload: Boolean = false,
    error: String? = null,
    isUploading: Boolean = false,
    uploadResultMessage: String? = null,
    onAutoUploadChange: (Boolean) -> Unit = {},
    onManualUpload: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    showDeleteDialogInitially: Boolean = false,
) {
    var showDeleteDialog by remember { mutableStateOf(showDeleteDialogInitially) }

    if (showDeleteDialog) {
        DeleteDataConfirmDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteAccount()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = AppStrings.ACCOUNT_SECTION_TITLE,
            style = MaterialTheme.typography.headlineSmall,
        )

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = AppStrings.ACCOUNT_PROVIDING_STATUS,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = AppStrings.accountUploadedCount(uploadedCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 機種変更注意書き
        Text(
            text = AppStrings.ACCOUNT_DEVICE_TRANSFER_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 自動アップロードトグル
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.ACCOUNT_AUTO_UPLOAD_LABEL,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = AppStrings.ACCOUNT_AUTO_UPLOAD_DESC,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoUpload,
                    onCheckedChange = onAutoUploadChange,
                )
            }
        }

        // 手動アップロードボタン（未アップロードがある場合のみ表示）
        if (pendingCount > 0 || uploadResultMessage != null) {
            Button(
                onClick = onManualUpload,
                enabled = !isUploading && pendingCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(AppStrings.accountManualUploadButton(pendingCount))
                }
            }
            if (uploadResultMessage != null) {
                Text(
                    text = uploadResultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // エラー表示（データ削除失敗など）
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 提供をやめてデータを削除（朱アウトライン・確認ダイアログ必須）。
        // DESIGN.mdのdestructive規定（朱アウトライン・角丸8dp）に合わせ、shape/borderを
        // 明示的に上書きする（M3のOutlinedButton既定はfull角丸・グレー枠のため）。
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(AppStrings.ACCOUNT_DISABLE_BUTTON)
        }

        LegalLinksRow(onOpenTerms = onOpenTerms)
    }
}

/**
 * データ削除の確認ダイアログ。
 * サーバー上の全データが削除され取り消せないことを明示する。
 */
@Composable
fun DeleteDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.ACCOUNT_DELETE_DIALOG_TITLE) },
        text = {
            Text(AppStrings.ACCOUNT_DELETE_DIALOG_TEXT)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = AppStrings.ACCOUNT_DELETE_CONFIRM,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

// ─── 法務導線（規約・ライセンス） ──────────────────────────────────────────────

/**
 * 利用規約・プライバシーポリシーへの控えめな導線。
 * 未提供・提供中の両方の画面下部に表示する。
 * OSSライセンスは設定画面に集約されているため、ここには含めない。
 */
@Composable
fun LegalLinksRow(
    onOpenTerms: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onOpenTerms) {
            Text(
                text = AppStrings.SETTINGS_ROW_TERMS,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewNotProviding() {
    ShogiTheme {
        Surface {
            AccountNotProvidingContent()
        }
    }
}

@Preview
@Composable
private fun PreviewNotProvidingWithError() {
    ShogiTheme {
        Surface {
            AccountNotProvidingContent(
                error = AppStrings.AUTH_ERROR_ANON_SIGN_IN_GENERIC,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewProviding() {
    ShogiTheme {
        Surface {
            AccountProvidingContent(
                uploadedCount = 12,
                autoUpload = false,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewProvidingAutoUploadOn() {
    ShogiTheme {
        Surface {
            AccountProvidingContent(
                uploadedCount = 12,
                autoUpload = true,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewDeleteDialog() {
    ShogiTheme {
        Surface {
            DeleteDataConfirmDialog(onConfirm = {}, onDismiss = {})
        }
    }
}
