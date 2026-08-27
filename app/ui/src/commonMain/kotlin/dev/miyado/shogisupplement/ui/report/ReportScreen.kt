package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.boardMaxHeight
import dev.miyado.shogisupplement.ui.common.DeleteGameConfirmDialog
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.ReportBackHandler
import dev.miyado.shogisupplement.ui.common.SfenPosition
import dev.miyado.shogisupplement.ui.theme.shogiColors
import dev.miyado.shogisupplement.upload.DeleteGameOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 盤と棋譜操作を備えたレポート画面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    game: GameRecord,
    reports: List<BlunderRecord>,
    flip: Boolean = false,
    strengthDisplayText: String? = null,
    evalDisplay: String = "cp",
    positionEvals: List<PositionEvalRow> = emptyList(),
    matchRateDisplayText: String? = null,
    blunderRateDisplayText: String? = null,
    analysisPending: Boolean = false,
    onAnalyze: () -> Unit = {},
    canDelete: Boolean = true,
    onBack: () -> Unit,
    pvExtState: Map<Long, PvExtState> = emptyMap(),
    pvExtensionEnabled: Boolean = true,
    onExtendBestPv: (blunderId: Long, sfenAtLineEnd: String, currentPvStr: String?) -> Unit = { _, _, _ -> },
    studyState: StudyState? = null,
    onStartStudy: (
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare?,
        tappedHandPieceType: PieceType?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onStudySquareTapped: (ShogiSquare) -> Unit = {},
    onStudyHandPieceTapped: (PieceType) -> Unit = {},
    onStudyPromoteDecision: (Boolean) -> Unit = {},
    onStudyStepBack: () -> Unit = {},
    onStudyResetToStart: () -> Unit = {},
    onStudyEnd: () -> Unit = {},
    onStudyChipTapped: (Int) -> Unit = {},
    onStudyBranchChipTapped: (Int) -> Unit = {},
    onStudyBranchPopupDismiss: () -> Unit = {},
    onStudyBranchOptionSelected: (depth: Int, moveUsi: String) -> Unit = { _, _ -> },
    onStudyAnalyze: () -> Unit = {},
    onCopyKif: (String) -> Unit = {},
    onDeleteGame: (
        deleteServer: Boolean,
        onResult: (DeleteGameOutcome) -> Unit,
    ) -> Unit = { _, onResult -> onResult(DeleteGameOutcome.Success) },
    initialSelectedIndex: Int? = null,
    initialViewerModeBestPv: Boolean = false,
    initialPlyIndex: Int = 0,
    initialShowGameInfoDialog: Boolean = false,
    initialBodyModeList: Boolean = false,
    justCompleted: Boolean = false,
) {
    var viewerMode by remember {
        mutableStateOf(if (initialViewerModeBestPv) ViewerMode.BEST_PV else ViewerMode.MAINLINE)
    }
    var plyIndex by remember { mutableIntStateOf(initialPlyIndex) }
    var selectedIdx by remember { mutableStateOf(initialSelectedIndex) }
    var bodyMode by remember {
        mutableStateOf(
            if (initialBodyModeList || initialSelectedIndex != null) ReportBodyMode.LIST else ReportBodyMode.SUMMARY,
        )
    }
    var showMoveList by remember { mutableStateOf(false) }
    var pendingExtendAdvance by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 完了バナーはこの画面内で一度だけ消費する。
    var showCompletionBanner by remember { mutableStateOf(justCompleted) }
    LaunchedEffect(justCompleted) {
        if (justCompleted) {
            delay(2500)
            showCompletionBanner = false
        }
    }

    val selectedBlunder = selectedIdx?.let { reports.getOrNull(it) }
    val hasBestPv = selectedBlunder?.bestPv != null

    val evalGraphPoints = remember(positionEvals, game.userSide) {
        buildEvalGraphPoints(positionEvals, userIsGote = game.userSide == "gote")
    }
    val blunderPlies = remember(reports) { reports.map { it.ply.toInt() }.toSet() }

    val exitStudy: () -> Unit = exit@{
        val s = studyState ?: return@exit
        onStudyEnd()
        viewerMode = if (s.originIsBestPv) ViewerMode.BEST_PV else ViewerMode.MAINLINE
        plyIndex = s.originPlyIndex
        selectedIdx = s.originSelectedIdx
    }
    ReportBackHandler(enabled = studyState != null) { exitStudy() }

    val (movesInMode, startSfen) = remember(viewerMode, selectedBlunder) {
        when (viewerMode) {
            ViewerMode.MAINLINE -> game.movesUsi to null
            ViewerMode.BEST_PV -> {
                val pv = selectedBlunder?.bestPv
                    ?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                pv to selectedBlunder?.sfenBefore
            }
        }
    }

    val maxPly = movesInMode.size
    val clampedPly = plyIndex.coerceIn(0, maxPly)

    val currentSfen = remember(viewerMode, clampedPly, selectedBlunder) {
        computeSfenAtStep(startSfen, movesInMode, clampedPly)
    }

    val prevSfen = remember(viewerMode, clampedPly, selectedBlunder) {
        if (clampedPly == 0) null
        else computeSfenAtStep(startSfen, movesInMode, clampedPly - 1)
    }

    val lastMoveDest = remember(viewerMode, clampedPly, selectedBlunder) {
        if (clampedPly <= 0) null
        else {
            movesInMode.getOrNull(clampedPly - 1)?.let { usiStr ->
                runCatching {
                    val move = ShogiMove.fromUsi(usiStr)
                    move.to.file to move.to.rank
                }.getOrNull()
            }
        }
    }

    val senteSuffix = if (game.userSide == "sente") AppStrings.PLAYER_YOU else ""
    val goteSuffix = if (game.userSide == "gote") AppStrings.PLAYER_YOU else ""
    val senteName = game.senteName ?: AppStrings.PLAYER_UNKNOWN
    val goteName = game.goteName ?: AppStrings.PLAYER_UNKNOWN
    val playersLine = "▲$senteName$senteSuffix　△$goteName$goteSuffix"
    var showGameInfoDialog by remember { mutableStateOf(initialShowGameInfoDialog) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val navInfo = rememberReportNavInfo(
        viewerMode = viewerMode,
        clampedPly = clampedPly,
        maxPly = maxPly,
        movesInMode = movesInMode,
        prevSfen = prevSfen,
        reports = reports,
        selectedBlunder = selectedBlunder,
        positionEvals = positionEvals,
        evalDisplay = evalDisplay,
        game = game,
        pvExtState = pvExtState,
        pvExtensionEnabled = pvExtensionEnabled,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        // シートは盤を隠し切らない高さに制限する。
        MoveListBottomSheet(
            show = showMoveList,
            onDismiss = { showMoveList = false },
            sheetMaxHeight = screenHeight * 0.55f - 56.dp,
            moves = game.movesUsi,
            currentPly = plyIndex.coerceIn(0, game.movesUsi.size),
            positionEvals = positionEvals,
            evalDisplay = evalDisplay,
            userIsGote = game.userSide == "gote",
            onSelectPly = { ply ->
                viewerMode = ViewerMode.MAINLINE
                plyIndex = ply
                showMoveList = false
            },
        )

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {

                ReportTopBar(
                    title = AppStrings.sourcePlaceLabel(game.sourcePlace) ?: game.fileName,
                    onBack = onBack,
                    onInfoClick = { showGameInfoDialog = true },
                    kifText = game.kifText,
                    onCopyKifClick = {
                        val kifText = game.kifText
                        if (kifText != null) {
                            onCopyKif(kifText)
                            scope.launch {
                                snackbarHostState.showSnackbar(AppStrings.KIF_COPIED_MESSAGE)
                            }
                        }
                    },
                    onDeleteClick = if (canDelete) { { showDeleteDialog = true } } else null,
                )

                val studyCurrentSfen = remember(studyState) {
                    studyState?.let { computeSfenAtStep(it.baseSfen, it.moves, it.moves.size) }
                }
                val studyLastMoveDest = remember(studyState) {
                    val s = studyState ?: return@remember null
                    if (s.moves.isEmpty()) null
                    else s.moves.lastOrNull()?.let { usiStr ->
                        runCatching { ShogiMove.fromUsi(usiStr).to.file to ShogiMove.fromUsi(usiStr).to.rank }
                            .getOrNull()
                    }
                }

                ReportBoardArea(
                    studyState = studyState,
                    currentSfen = currentSfen,
                    studyCurrentSfen = studyCurrentSfen,
                    flip = flip,
                    lastMoveDest = lastMoveDest,
                    studyLastMoveDest = studyLastMoveDest,
                    clampedPly = clampedPly,
                    maxPly = maxPly,
                    viewerMode = viewerMode,
                    selectedIdx = selectedIdx,
                    studyOriginAbsolutePly = navInfo.studyOriginAbsolutePly,
                    studyOrigin = navInfo.studyOrigin,
                    onStartStudy = onStartStudy,
                    onStudySquareTapped = onStudySquareTapped,
                    onStudyHandPieceTapped = onStudyHandPieceTapped,
                    onPlyIndexChange = { plyIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = boardMaxHeight()),
                )

                StudyPromoteDialog(
                    show = studyState?.showPromoteDialog == true,
                    onDecision = onStudyPromoteDecision,
                )

                val studySenteToMove = remember(studyCurrentSfen, currentSfen) {
                    SfenPosition.parse(studyCurrentSfen ?: currentSfen).isBlackTurn
                }

                // 排他的な固定高スロットで検討モード切替時のY座標を保つ。
                if (showCompletionBanner) {
                    ReportNavBannerRow(
                        text = AppStrings.ANALYSIS_COMPLETED_BANNER,
                        textColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.shogiColors.primarySoft,
                    )
                } else {
                    ReportNavRow(
                        studyState = studyState,
                        studySenteToMove = studySenteToMove,
                        onStudyStepBack = onStudyStepBack,
                        onStudyExit = exitStudy,
                        navLabelAnnotated = navInfo.navLabelAnnotated,
                        onLabelClick = { showMoveList = true },
                        canGoFirst = clampedPly > 0,
                        onFirst = { plyIndex = 0 },
                        canGoPrev = clampedPly > 0,
                        onPrev = { plyIndex = (clampedPly - 1).coerceAtLeast(0) },
                        canGoNext = clampedPly < maxPly || navInfo.canTriggerExtend,
                        onNext = {
                            if (clampedPly < maxPly) {
                                plyIndex = clampedPly + 1
                            } else if (navInfo.canTriggerExtend) {
                                selectedBlunder?.let { blunder ->
                                    val sfenAtEnd = computeSfenAtStep(
                                        blunder.sfenBefore,
                                        movesInMode,
                                        movesInMode.size,
                                    )
                                    pendingExtendAdvance = true
                                    onExtendBestPv(blunder.id, sfenAtEnd, blunder.bestPv)
                                }
                            }
                        },
                        showExtendIndicator = navInfo.showExtendIndicator,
                        canTriggerExtend = navInfo.canTriggerExtend,
                        canGoLast = clampedPly < maxPly,
                        onLast = { plyIndex = maxPly },
                    )
                }

                if (studyState == null) {
                    LaunchedEffect(maxPly) {
                        if (pendingExtendAdvance) {
                            plyIndex = clampedPly + 1
                            pendingExtendAdvance = false
                        }
                    }
                    LaunchedEffect(selectedBlunder?.let { pvExtState[it.id] }) {
                        if (pendingExtendAdvance &&
                            selectedBlunder != null &&
                            pvExtState[selectedBlunder.id] is PvExtState.Error
                        ) {
                            pendingExtendAdvance = false
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.shogiColors.line,
                    modifier = Modifier.testTag("report_divider"),
                )

                val noBlundersMessage = when {
                    game.userSide != null && game.gameWinner != null ->
                        if (game.gameWinner == game.userSide) {
                            AppStrings.NO_BLUNDERS_WIN
                        } else {
                            AppStrings.noBlundersLoss(game.endReason ?: "負け")
                        }
                    else -> AppStrings.NO_BLUNDERS_UNKNOWN
                }

                val selectBlunderAndShowList: (Int) -> Unit = { idx ->
                    if (studyState == null) {
                        selectedIdx = idx
                        viewerMode = ViewerMode.MAINLINE
                        plyIndex = (reports[idx].ply - 1).toInt()
                        bodyMode = ReportBodyMode.LIST
                    }
                }

                if (studyState != null) {
                    StudyPanel(
                        studyState = studyState,
                        onChipTapped = onStudyChipTapped,
                        onBranchChipTapped = onStudyBranchChipTapped,
                        onBranchPopupDismiss = onStudyBranchPopupDismiss,
                        onBranchOptionSelected = onStudyBranchOptionSelected,
                        onAnalyze = onStudyAnalyze,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    when (bodyMode) {
                        ReportBodyMode.SUMMARY -> {
                            ReportSummaryBody(
                                evalGraphPoints = evalGraphPoints,
                                maxPly = game.movesUsi.size,
                                blunderPlies = blunderPlies,
                                currentPly = clampedPly,
                                onPlyTapped = { ply ->
                                    viewerMode = ViewerMode.MAINLINE
                                    plyIndex = ply
                                    val idx = reports.indexOfFirst { it.ply.toInt() == ply }
                                    if (idx >= 0) {
                                        selectedIdx = idx
                                        bodyMode = ReportBodyMode.LIST
                                    }
                                },
                                onPlyDragged = { ply ->
                                    viewerMode = ViewerMode.MAINLINE
                                    plyIndex = ply
                                },
                                reports = reports,
                                noBlundersMessage = noBlundersMessage,
                                strengthDisplayText = strengthDisplayText,
                                matchRateDisplayText = matchRateDisplayText,
                                blunderRateDisplayText = blunderRateDisplayText,
                                onViewList = { bodyMode = ReportBodyMode.LIST },
                                analysisPending = analysisPending,
                                onAnalyze = onAnalyze,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        ReportBodyMode.LIST -> {
                            ReportBlunderListBody(
                                onBackToSummary = {
                                    bodyMode = ReportBodyMode.SUMMARY
                                    viewerMode = ViewerMode.MAINLINE
                                },
                                viewerMode = viewerMode,
                                hasBestPv = hasBestPv,
                                onSelectMainlineTab = {
                                    viewerMode = ViewerMode.MAINLINE
                                    plyIndex = clampedPly.coerceAtMost(game.movesUsi.size)
                                },
                                onSelectBestPvTab = {
                                    viewerMode = ViewerMode.BEST_PV
                                    plyIndex = 0
                                },
                                reports = reports,
                                noBlundersMessage = noBlundersMessage,
                                selectedIdx = selectedIdx,
                                evalDisplay = evalDisplay,
                                onSelectBlunder = selectBlunderAndShowList,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } // else (studyState == null)
            } // Column
        } // Scaffold content lambda

        GameInfoDialog(
            show = showGameInfoDialog,
            onDismiss = { showGameInfoDialog = false },
            game = game,
            playersLine = playersLine,
        )
        DeleteGameConfirmDialog(
            show = showDeleteDialog,
            canDeleteServer = game.uploadedAt != null,
            onConfirm = { deleteServer, onResult ->
                onDeleteGame(deleteServer, onResult)
            },
            onDismiss = { showDeleteDialog = false },
        )
    } // BoxWithConstraints
} // ReportScreen

internal fun computeSfenAtStep(startSfen: String?, moves: List<String>, steps: Int): String {
    val board = if (startSfen != null) {
        runCatching { ShogiBoard.fromSfen(startSfen) }.getOrElse { ShogiBoard() }
    } else {
        ShogiBoard()
    }
    val limit = steps.coerceAtMost(moves.size)
    for (i in 0 until limit) {
        runCatching { board.push(ShogiMove.fromUsi(moves[i])) }.onFailure { break }
    }
    return board.toSfen()
}
