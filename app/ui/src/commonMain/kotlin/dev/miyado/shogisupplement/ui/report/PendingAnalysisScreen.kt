package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingAnalysisScreen(
    game: GameRecord,
    onBack: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.sourcePlaceLabel(game.sourcePlace) ?: game.fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.BACK)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(AppStrings.PENDING_ANALYSIS_TITLE, style = MaterialTheme.typography.headlineSmall)
            Text(AppStrings.PENDING_ANALYSIS_BODY, style = MaterialTheme.typography.bodyLarge)
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
