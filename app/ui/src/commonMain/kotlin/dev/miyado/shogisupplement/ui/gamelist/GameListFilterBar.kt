package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameResultFilter
import dev.miyado.shogisupplement.db.distinctOpeningStyles
import dev.miyado.shogisupplement.db.distinctSources
import dev.miyado.shogisupplement.db.hasResultData
import dev.miyado.shogisupplement.db.hasUserSideData
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.shogiColors
import dev.miyado.shogisupplement.util.currentEpochSeconds

private const val SECONDS_PER_DAY = 24L * 60 * 60

/** 棋譜一覧の絞り込みヘッダー。条件の詳細を常設せず、行高を固定してno-jitterを保つ。 */
@Composable
fun GameListFilterHeader(
    activeCount: Int,
    shownCount: Int,
    totalCount: Int,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameListFilterButton(activeCount = activeCount, onClick = onOpenFilter)
        GameListCountText(shownCount = shownCount, totalCount = totalCount)
    }
}

@Composable
private fun GameListFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    // 適用中（activeCount > 0）は判定チップと同じ紺青系（primarySoft）で強調する。
    // バッジの数字だけでなくボタン自体の色でも「絞り込み中」が一目で分かるようにするため。
    val active = activeCount > 0
    val containerColor = if (active) shogiColors.primarySoft else MaterialTheme.colorScheme.surface
    val contentColor = if (active) MaterialTheme.colorScheme.primary else shogiColors.ink2
    val borderColor = if (active) MaterialTheme.colorScheme.primary else shogiColors.line
    val shape = RoundedCornerShape(8.dp) // ボタンの角丸は8dp。チップの999dp（ピル型）とは意図的に別形状。

    Row(
        modifier = Modifier
            .testTag("filter_open_button")
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedBox(
            badge = {
                // 朱（loss）は損失専用色のため、絞り込み中バッジには使わない。
                // 「いま選択中」を表す紺青（primary）で統一する（選択チップと同じ意味付け）。
                if (active) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text("$activeCount")
                    }
                }
            },
        ) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = null,
                tint = contentColor,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            AppStrings.GAME_LIST_FILTER_BUTTON,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

/** 絞り込み条件のボトムシート。filterはドラフトで、onApply時だけ一覧へ反映する。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListFilterSheet(
    allGames: List<GameRecord>,
    filter: GameListFilter,
    onFilterChange: (GameListFilter) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                AppStrings.GAME_LIST_FILTER_SHEET_TITLE,
                style = MaterialTheme.typography.titleLarge,
            )
            GameListFilterBar(
                allGames = allGames,
                filter = filter,
                onFilterChange = onFilterChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_clear_button"),
                ) {
                    Text(AppStrings.GAME_LIST_FILTER_CLEAR)
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_apply_button"),
                ) {
                    Text(AppStrings.GAME_LIST_FILTER_APPLY)
                }
            }
        }
    }
}

/** 実在する値だけを軸別チップにする。期間の基準時刻は初回コンポジションで固定する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameListFilterBar(
    allGames: List<GameRecord>,
    filter: GameListFilter,
    onFilterChange: (GameListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = allGames.distinctSources()
    val openingStyles = allGames.distinctOpeningStyles()
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
        if (openingStyles.isNotEmpty()) {
            FilterAxisRow(AppStrings.GAME_LIST_FILTER_OPENING_STYLE) {
                openingStyles.forEach { openingStyle ->
                    FilterChipItem(
                        label = openingStyle,
                        selected = filter.openingStyle == openingStyle,
                        testTag = "filter_chip_opening_style_$openingStyle",
                        onClick = {
                            onFilterChange(
                                filter.copy(
                                    openingStyle = if (filter.openingStyle == openingStyle) null else openingStyle,
                                ),
                            )
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
    val shape = RoundedCornerShape(999.dp)

    Row(
        // clipを外側に置き、クリック時のリップルを丸チップの形状へ収める。
        modifier = Modifier
            .testTag(testTag)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
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
