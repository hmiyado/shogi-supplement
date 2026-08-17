package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings

@Composable
internal fun PendingAnalysisCard(
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(AppStrings.PENDING_ANALYSIS_TITLE, style = MaterialTheme.typography.titleMedium)
            Text(AppStrings.PENDING_ANALYSIS_BODY, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onAnalyze,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("pending_analysis_button"),
            ) {
                Text(AppStrings.ANALYZE_GAME)
            }
        }
    }
}
