package dev.miyado.shogisupplement.ui.strength

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.TextStyleDataLarge
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.roundToInt

/**
 * 推定棋力詳細画面。現在の推定棋力（対局サービスでの最高段級位を併記）／対局ごとの推移／対局サービスの3カード構成。
 * 対局サービスの編集ダイアログ自体は持たず、[onEditAccounts] で呼び出し元にホイストする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimatedStrengthDetailScreen(
    data: StrengthDetailData,
    onBack: () -> Unit,
    onEditAccounts: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.STRENGTH_DETAIL_TITLE) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(data) }
            if (data.trend.isNotEmpty()) {
                item { TrendCard(data.trend) }
            }
            item { AccountsCard(data.services, onEditAccounts) }
        }
    }
}

// ─── 現在の推定棋力 ─────────────────────────────────────────────────────────────

@Composable
private fun OverviewCard(data: StrengthDetailData) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    AppStrings.STRENGTH_DETAIL_EYEBROW,
                    style = MaterialTheme.typography.labelMedium,
                    color = shogiColors.ink2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = data.deviation.toString(),
                    style = TextStyleDataLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    AppStrings.strengthDetailRange(data.rangeLow, data.rangeHigh),
                    style = TextStyleData,
                    color = shogiColors.ink2,
                )
            }
            if (data.bestRank != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        AppStrings.STRENGTH_DETAIL_BEST_RANK_CAPTION,
                        style = MaterialTheme.typography.labelMedium,
                        color = shogiColors.ink2,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        data.bestRank.label,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        data.bestRank.ruleLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = shogiColors.ink2,
                    )
                }
            }
        }
    }
}

// ─── 対局ごとの推移 ─────────────────────────────────────────────────────────────

private const val TREND_CHART_HEIGHT_DP = 160

@Composable
private fun TrendCard(trend: List<StrengthTrendPoint>) {
    val shogiColors = MaterialTheme.shogiColors
    var selectedIndex by remember(trend) { mutableStateOf(trend.lastIndex) }
    val selected = trend[selectedIndex]

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(AppStrings.STRENGTH_DETAIL_TREND_TITLE, style = MaterialTheme.typography.titleLarge)
                Text(
                    AppStrings.strengthDetailTrendLabel(trend.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = shogiColors.ink2,
                )
            }
            Spacer(Modifier.height(10.dp))

            val minY = remember(trend) { trend.minOf { it.deviation - it.deviationWidth } - 3 }
            val maxY = remember(trend) { trend.maxOf { it.deviation + it.deviationWidth } + 3 }
            Row(Modifier.fillMaxWidth().height(TREND_CHART_HEIGHT_DP.dp)) {
                Column(
                    modifier = Modifier.width(28.dp).fillMaxHeight().padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(maxY.toString(), style = TextStyleData, color = shogiColors.ink3)
                    Text(((minY + maxY) / 2).toString(), style = TextStyleData, color = shogiColors.ink3)
                    Text(minY.toString(), style = TextStyleData, color = shogiColors.ink3)
                }
                Spacer(Modifier.width(4.dp))
                TrendChart(
                    trend = trend,
                    minY = minY,
                    maxY = maxY,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trend.first().dateLabel, style = TextStyleData, color = shogiColors.ink3)
                Text(trend.last().dateLabel, style = TextStyleData, color = shogiColors.ink3)
            }

            Spacer(Modifier.height(8.dp))
            SelectedGameRow(selected)
            Spacer(Modifier.height(10.dp))
            Text(
                AppStrings.STRENGTH_DETAIL_TREND_CAPTION,
                style = MaterialTheme.typography.labelSmall,
                color = shogiColors.ink2,
            )
        }
    }
}

@Composable
private fun TrendChart(
    trend: List<StrengthTrendPoint>,
    minY: Int,
    maxY: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shogiColors = MaterialTheme.shogiColors
    val gridColor = shogiColors.line
    val lineColor = MaterialTheme.colorScheme.primary
    val confidenceColor = shogiColors.primarySoft
    val dotFill = MaterialTheme.colorScheme.surface
    val currentDotFill = shogiColors.highlight
    val currentDotStroke = MaterialTheme.colorScheme.onSurface
    val yRange = (maxY - minY).coerceAtLeast(1)

    Canvas(
        modifier = modifier.pointerInput(trend) {
            detectTapGestures { offset ->
                onSelect(nearestPointIndex(offset.x, size.width, trend.size))
            }
        },
    ) {
        val w = size.width
        val h = size.height
        fun xOf(i: Int): Float = if (trend.size <= 1) w / 2f else w * i / (trend.size - 1)
        fun yOf(v: Int): Float = h - h * (v - minY).toFloat() / yRange

        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = h * frac
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        if (trend.size >= 2) {
            val upper = trend.mapIndexed { i, p -> Offset(xOf(i), yOf(p.deviation + p.deviationWidth)) }
            val lower = trend.mapIndexed { i, p -> Offset(xOf(i), yOf(p.deviation - p.deviationWidth)) }
            val band = Path().apply {
                moveTo(upper.first().x, upper.first().y)
                upper.drop(1).forEach { lineTo(it.x, it.y) }
                lower.reversed().forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(band, color = confidenceColor)

            for (i in 0 until trend.size - 1) {
                drawLine(
                    color = lineColor,
                    start = Offset(xOf(i), yOf(trend[i].deviation)),
                    end = Offset(xOf(i + 1), yOf(trend[i + 1].deviation)),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
        }

        trend.forEachIndexed { i, p ->
            val center = Offset(xOf(i), yOf(p.deviation))
            val isLatest = i == trend.lastIndex
            if (isLatest) {
                drawCircle(currentDotStroke, radius = 6.dp.toPx(), center = center)
                drawCircle(currentDotFill, radius = 4.5.dp.toPx(), center = center)
            } else {
                drawCircle(lineColor, radius = 5.dp.toPx(), center = center)
                drawCircle(dotFill, radius = 3.5.dp.toPx(), center = center)
            }
        }
    }
}

/** タップ位置のxから最も近い点のインデックスを返す。 */
internal fun nearestPointIndex(x: Float, widthPx: Int, pointCount: Int): Int {
    if (pointCount <= 1 || widthPx <= 0) return 0
    val step = widthPx.toFloat() / (pointCount - 1)
    return (x / step).roundToInt().coerceIn(0, pointCount - 1)
}

@Composable
private fun SelectedGameRow(point: StrengthTrendPoint) {
    val shogiColors = MaterialTheme.shogiColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(shogiColors.surface2, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val meta = AppStrings.strengthDetailSelectedMeta(
            point.blunderRateText ?: AppStrings.STRENGTH_DETAIL_RULE_UNSET,
            point.matchRateText ?: AppStrings.STRENGTH_DETAIL_RULE_UNSET,
        )
        Text(meta, style = MaterialTheme.typography.labelMedium, color = shogiColors.ink2)
        Text(
            point.deviation.toString(),
            style = TextStyleData.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── 対局サービス ───────────────────────────────────────────────────────────────

@Composable
private fun AccountsCard(services: List<StrengthDetailService>, onEdit: () -> Unit) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(AppStrings.STRENGTH_DETAIL_ACCOUNTS_TITLE, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onEdit) {
                    Text(AppStrings.STRENGTH_DETAIL_ACCOUNTS_EDIT, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                AppStrings.STRENGTH_DETAIL_ACCOUNTS_LEDE,
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink2,
            )
            if (services.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    AppStrings.STRENGTH_DETAIL_ACCOUNTS_EMPTY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = shogiColors.ink3,
                )
            } else {
                services.forEachIndexed { index, service ->
                    if (index > 0) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = shogiColors.line)
                    }
                    ServiceBlock(service, isFirst = index == 0)
                }
            }
        }
    }
}

@Composable
private fun ServiceBlock(service: StrengthDetailService, isFirst: Boolean) {
    val shogiColors = MaterialTheme.shogiColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 12.dp else 14.dp),
    ) {
        Text(service.label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(3.dp))
        Text(
            service.accountName ?: AppStrings.STRENGTH_DETAIL_ACCOUNT_NAME_UNSET,
            style = MaterialTheme.typography.labelMedium,
            color = if (service.accountName == null) shogiColors.ink3 else shogiColors.ink2,
        )
        if (service.rules.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                service.rules.forEach { rule -> ServiceRuleRow(rule) }
            }
        } else if (service.ratingText != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(AppStrings.STRENGTH_DETAIL_LISHOGI_RATING_LABEL, style = MaterialTheme.typography.bodyMedium)
                Text(service.ratingText, style = TextStyleData.copy(fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ServiceRuleRow(rule: StrengthDetailServiceRule) {
    val shogiColors = MaterialTheme.shogiColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rule.ruleLabel, style = MaterialTheme.typography.bodyMedium)
        if (rule.rankLabel != null) {
            Text(rule.rankLabel, style = TextStyleData.copy(fontSize = 13.sp))
        } else {
            Text(
                AppStrings.STRENGTH_DETAIL_RULE_UNSET,
                style = MaterialTheme.typography.bodyMedium,
                color = shogiColors.ink3,
            )
        }
    }
}
