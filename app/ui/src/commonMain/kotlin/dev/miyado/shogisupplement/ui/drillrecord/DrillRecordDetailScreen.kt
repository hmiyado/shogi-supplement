package dev.miyado.shogisupplement.ui.drillrecord

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.TextStyleDataLarge
import dev.miyado.shogisupplement.ui.theme.shogiColors

/** 密度グリッドの列数。30日を10×3で並べる。 */
private const val GRID_COLUMNS = 10

/**
 * 学習の記録の詳細画面。概要／30日の密度グリッド／7日間連続の達成の3カード構成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillRecordDetailScreen(
    data: DrillRecordDetailData,
    onBack: () -> Unit,
    onShare: () -> Unit = {},
) {
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.drill_record_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(vectorResource(Res.drawable.ic_outlined_share), contentDescription = stringResource(Res.string.share_screen))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).adaptiveContentWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(data) }
            item { DensityGridCard(data.dailyCounts) }
            item { WeekStreakCard(data.weekStreaks) }
        }
    }
}

// ─── 概要 ───────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewCard(data: DrillRecordDetailData) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(Res.string.drill_record_detail_active_days_label),
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink2,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = data.activeDaysInWindow.toString(),
                    style = TextStyleDataLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.drill_record_active_days_suffix),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.drill_record_window_prefix))
                        withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
                            append(data.windowDays.toString())
                        }
                        append(stringResource(Res.string.drill_record_window_suffix))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = shogiColors.ink2,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.drill_record_total_prefix))
                    withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
                        append(data.totalAttempts.toString())
                    }
                    append(stringResource(Res.string.drill_record_total_suffix))
                    append(" ・ ")
                    append(stringResource(Res.string.drill_record_correct_prefix))
                    withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
                        append(UiStrings.drillRecordAccuracy(data.correctAttempts, data.totalAttempts))
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink3,
            )
        }
    }
}

// ─── 30日の密度グリッド ─────────────────────────────────────────────────────────

@Composable
private fun DensityGridCard(dailyCounts: List<Int>) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(Res.string.drill_record_detail_grid_title), style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dailyCounts.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { columnIndex, count ->
                            val isToday = (rowIndex * GRID_COLUMNS + columnIndex) == dailyCounts.lastIndex
                            DayCell(count = count, isToday = isToday, modifier = Modifier.weight(1f))
                        }
                        // 余った列にもSpacerを置く。置かないと最終行のマスだけ横に伸びる。
                        repeat(GRID_COLUMNS - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.drill_record_detail_grid_less),
                    style = MaterialTheme.typography.labelMedium,
                    color = shogiColors.ink3,
                )
                Spacer(Modifier.width(6.dp))
                listOf(0, 1, 3, 6).forEach { sample ->
                    // 凡例はマスが小さいため角丸を浅くする（同じ4dpだと丸く見える）。
                    DayCell(
                        count = sample,
                        isToday = false,
                        modifier = Modifier.size(10.dp),
                        cornerRadius = 2.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(2.dp))
                Text(
                    stringResource(Res.string.drill_record_detail_grid_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = shogiColors.ink3,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(Res.string.drill_record_detail_grid_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = shogiColors.ink3,
                )
            }
        }
    }
}

/**
 * 1日ぶんのマス。解答数で濃さが4段階変わる。
 * 今日は卵黄の枠を巻く（面としての卵黄＝いま注目、の用法）。
 */
@Composable
private fun DayCell(
    count: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
) {
    val shogiColors = MaterialTheme.shogiColors
    val primary = MaterialTheme.colorScheme.primary
    val fill = when {
        count == 0 -> Color.Transparent
        count <= 2 -> shogiColors.primarySoft
        count <= 5 -> primary.copy(alpha = 0.42f)
        else -> primary
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(fill, RoundedCornerShape(cornerRadius))
            .then(
                if (isToday) {
                    Modifier.border(2.dp, shogiColors.highlight, RoundedCornerShape(cornerRadius))
                } else if (count == 0) {
                    Modifier.border(1.dp, shogiColors.line, RoundedCornerShape(cornerRadius))
                } else {
                    Modifier
                },
            ),
    )
}

// ─── 7日間連続の達成 ────────────────────────────────────────────────────────────

@Composable
private fun WeekStreakCard(streaks: List<WeekStreakRow>) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(Res.string.drill_record_detail_streak_title), style = MaterialTheme.typography.titleLarge)
            if (streaks.isEmpty()) {
                // Why not カードごと消す: 達成の有無で画面の高さが変わってしまう。
                Text(
                    stringResource(Res.string.drill_record_detail_streak_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = shogiColors.ink3,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                streaks.forEachIndexed { index, streak ->
                    if (index > 0) HorizontalDivider(color = shogiColors.line)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            UiStrings.drillRecordStreakOrdinal(streak.ordinal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = shogiColors.ink2,
                        )
                        Text(
                            UiStrings.drillRecordStreakRange(streak.startLabel, streak.endLabel),
                            style = TextStyleData,
                            color = shogiColors.ink2,
                        )
                    }
                }
            }
        }
    }
}
