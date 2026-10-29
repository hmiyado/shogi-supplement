package dev.miyado.shogisupplement.ui.report

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.ui.common.SfenPosition
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.common.TwoPaneBoardHeightFraction
import dev.miyado.shogisupplement.ui.common.WindowWidthClass
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.boardMaxHeight
import dev.miyado.shogisupplement.ui.common.rememberWindowWidthClass
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
fun SharedPositionScreen(
    studyState: StudyState,
    onBack: () -> Unit,
    titleOverride: String? = null,
    playersLine: String? = null,
    gameMoves: List<String>? = null,
    onSelectGamePly: ((Int) -> Unit)? = null,
    onStudySquareTapped: (ShogiSquare) -> Unit,
    onStudyHandPieceTapped: (PieceType) -> Unit,
    onStudyStepBack: () -> Unit,
    onStudyStepForward: () -> Unit,
    onStudyChipTapped: (Int) -> Unit,
    onStudyBranchChipTapped: (Int) -> Unit,
    onStudyBranchPopupDismiss: () -> Unit,
    onStudyBranchOptionSelected: (depth: Int, nodeId: Long) -> Unit,
    onStudyAnalyze: () -> Unit,
    onStudyCandidateSelected: (String) -> Unit,
    onStudyPromoteDecision: (Boolean) -> Unit,
) {
    val title = titleOverride ?: stringResource(Res.string.shared_position_title)
    val currentSfen = remember(studyState.baseSfen, studyState.moves) {
        computeSfenAtStep(studyState.baseSfen, studyState.moves, studyState.moves.size)
    }
    val lastMoveDest = remember(studyState.moves) {
        studyState.moves.lastOrNull()?.let { move ->
            runCatching { ShogiMove.fromUsi(move).to.file to ShogiMove.fromUsi(move).to.rank }.getOrNull()
        }
    }
    val senteToMove = remember(currentSfen) { SfenPosition.parse(currentSfen).isBlackTurn }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = rememberWindowWidthClass(maxWidth) == WindowWidthClass.EXPANDED
        val boardModifier = if (twoPane) {
            Modifier.fillMaxWidth().heightIn(max = maxHeight * TwoPaneBoardHeightFraction)
        } else {
            Modifier.adaptiveContentWidth().heightIn(max = boardMaxHeight())
        }

        Column(Modifier.fillMaxSize()) {
            ShogiThinTopBar(title = title, onBack = onBack)
            if (playersLine != null) {
                Text(
                    playersLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.shogiColors.ink2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        SharedPositionBoard(
                            studyState = studyState,
                            currentSfen = currentSfen,
                            lastMoveDest = lastMoveDest,
                            onStudySquareTapped = onStudySquareTapped,
                            onStudyHandPieceTapped = onStudyHandPieceTapped,
                            modifier = boardModifier,
                        )
                    }
                    VerticalDivider(
                        color = MaterialTheme.shogiColors.line,
                        modifier = Modifier.testTag("shared_position_divider"),
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        SharedPositionStudyPane(
                            studyState = studyState,
                            senteToMove = senteToMove,
                            gameMoves = gameMoves,
                            onSelectGamePly = onSelectGamePly,
                            onStudyStepBack = onStudyStepBack,
                            onStudyStepForward = onStudyStepForward,
                            onStudyChipTapped = onStudyChipTapped,
                            onStudyBranchChipTapped = onStudyBranchChipTapped,
                            onStudyBranchPopupDismiss = onStudyBranchPopupDismiss,
                            onStudyBranchOptionSelected = onStudyBranchOptionSelected,
                            onStudyAnalyze = onStudyAnalyze,
                            onStudyCandidateSelected = onStudyCandidateSelected,
                        )
                    }
                }
            } else {
                SharedPositionBoard(
                    studyState = studyState,
                    currentSfen = currentSfen,
                    lastMoveDest = lastMoveDest,
                    onStudySquareTapped = onStudySquareTapped,
                    onStudyHandPieceTapped = onStudyHandPieceTapped,
                    modifier = boardModifier,
                )
                HorizontalDivider(
                    color = MaterialTheme.shogiColors.line,
                    modifier = Modifier.testTag("shared_position_divider"),
                )
                SharedPositionStudyPane(
                    studyState = studyState,
                    senteToMove = senteToMove,
                    gameMoves = gameMoves,
                    onSelectGamePly = onSelectGamePly,
                    onStudyStepBack = onStudyStepBack,
                    onStudyStepForward = onStudyStepForward,
                    onStudyChipTapped = onStudyChipTapped,
                    onStudyBranchChipTapped = onStudyBranchChipTapped,
                    onStudyBranchPopupDismiss = onStudyBranchPopupDismiss,
                    onStudyBranchOptionSelected = onStudyBranchOptionSelected,
                    onStudyAnalyze = onStudyAnalyze,
                    onStudyCandidateSelected = onStudyCandidateSelected,
                )
            }
        }
        StudyPromoteDialog(
            show = studyState.showPromoteDialog,
            onDecision = onStudyPromoteDecision,
        )
    }
}

@Composable
fun SharedPositionErrorScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ShogiThinTopBar(title = stringResource(Res.string.shared_position_title), onBack = onBack)
        Box(Modifier.fillMaxSize().adaptiveContentWidth().padding(16.dp)) {
            Text(
                stringResource(Res.string.shared_position_invalid_url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.shogiColors.ink2,
            )
        }
    }
}

@Composable
private fun SharedPositionBoard(
    studyState: StudyState,
    currentSfen: String,
    lastMoveDest: Pair<Int, Int>?,
    onStudySquareTapped: (ShogiSquare) -> Unit,
    onStudyHandPieceTapped: (PieceType) -> Unit,
    modifier: Modifier,
) {
    ShogiBoardView(
        sfen = currentSfen,
        flip = studyState.flip,
        lastMoveDest = lastMoveDest,
        selectedFrom = studyState.selectedFrom,
        selectedDropType = studyState.selectedDropType,
        legalDestinations = studyState.legalDestinations,
        onSquareTapped = onStudySquareTapped,
        onHandPieceTapped = onStudyHandPieceTapped,
        modifier = modifier,
    )
}

@Composable
private fun SharedPositionStudyPane(
    studyState: StudyState,
    senteToMove: Boolean,
    gameMoves: List<String>?,
    onSelectGamePly: ((Int) -> Unit)?,
    onStudyStepBack: () -> Unit,
    onStudyStepForward: () -> Unit,
    onStudyChipTapped: (Int) -> Unit,
    onStudyBranchChipTapped: (Int) -> Unit,
    onStudyBranchPopupDismiss: () -> Unit,
    onStudyBranchOptionSelected: (depth: Int, nodeId: Long) -> Unit,
    onStudyAnalyze: () -> Unit,
    onStudyCandidateSelected: (String) -> Unit,
) {
    var showMoves by remember { mutableStateOf(false) }
    if (gameMoves != null && onSelectGamePly != null) {
        val currentPly = remember(studyState.moves, gameMoves) {
            var count = 0
            while (count < studyState.moves.size && count < gameMoves.size &&
                studyState.moves[count] == gameMoves[count]
            ) {
                count++
            }
            count
        }
        MoveListBottomSheet(
            show = showMoves,
            onDismiss = { showMoves = false },
            sheetMaxHeight = 560.dp,
            moves = gameMoves,
            currentPly = if (studyState.moves.size == currentPly) currentPly else -1,
            positionEvals = emptyList(),
            evalDisplay = "cp",
            userIsGote = false,
            initialSfen = studyState.baseSfen,
            includeInitial = true,
            onSelectPly = { showMoves = false; onSelectGamePly(it) },
        )
    }
    StudyNavRow(
        studyState = studyState,
        studySenteToMove = senteToMove,
        onStudyStepBack = onStudyStepBack,
        onStudyStepForward = onStudyStepForward,
        onStudyExit = null,
        onSelectMove = if (gameMoves != null && onSelectGamePly != null) ({ showMoves = true }) else null,
    )
    Box(Modifier.fillMaxSize()) {
        StudyPanel(
            studyState = studyState,
            onChipTapped = onStudyChipTapped,
            onBranchChipTapped = onStudyBranchChipTapped,
            onBranchPopupDismiss = onStudyBranchPopupDismiss,
            onBranchOptionSelected = onStudyBranchOptionSelected,
            onAnalyze = onStudyAnalyze,
            onCandidateTapped = onStudyCandidateSelected,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
