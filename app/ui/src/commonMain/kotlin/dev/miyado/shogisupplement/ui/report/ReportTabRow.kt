package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.shogiColors

private val TabHeight = 44.dp
private val IndicatorThickness = 2.dp

// Why not ボタンを並べる: 枠線と座布団のある形は操作ボタンに見え、いま何を見ているかの
// 表示に読めない。選択中だけを紺青と下辺のインジケータで示す。
/** レポート画面の表示切替。盤の下・本文の上に置く。 */
@Composable
internal fun ReportTabRow(
    active: ReportBodyMode,
    onSelect: (ReportBodyMode) -> Unit,
    isEnabled: (ReportBodyMode) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TabHeight)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ReportBodyMode.entries.forEach { mode ->
            val label = when (mode) {
                ReportBodyMode.SUMMARY -> AppStrings.REPORT_TAB_SUMMARY
                ReportBodyMode.LIST -> AppStrings.REPORT_TAB_BLUNDERS
                ReportBodyMode.STUDY -> AppStrings.REPORT_TAB_STUDY
            }
            IndicatorTab(
                label = label,
                isActive = active == mode,
                enabled = isEnabled(mode),
                onClick = { onSelect(mode) },
            )
        }
    }
}

// 線を下辺に出す理由: 指している内容がタブより下にある。上辺だと盤の側を指すことになる。
/** タブ1つ。 */
@Composable
private fun RowScope.IndicatorTab(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    val edge = Alignment.BottomCenter
    Box(modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = enabled, onClick = onClick)) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(edge)
                    .fillMaxWidth()
                    .height(IndicatorThickness)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            HorizontalDivider(color = shogiColors.line, modifier = Modifier.align(edge))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            ),
            color = when {
                isActive -> MaterialTheme.colorScheme.primary
                enabled -> shogiColors.ink2
                else -> shogiColors.ink3
            },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
