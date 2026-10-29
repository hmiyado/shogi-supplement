package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.ui.report.SharedPositionErrorScreen
import dev.miyado.shogisupplement.ui.report.SharedPositionScreen
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme

interface SharedPositionStudyActions {
    fun onStudySquareTapped(sq: ShogiSquare)
    fun onStudyHandPieceTapped(pieceType: PieceType)
    fun onStudyStepBack()
    fun onStudyChipTapped(depth: Int)
    fun onStudyBranchChipTapped(depth: Int)
    fun onStudyBranchPopupDismiss()
    fun onStudyBranchOptionSelected(depth: Int, nodeId: Long)
    fun onStudyAnalyze()
    fun onStudyCandidateSelected(moveUsi: String)
    fun onStudyPromoteDecision(promote: Boolean)
    fun hasUnsavedVariation(): Boolean
    fun confirmLeave()
}

@Composable
fun SharedPositionApp(
    studyState: StudyState?,
    actions: SharedPositionStudyActions,
    onBack: () -> Unit,
    titleOverride: String? = null,
    playersLine: String? = null,
    gameMoves: List<String>? = null,
    onSelectGamePly: ((Int) -> Unit)? = null,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (actions.hasUnsavedVariation()) showDiscardDialog = true else onBack()
    }
    ShogiTheme {
        WebAppSurface {
            val state = studyState
            if (showDiscardDialog) {
                AlertDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = { Text(stringResource(Res.string.study_discard_title)) },
                    text = { Text(stringResource(Res.string.study_discard_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDiscardDialog = false
                                actions.confirmLeave()
                                onBack()
                            },
                        ) {
                            Text(
                                stringResource(Res.string.manual_kifu_discard),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardDialog = false }) {
                            Text(stringResource(Res.string.cancel))
                        }
                    },
                )
            }
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SharedPositionScreen(
                    studyState = state,
                    onBack = requestBack,
                    titleOverride = titleOverride,
                    playersLine = playersLine,
                    gameMoves = gameMoves,
                    onSelectGamePly = onSelectGamePly,
                    onStudySquareTapped = actions::onStudySquareTapped,
                    onStudyHandPieceTapped = actions::onStudyHandPieceTapped,
                    onStudyStepBack = actions::onStudyStepBack,
                    onStudyStepForward = { actions.onStudyChipTapped(state.moves.size + 1) },
                    onStudyChipTapped = actions::onStudyChipTapped,
                    onStudyBranchChipTapped = actions::onStudyBranchChipTapped,
                    onStudyBranchPopupDismiss = actions::onStudyBranchPopupDismiss,
                    onStudyBranchOptionSelected = actions::onStudyBranchOptionSelected,
                    onStudyAnalyze = actions::onStudyAnalyze,
                    onStudyCandidateSelected = actions::onStudyCandidateSelected,
                    onStudyPromoteDecision = actions::onStudyPromoteDecision,
                )
            }
        }
    }
}

@Composable
fun SharedPositionErrorApp(onBack: () -> Unit) {
    ShogiTheme {
        WebAppSurface {
            SharedPositionErrorScreen(onBack = onBack)
        }
    }
}
