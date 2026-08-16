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
import androidx.compose.material3.RadioButton
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
 * はじめに。どちらかを選ぶまで他の画面へ進めない。
 * Why not 確定処理を持つ: サインイン・登録は画面の外が担う。
 */
@Composable
fun ConsentScreen(
    isSubmitting: Boolean = false,
    onAccept: (withAccount: Boolean) -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    var withAccount by remember { mutableStateOf(true) }

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
                Spacer(Modifier.height(8.dp))
                ConsentOption(
                    label = AppStrings.CONSENT_WITH_ACCOUNT_LABEL,
                    points = AppStrings.CONSENT_WITH_ACCOUNT_POINTS,
                    selected = withAccount,
                    onSelect = { withAccount = true },
                )
                Spacer(Modifier.height(8.dp))
                ConsentOption(
                    label = AppStrings.CONSENT_WITHOUT_ACCOUNT_LABEL,
                    points = AppStrings.CONSENT_WITHOUT_ACCOUNT_POINTS,
                    selected = !withAccount,
                    onSelect = { withAccount = false },
                )
            }

            // Why not 選択肢と同じスクロール領域に置く: 直前の選択肢に属する説明に見える。
            // 規約はどちらを選んでも共通の前提のため、ボタンの直上へ置く。
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenTerms) {
                Text(
                    text = AppStrings.SETTINGS_ROW_TERMS,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { onAccept(withAccount) },
                enabled = !isSubmitting,
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

/** Why not チェックボックス: 二者択一のため、両方選べるように見える形にしない。 */
@Composable
private fun ConsentOption(
    label: String,
    points: List<String>,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            points.forEach { point ->
                // 折り返した2行目が行頭へ戻ると箇条書きに見えないため、記号と本文を分けて並べる。
                Row {
                    Text(
                        text = "・",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.shogiColors.ink2,
                    )
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.shogiColors.ink2,
                    )
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
