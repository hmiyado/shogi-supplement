package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 悪手ゼロの対局でも悪手率・一致率は算出できるため、noBlundersMessage の下に
 * 続けて表示する（一覧への導線ボタンだけ出さない）。
 */@Composable
internal fun BlunderSummaryCard(
    reports: List<BlunderRecord>,
    noBlundersMessage: String,
    strengthDisplayText: String?,
    matchRateDisplayText: String?,
    blunderRateDisplayText: String?,
    onViewList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (reports.isEmpty()) {
                Text(noBlundersMessage, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }

            // 悪手率・一致率（同格のフォントサイズ・2行。ラベルは通常書体、値のみMono）。
            if (blunderRateDisplayText != null) {
                StatLine(AppStrings.BLUNDER_RATE_LABEL, blunderRateDisplayText)
            }
            if (matchRateDisplayText != null) {
                if (blunderRateDisplayText != null) Spacer(Modifier.height(2.dp))
                StatLine(AppStrings.MATCH_RATE_LABEL, matchRateDisplayText)
            }

            if (strengthDisplayText != null) {
                if (blunderRateDisplayText != null || matchRateDisplayText != null) {
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AppStrings.GAME_STRENGTH_PREFIX,
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink2,
                    )
                    Text(
                        strengthDisplayText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = IbmPlexMonoFamily),
                        color = shogiColors.ink2,
                    )
                }
            }

            if (reports.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onViewList, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.VIEW_BLUNDER_LIST, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * 「ラベル: 値」の1行（同格のフォントサイズ。値のみMono）。
 * 悪手率・エンジン一致率の2行で共有するスタイル。
 */
@Composable
private fun StatLine(label: String, value: String) {
    val text = buildAnnotatedString {
        append(label)
        withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(value) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
