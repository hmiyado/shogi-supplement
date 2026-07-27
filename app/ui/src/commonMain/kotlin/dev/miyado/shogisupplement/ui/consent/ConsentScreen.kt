package dev.miyado.shogisupplement.ui.consent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * 同意オンボーディング（iOS専用・初回起動必須）の全画面。
 *
 * 設計書「認証・同意モデルの変更（iOS＝同意必須）」節が仕様の正: 利用規約・プライバシー
 * ポリシーへのリンク＋研究利用（データ提供）への同意チェックが揃わないと先へ進めない。
 * 戻るボタンを持たない＝スキップ不可（呼び出し側もこの画面を表示中は他ルートへ
 * 遷移させない。iOS の MainViewController 参照）。
 *
 * 同意後の実処理（同意フラグ保存→匿名サインイン→自動アップロードON→
 * 引き継ぎシークレット登録）は呼び出し側が
 * [dev.miyado.shogisupplement.consent.ConsentOrchestrator] を呼んで担う。本Composableは
 * チェック状態と送信中インジケータのみを持つ、状態hoisting方式（AccountScreen等と同型）。
 *
 * @param isSubmitting 同意確定処理（サインイン等）の実行中。ボタン内にインジケータを出し、
 *   二重タップを防ぐ（AccountProvidingContent の手動アップロードボタンと同じパターン）。
 */
@Composable
fun ConsentScreen(
    isSubmitting: Boolean = false,
    onAccept: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    var researchConsentChecked by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = AppStrings.CONSENT_TITLE,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = AppStrings.CONSENT_INTRO,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = AppStrings.CONSENT_BETA_NOTICE,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { researchConsentChecked = !researchConsentChecked },
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = researchConsentChecked,
                        onCheckedChange = { researchConsentChecked = it },
                    )
                    Text(
                        text = AppStrings.CONSENT_RESEARCH_UPLOAD_TEXT,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                TextButton(onClick = onOpenTerms) {
                    Text(
                        text = AppStrings.SETTINGS_ROW_TERMS,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = AppStrings.CONSENT_REQUIRED_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.shogiColors.ink3,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAccept,
                enabled = researchConsentChecked && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(AppStrings.CONSENT_ACCEPT_BUTTON)
                }
            }
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewConsentScreen() {
    ShogiTheme {
        ConsentScreen()
    }
}

@Preview
@Composable
private fun PreviewConsentScreenSubmitting() {
    ShogiTheme {
        ConsentScreen(isSubmitting = true)
    }
}

@Preview
@Composable
private fun PreviewConsentScreenDark() {
    ShogiTheme(themeMode = "dark") {
        ConsentScreen()
    }
}
