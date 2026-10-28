package dev.miyado.shogisupplement.ui.strength

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.strength.MateBucket
import dev.miyado.shogisupplement.strength.MateStatistics
import dev.miyado.shogisupplement.ui.generated.resources.Res
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_bar_description
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_bucket_description
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_coverage
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_counts
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_empty
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_last_bucket
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_missing
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_no_sample
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_overflow_description
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_percent
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_subtitle
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_title
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_x_axis
import dev.miyado.shogisupplement.ui.generated.resources.mate_statistics_y_axis
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val MATE_CHART_HEIGHT_DP = 220
private const val MATE_PLOT_HEIGHT_DP = 200
private const val MATE_TOP_LABEL_SPACE_DP = 20
private const val MATE_Y_AXIS_WIDTH_DP = 36
private const val MATE_BUCKET_MIN_WIDTH_DP = 44
private const val MATE_X_LABELS_HEIGHT_DP = 44
private const val MATE_CHART_TOTAL_HEIGHT_DP = MATE_CHART_HEIGHT_DP + MATE_X_LABELS_HEIGHT_DP
private const val MATE_BAR_WIDTH_DP = 22
private const val MATE_SCROLL_TEST_TAG = "mate_statistics_scroll"

@Composable
fun MateStatisticsCard(statistics: MateStatistics) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 12.dp)) {
            Text(stringResource(Res.string.mate_statistics_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.mate_statistics_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink2,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.mate_statistics_y_axis),
                style = MaterialTheme.typography.labelSmall,
                color = shogiColors.ink2,
            )
            Spacer(Modifier.height(4.dp))
            MateChart(statistics)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = shogiColors.line)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    Res.string.mate_statistics_coverage,
                    statistics.analyzedGames,
                    statistics.verifiedGames,
                ),
                style = TextStyleData,
                color = shogiColors.ink2,
            )
        }
    }
}

@Composable
private fun MateChart(statistics: MateStatistics) {
    val shogiColors = MaterialTheme.shogiColors
    val scrollState = rememberScrollState()
    val buckets = statistics.buckets

    Row(Modifier.fillMaxWidth().height(MATE_CHART_TOTAL_HEIGHT_DP.dp)) {
        Box(Modifier.width(MATE_Y_AXIS_WIDTH_DP.dp).height(MATE_CHART_HEIGHT_DP.dp)) {
            listOf(100, 75, 50, 25, 0).forEach { percent ->
                Text(
                    "$percent",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            y = (
                                MATE_TOP_LABEL_SPACE_DP +
                                    MATE_PLOT_HEIGHT_DP * (1 - percent / 100f) -
                                    8
                                ).dp,
                        )
                        .wrapContentHeight(unbounded = true),
                    style = TextStyleData.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = shogiColors.ink3,
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(Modifier.weight(1f).height(MATE_CHART_TOTAL_HEIGHT_DP.dp)) {
            val contentWidth = maxOf(
                maxWidth,
                MATE_BUCKET_MIN_WIDTH_DP.dp * buckets.size.coerceAtLeast(1).toFloat(),
            )
            Column(Modifier.fillMaxWidth().horizontalScroll(scrollState).testTag(MATE_SCROLL_TEST_TAG)) {
                Box(Modifier.width(contentWidth).height(MATE_CHART_HEIGHT_DP.dp)) {
                    Canvas(
                        Modifier
                            .align(Alignment.BottomStart)
                            .width(contentWidth)
                            .height(MATE_PLOT_HEIGHT_DP.dp),
                    ) {
                        listOf(0, 25, 50, 75, 100).forEach { percent ->
                            val y = size.height - size.height * percent / 100f
                            drawLine(
                                color = shogiColors.line,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                    if (buckets.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.mate_statistics_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = shogiColors.ink2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            buckets.forEach { bucket -> MateBar(bucket, Modifier.weight(1f)) }
                        }
                    }
                }
                if (buckets.isNotEmpty()) {
                    Row(
                        Modifier
                            .width(contentWidth)
                            .height(MATE_X_LABELS_HEIGHT_DP.dp)
                            .padding(top = 8.dp),
                    ) {
                        buckets.forEach { bucket ->
                            MateBucketLabels(bucket, Modifier.weight(1f))
                        }
                    }
                } else {
                    Spacer(Modifier.width(contentWidth).height(MATE_X_LABELS_HEIGHT_DP.dp))
                }
            }
        }
    }
    Text(
        stringResource(Res.string.mate_statistics_x_axis),
        modifier = Modifier.fillMaxWidth().padding(start = (MATE_Y_AXIS_WIDTH_DP + 8).dp),
        style = MaterialTheme.typography.labelSmall,
        color = shogiColors.ink2,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MateBar(bucket: MateBucket, modifier: Modifier = Modifier) {
    val rate = bucket.successRate
    val description = barDescription(bucket)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (rate == null) {
            Text(
                stringResource(Res.string.mate_statistics_missing),
                style = TextStyleData,
                color = MaterialTheme.shogiColors.ink3,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.mate_statistics_percent, (rate * 100).roundToInt()),
                    style = TextStyleData.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(MATE_BAR_WIDTH_DP.dp)
                        .height((MATE_PLOT_HEIGHT_DP * rate).toFloat().dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun MateBucketLabels(bucket: MateBucket, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (bucket.plies == MateStatistics.LAST_BUCKET) {
                stringResource(Res.string.mate_statistics_last_bucket)
            } else {
                bucket.plies.toString()
            },
            style = TextStyleData,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.mate_statistics_counts, bucket.found, bucket.decided),
            style = TextStyleData,
            color = MaterialTheme.shogiColors.ink2,
        )
    }
}

@Composable
private fun barDescription(bucket: MateBucket): String {
    val label = if (bucket.plies == MateStatistics.LAST_BUCKET) {
        stringResource(Res.string.mate_statistics_overflow_description)
    } else {
        stringResource(Res.string.mate_statistics_bucket_description, bucket.plies)
    }
    val rate = bucket.successRate
        ?: return stringResource(Res.string.mate_statistics_no_sample, label)
    return stringResource(
        Res.string.mate_statistics_bar_description,
        label,
        (rate * 100).roundToInt(),
        bucket.found,
        bucket.decided,
    )
}
