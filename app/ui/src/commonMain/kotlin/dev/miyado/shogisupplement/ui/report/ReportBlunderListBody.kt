package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun ReportBlunderListBody(
    onBackToSummary: () -> Unit,
    viewerMode: ViewerMode,
    hasBestPv: Boolean,
    onSelectMainlineTab: () -> Unit,
    onSelectBestPvTab: () -> Unit,
    reports: List<BlunderRecord>,
    noBlundersMessage: String,
    selectedIdx: Int?,
    evalDisplay: String,
    onSelectBlunder: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(32.dp)
                .clickable(onClick = onBackToSummary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = AppStrings.BACK_TO_SUMMARY,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                AppStrings.BACK_TO_SUMMARY,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.shogiColors.ink2,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReportViewerTab(
                label = AppStrings.TAB_MAINLINE,
                isActive = viewerMode == ViewerMode.MAINLINE,
                enabled = true,
                modifier = Modifier.weight(1f).height(36.dp),
                onClick = onSelectMainlineTab,
            )
            ReportViewerTab(
                label = AppStrings.TAB_BEST_PV,
                isActive = viewerMode == ViewerMode.BEST_PV,
                enabled = hasBestPv,
                modifier = Modifier.weight(1f).height(36.dp),
                onClick = onSelectBestPvTab,
            )
        }

        if (reports.isEmpty()) {
            Text(
                noBlundersMessage,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                itemsIndexed(reports) { idx, report ->
                    BlunderCard(
                        report = report,
                        isSelected = selectedIdx == idx,
                        evalDisplay = evalDisplay,
                        onClick = { onSelectBlunder(idx) },
                    )
                }
            }
        }
    }
}
