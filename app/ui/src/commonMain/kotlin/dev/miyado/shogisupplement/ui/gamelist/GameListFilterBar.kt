package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameResultFilter
import dev.miyado.shogisupplement.db.distinctSources
import dev.miyado.shogisupplement.db.hasResultData
import dev.miyado.shogisupplement.db.hasUserSideData
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.shogiColors
import dev.miyado.shogisupplement.util.currentEpochSeconds

private const val SECONDS_PER_DAY = 24L * 60 * 60

/**
 * 棋譜一覧の絞り込みバー。
 *
 * 表示する軸・チップは[allGames]（絞り込み前の全件）から実在する値のみを組み立てる
 * （データが無い軸・値のチップは作らない。miyadoさん指示）。
 *
 * 件数表示・全解除は[GameListScreen]側に置く
 * （このバーは軸ごとのチップ選択のみを担当し、結果表示とは責務を分ける）。
 *
 * 期間チップの基準時刻（「いま」）はバーの初回コンポジション時に1回だけ固定する
 * （[remember]）。再コンポジションのたびに再計算すると、選択済みチップの
 * dateFrom値と比較がずれて選択状態が解除されて見えてしまうため。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameListFilterBar(
    allGames: List<GameRecord>,
    filter: GameListFilter,
    onFilterChange: (GameListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = allGames.distinctSources()
    val showSideAxis = allGames.hasUserSideData()
    val showResultAxis = allGames.hasResultData()
    val now = remember { currentEpochSeconds() }
    val dateFrom7d = now - 7 * SECONDS_PER_DAY
    val dateFrom30d = now - 30 * SECONDS_PER_DAY

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isNotEmpty()) {
            FilterAxisRow(AppStrings.GAME_LIST_FILTER_SOURCE) {
                sources.forEach { source ->
                    FilterChipItem(
                        label = AppStrings.sourceFilterLabel(source),
                        selected = filter.source == source,
                        testTag = "filter_chip_source_$source",
                        onClick = {
                            onFilterChange(filter.copy(source = if (filter.source == source) null else source))
                        },
                    )
                }
            }
        }
        if (showSideAxis) {
            FilterAxisRow(AppStrings.GAME_LIST_FILTER_SIDE) {
                listOf("sente" to AppStrings.PLAYER_SIDE_SENTE, "gote" to AppStrings.PLAYER_SIDE_GOTE)
                    .forEach { (value, label) ->
                        FilterChipItem(
                            label = label,
                            selected = filter.userSide == value,
                            testTag = "filter_chip_side_$value",
                            onClick = {
                                onFilterChange(
                                    filter.copy(userSide = if (filter.userSide == value) null else value),
                                )
                            },
                        )
                    }
            }
        }
        if (showResultAxis) {
            FilterAxisRow(AppStrings.GAME_LIST_FILTER_RESULT) {
                listOf(
                    GameResultFilter.WIN to AppStrings.GAME_RESULT_WIN,
                    GameResultFilter.LOSS to AppStrings.GAME_RESULT_LOSS,
                ).forEach { (value, label) ->
                    FilterChipItem(
                        label = label,
                        selected = filter.result == value,
                        testTag = "filter_chip_result_${value.name}",
                        onClick = {
                            onFilterChange(filter.copy(result = if (filter.result == value) null else value))
                        },
                    )
                }
            }
        }
        FilterAxisRow(AppStrings.GAME_LIST_FILTER_PERIOD) {
            listOf(
                Triple(dateFrom7d, AppStrings.GAME_LIST_FILTER_PERIOD_7D, "7d"),
                Triple(dateFrom30d, AppStrings.GAME_LIST_FILTER_PERIOD_30D, "30d"),
            ).forEach { (candidateDateFrom, label, tagSuffix) ->
                val selected = filter.dateFrom == candidateDateFrom
                FilterChipItem(
                    label = label,
                    selected = selected,
                    testTag = "filter_chip_period_$tagSuffix",
                    onClick = {
                        onFilterChange(filter.copy(dateFrom = if (selected) null else candidateDateFrom))
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterAxisRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.shogiColors.ink3,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    val containerColor = if (selected) shogiColors.primarySoft else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else shogiColors.ink2
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else shogiColors.line

    Row(
        // testTag は視覚に影響しないため golden 画像には無関係（VRTからの一意なチップ特定用）。
        modifier = Modifier
            .testTag(testTag)
            .background(containerColor, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

@Composable
fun GameListCountText(shownCount: Int, totalCount: Int) {
    val text = if (shownCount == totalCount) {
        AppStrings.gameListTotalCount(totalCount)
    } else {
        AppStrings.gameListFilteredCount(shownCount, totalCount)
    }
    Text(text, style = TextStyleData)
}
