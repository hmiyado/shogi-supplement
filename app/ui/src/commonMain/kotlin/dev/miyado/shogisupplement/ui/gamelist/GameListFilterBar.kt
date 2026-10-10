package dev.miyado.shogisupplement.ui.gamelist


import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import dev.miyado.shogisupplement.ui.generated.resources.*

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.Res
import dev.miyado.shogisupplement.ui.generated.resources.game_list_load_retry


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.GameListSummary
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameResultFilter
import dev.miyado.shogisupplement.db.SavedGameFilter
import dev.miyado.shogisupplement.db.TIME_CONTROL_OTHER
import dev.miyado.shogisupplement.db.availableTimeControls
import dev.miyado.shogisupplement.db.clearUnavailableTimeControl
import dev.miyado.shogisupplement.db.distinctOpeningStyles
import dev.miyado.shogisupplement.db.distinctSources
import dev.miyado.shogisupplement.db.distinctTimeControls
import dev.miyado.shogisupplement.db.hasResultData
import dev.miyado.shogisupplement.db.hasUserSideData
import dev.miyado.shogisupplement.db.filterGames
import dev.miyado.shogisupplement.db.summarize
import dev.miyado.shogisupplement.strength.StrengthEstimator
import dev.miyado.shogisupplement.strength.toDisplayString
import dev.miyado.shogisupplement.ui.common.exposeTestTags
import dev.miyado.shogisupplement.ui.common.withMonoNumbers
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.shogiColors
import dev.miyado.shogisupplement.util.currentEpochSeconds

private const val SECONDS_PER_DAY = 24L * 60 * 60

// 見出し・ボタン行・シート上下の余白がウィンドウに必ず残る配分。
private const val AXES_MAX_HEIGHT_FRACTION = 0.5f
private const val SAVED_FILTERS_MAX_HEIGHT_FRACTION = 0.3f

/** 棋譜一覧の絞り込みヘッダー。条件の詳細を常設せず、行高を固定してno-jitterを保つ。 */
@Composable
fun GameListFilterHeader(
    activeCount: Int,
    shownCount: Int,
    totalCount: Int,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier,
    summary: GameListSummary? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameListFilterButton(activeCount = activeCount, onClick = onOpenFilter)
            GameListCountText(shownCount = shownCount, totalCount = totalCount)
        }
        GameListSummaryRow(summary)
    }
}

/**
 * 絞り込んだ集合の成績。条件を変えるたびに出たり消えたりすると一覧の先頭がずれるため、
 * 出す内容が無くても行の高さは残す。
 */
@Composable
private fun GameListSummaryRow(summary: GameListSummary?) {
    val parts = summary?.let { s ->
        buildList {
            s.winRatePct?.let { add(UiStrings.gameListSummaryWinRate(it, s.wins, s.decidedGames)) }
            s.blunderRatePct?.let {
                add(UiStrings.gameListSummaryBlunderRate(it, s.blunders, s.userMoves))
            }
            if (s.ratings.isNotEmpty()) {
                val estimate = StrengthEstimator.aggregate(s.ratings, s.userMoves)
                add(UiStrings.gameListSummaryStrength(estimate.toDisplayString()))
            }
        }
    }.orEmpty()
    Box(
        modifier = Modifier.fillMaxWidth().height(SUMMARY_ROW_HEIGHT_DP.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(stringResource(Res.string.game_list_summary_separator)),
                style = TextStyleData,
                color = MaterialTheme.shogiColors.ink2,
                maxLines = 1,
            )
        }
    }
}

private const val SUMMARY_ROW_HEIGHT_DP = 24

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
                imageVector = vectorResource(Res.drawable.ic_filled_filter_list),
                contentDescription = null,
                tint = contentColor,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(Res.string.game_list_filter_button),
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
    facets: dev.miyado.shogisupplement.db.GameListFacets? = null,
    savedCounts: Map<String, Int>? = null,
    knownPositionLabels: List<String> = emptyList(),
    filter: GameListFilter,
    onFilterChange: (GameListFilter) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    savedFilters: List<SavedGameFilter> = emptyList(),
    onSelectSavedFilter: (SavedGameFilter) -> Unit = {},
    onRequestSave: (() -> Unit)? = null,
    onEditSavedFilter: (SavedGameFilter) -> Unit = {},
    onDeleteSavedFilter: (String) -> Unit = {},
) {
    val maxAxesHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * AXES_MAX_HEIGHT_FRACTION
    }
    val maxSavedFiltersHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * SAVED_FILTERS_MAX_HEIGHT_FRACTION
    }
    // 既定の半開きだと軸が5本を超えた時点でボタン行がシートの外に出る。全開で開く。
    ModalBottomSheet(
        modifier = Modifier.exposeTestTags(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SavedGameFilterSection(
                savedFilters = savedFilters,
                allGames = allGames,
                savedCounts = savedCounts,
                modifier = Modifier
                    .heightIn(max = maxSavedFiltersHeight)
                    .verticalScroll(rememberScrollState()),
                onSelect = onSelectSavedFilter,
                onEdit = onEditSavedFilter,
                onDelete = onDeleteSavedFilter,
            )
            Text(
                stringResource(Res.string.game_list_filter_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            GameListFilterBar(
                allGames = allGames,
                facets = facets,
                knownPositionLabels = knownPositionLabels,
                filter = filter,
                onFilterChange = onFilterChange,
                // 持ち時間はKIFヘッダの原文由来で項目数に上限が無い。ModalBottomSheetは
                // 中身を高さ無制限で測りweightで配分できないため、ウィンドウ高さから
                // 上限を出して条件側だけをスクロールさせる。
                modifier = Modifier
                    .heightIn(max = maxAxesHeight)
                    .verticalScroll(rememberScrollState()),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onRequestSave != null) {
                    TextButton(
                        onClick = onRequestSave,
                        modifier = Modifier.weight(1f).testTag("filter_save_button"),
                    ) {
                        Text(stringResource(Res.string.game_list_save_filter))
                    }
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                        .testTag("filter_clear_button"),
                ) {
                    Text(stringResource(Res.string.game_list_filter_clear))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                        .testTag("filter_apply_button"),
                ) {
                    Text(stringResource(Res.string.game_list_filter_apply))
                }
            }
        }
    }
}

@Composable
private fun SavedGameFilterSection(
    savedFilters: List<SavedGameFilter>,
    savedCounts: Map<String, Int>? = null,
    allGames: List<GameRecord>,
    modifier: Modifier = Modifier,
    onSelect: (SavedGameFilter) -> Unit,
    onEdit: (SavedGameFilter) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.game_list_saved_filters),
            style = MaterialTheme.typography.titleMedium,
        )
        if (savedFilters.isEmpty()) {
            Text(
                stringResource(Res.string.game_list_saved_filter_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.shogiColors.ink3,
            )
        } else {
            savedFilters.forEach { saved ->
                SavedGameFilterCard(
                    saved = saved,
                    gameCount = savedCounts?.get(saved.name) ?: allGames.filterGames(saved.currentFilter(currentEpochSeconds())).size,
                    onClick = { onSelect(saved) },
                    onEdit = { onEdit(saved) },
                    onDelete = { onDelete(saved.name) },
                )
            }
        }
    }
}

@Composable
private fun SavedGameFilterCard(
    saved: SavedGameFilter,
    gameCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.shogiColors.line),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(saved.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.saved_filter_period_count, UiStrings.gameListSavedFilterPeriod(saved.periodDays), gameCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.shogiColors.ink3,
                )
            }
            TextButton(onClick = onEdit) { Text(stringResource(Res.string.game_list_saved_filter_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.game_list_saved_filter_delete)) }
        }
    }
}

@Composable
internal fun SaveGameFilterDialog(
    initialName: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.game_list_save_filter_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.game_list_save_filter_name)) },
                placeholder = { Text(stringResource(Res.string.game_list_save_filter_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.trim().isNotEmpty(),
            ) { Text(stringResource(Res.string.game_list_save_filter_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SavedGameFilterComparisonSheet(
    saved: SavedGameFilter,
    games: List<GameRecord>,
    blunderCounts: Map<Long, Int>,
    queries: dev.miyado.shogisupplement.db.GameListReader? = null,
    labelOwner: String? = null,
    dataRevision: Any? = null,
    onDismiss: () -> Unit,
) {
    val now = remember(saved) { currentEpochSeconds() }
    val days = (saved.periodDays ?: SavedGameFilter.DEFAULT_PERIOD_DAYS).coerceAtLeast(1).toLong()
    val currentFilter = saved.conditionFilter().copy(dateFrom = now - days * SECONDS_PER_DAY)
    val previousFilter = saved.conditionFilter().copy(dateFrom = now - days * 2 * SECONDS_PER_DAY, dateTo = now - days * SECONDS_PER_DAY - 1)
    var summaries by remember(saved, labelOwner, dataRevision) { mutableStateOf<Pair<GameListSummary, GameListSummary>?>(null) }
    var loadFailed by remember(saved, labelOwner, dataRevision) { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(queries, saved, labelOwner, dataRevision, attempt) {
        loadFailed = false
        try {
            summaries = if (queries == null) games.filterGames(currentFilter).summarize(blunderCounts) to games.filterGames(previousFilter).summarize(blunderCounts)
            else kotlinx.coroutines.withContext(dev.miyado.shogisupplement.ui.common.defaultIoDispatcher) {
                queries.summary(labelOwner, currentFilter) to queries.summary(labelOwner, previousFilter)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { loadFailed = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(saved.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${stringResource(Res.string.game_list_filter_compare_title)}・${UiStrings.gameListSavedFilterPeriod(days.toInt())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.shogiColors.ink2,
            )
            val result = summaries
            if (loadFailed) TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.game_list_load_retry)) }
            else if (result == null) androidx.compose.material3.CircularProgressIndicator()
            else Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ComparisonPeriodCard(
                    label = stringResource(Res.string.game_list_filter_compare_current),
                    summary = result.first,
                    modifier = Modifier.weight(1f),
                )
                ComparisonPeriodCard(
                    label = stringResource(Res.string.game_list_filter_compare_previous),
                    summary = result.second,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.game_list_filter_compare_close))
            }
        }
    }
}

@Composable
private fun ComparisonPeriodCard(
    label: String,
    summary: GameListSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.shogiColors.line),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.shogiColors.ink2)
            Text(stringResource(Res.string.game_count, summary.games), style = MaterialTheme.typography.titleLarge)
            summary.winRatePct?.let {
                Text(UiStrings.gameListSummaryWinRate(it, summary.wins, summary.decidedGames), style = MaterialTheme.typography.bodySmall)
            }
            summary.blunderRatePct?.let {
                Text(UiStrings.gameListSummaryBlunderRate(it, summary.blunders, summary.userMoves), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 実在する値だけを軸別チップにする。期間の基準時刻は初回コンポジションで固定する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameListFilterBar(
    allGames: List<GameRecord>,
    facets: dev.miyado.shogisupplement.db.GameListFacets? = null,
    knownPositionLabels: List<String> = emptyList(),
    filter: GameListFilter,
    onFilterChange: (GameListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelPositionLabelTitle = stringResource(Res.string.position_label_title)

    val sources = facets?.sources ?: allGames.distinctSources()
    val openingStyles = facets?.openings ?: allGames.distinctOpeningStyles()
    val timeControls = facets?.timeControls(null) ?: allGames.distinctTimeControls()
    // 出典を選び直すと、その出典に無い持ち時間の指定が残りうる。どの軸を触っても
    // 実在しない組み合わせにならないよう、変更は必ずここを通す。
    val availableTimeControls = facets?.timeControls(filter.source) ?: allGames.availableTimeControls(filter.source)
    val onFilterChangeNormalized: (GameListFilter) -> Unit = {
        val available = facets?.timeControls(it.source) ?: allGames.availableTimeControls(it.source)
        onFilterChange(if (it.timeControl != null && it.timeControl !in available) it.copy(timeControl = null) else it)
    }
    val showSideAxis = facets?.hasSide ?: allGames.hasUserSideData()
    val showResultAxis = facets?.hasResult ?: allGames.hasResultData()
    val now = remember { currentEpochSeconds() }
    val dateFrom7d = now - 7 * SECONDS_PER_DAY
    val dateFrom30d = now - 30 * SECONDS_PER_DAY

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isNotEmpty()) {
            FilterAxisRow(stringResource(Res.string.game_list_filter_source)) {
                sources.forEach { source ->
                    FilterChipItem(
                        label = AnnotatedString(UiStrings.sourceFilterLabel(source)),
                        selected = filter.source == source,
                        testTag = "filter_chip_source_$source",
                        onClick = {
                            onFilterChangeNormalized(filter.copy(source = if (filter.source == source) null else source))
                        },
                    )
                }
            }
        }
        val matchingLabels = facets?.matchingLabels ?: allGames.flatMap { it.positionLabels }.toSet()
        val positionLabels = (facets?.knownLabels.orEmpty() + knownPositionLabels + matchingLabels).distinct().sorted()
        if (positionLabels.isNotEmpty()) {
            FilterAxisRow(labelPositionLabelTitle) {
                positionLabels.forEach { label ->
                    FilterChipItem(
                        label = AnnotatedString(label), selected = filter.positionLabel == label,
                        testTag = "filter_position_label_$label",
                        enabled = label in matchingLabels || filter.positionLabel == label,
                        onClick = { onFilterChange(filter.copy(positionLabel = label.takeUnless { filter.positionLabel == it })) },
                    )
                }
            }
        }
        if (timeControls.isNotEmpty()) {
            FilterAxisRow(stringResource(Res.string.game_list_filter_time_control)) {
                timeControls.forEach { timeControl ->
                    FilterChipItem(
                        label = if (timeControl == TIME_CONTROL_OTHER) {
                            AnnotatedString(stringResource(Res.string.game_list_filter_other))
                        } else {
                            withMonoNumbers(timeControl)
                        },
                        selected = filter.timeControl == timeControl,
                        testTag = "filter_chip_time_control_$timeControl",
                        enabled = timeControl in availableTimeControls,
                        onClick = {
                            onFilterChangeNormalized(
                                filter.copy(
                                    timeControl = if (filter.timeControl == timeControl) null else timeControl,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (openingStyles.isNotEmpty()) {
            FilterAxisRow(stringResource(Res.string.game_list_filter_opening_style)) {
                openingStyles.forEach { openingStyle ->
                    FilterChipItem(
                        label = AnnotatedString(openingStyle),
                        selected = filter.openingStyle == openingStyle,
                        testTag = "filter_chip_opening_style_$openingStyle",
                        onClick = {
                            onFilterChangeNormalized(
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
            FilterAxisRow(stringResource(Res.string.game_list_filter_side)) {
                listOf("sente" to stringResource(Res.string.player_side_sente), "gote" to stringResource(Res.string.player_side_gote))
                    .forEach { (value, label) ->
                        FilterChipItem(
                            label = AnnotatedString(label),
                            selected = filter.userSide == value,
                            testTag = "filter_chip_side_$value",
                            onClick = {
                                onFilterChangeNormalized(
                                    filter.copy(userSide = if (filter.userSide == value) null else value),
                                )
                            },
                        )
                    }
            }
        }
        if (showResultAxis) {
            FilterAxisRow(stringResource(Res.string.game_list_filter_result)) {
                listOf(
                    GameResultFilter.WIN to stringResource(Res.string.game_result_win),
                    GameResultFilter.LOSS to stringResource(Res.string.game_result_loss),
                ).forEach { (value, label) ->
                    FilterChipItem(
                        label = AnnotatedString(label),
                        selected = filter.result == value,
                        testTag = "filter_chip_result_${value.name}",
                        onClick = {
                            onFilterChangeNormalized(filter.copy(result = if (filter.result == value) null else value))
                        },
                    )
                }
            }
        }
        FilterAxisRow(stringResource(Res.string.game_list_filter_period)) {
            listOf(
                Triple(dateFrom7d, stringResource(Res.string.game_list_filter_period_7d), "7d"),
                Triple(dateFrom30d, stringResource(Res.string.game_list_filter_period_30d), "30d"),
            ).forEach { (candidateDateFrom, label, tagSuffix) ->
                val selected = filter.dateFrom == candidateDateFrom
                FilterChipItem(
                    label = AnnotatedString(label),
                    selected = selected,
                    testTag = "filter_chip_period_$tagSuffix",
                    onClick = {
                        onFilterChangeNormalized(filter.copy(dateFrom = if (selected) null else candidateDateFrom))
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
    label: AnnotatedString,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shogiColors = MaterialTheme.shogiColors
    val containerColor = if (selected) shogiColors.primarySoft else MaterialTheme.colorScheme.surface
    val contentColor = when {
        !enabled -> shogiColors.ink3
        selected -> MaterialTheme.colorScheme.primary
        else -> shogiColors.ink2
    }
    val borderColor = if (selected && enabled) MaterialTheme.colorScheme.primary else shogiColors.line
    val shape = RoundedCornerShape(999.dp)

    Row(
        // clipを外側に置き、クリック時のリップルを丸チップの形状へ収める。
        modifier = Modifier
            .testTag(testTag)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
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
        UiStrings.gameListTotalCount(totalCount)
    } else {
        UiStrings.gameListFilteredCount(shownCount, totalCount)
    }
    Text(text, style = TextStyleData)
}
