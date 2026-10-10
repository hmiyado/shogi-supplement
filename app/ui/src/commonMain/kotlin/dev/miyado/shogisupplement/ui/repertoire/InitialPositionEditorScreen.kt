package dev.miyado.shogisupplement.ui.repertoire

import org.jetbrains.compose.resources.stringResource

import org.jetbrains.compose.resources.vectorResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.EditorPieceLocation
import dev.miyado.shogisupplement.board.InitialPositionEditor
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.ui.common.*
import dev.miyado.shogisupplement.ui.common.BoardEditorActions
import dev.miyado.shogisupplement.ui.common.ReportBackHandler
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.common.ShogiSecondaryButton
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialPositionEditorScreen(
    initialSfen: String,
    hasMoves: Boolean,
    onClose: () -> Unit,
    onApply: (String) -> Unit,
    initialFlip: Boolean = false,
) {
    val labelInitialPositionChangeTitle = stringResource(Res.string.initial_position_change_title)
    val labelInitialPositionDiscardMoves = stringResource(Res.string.initial_position_discard_moves)
    val labelInitialPositionStart = stringResource(Res.string.initial_position_start)
    val labelKentoCancelButton = stringResource(Res.string.kento_cancel_button)
    val labelInitialPositionCreate = stringResource(Res.string.initial_position_create)
    val labelBoardFlip = stringResource(Res.string.board_flip)
    val labelBack = stringResource(Res.string.back)
    val labelBoardTurn = stringResource(Res.string.board_turn)
    val labelTextSideSente2 = stringResource(Res.string.text_side_sente_2)
    val labelTextSideGote2 = stringResource(Res.string.text_side_gote_2)
    val labelPieceBox = stringResource(Res.string.piece_box)
    val labelInitialPositionMissingKings = stringResource(Res.string.initial_position_missing_kings)
    val labelInitialPositionReset = stringResource(Res.string.initial_position_reset)

    var editor by rememberSaveable(initialSfen, stateSaver = Saver(
        save = { it.toSfen() },
        restore = { InitialPositionEditor.fromSfen(it) },
    )) { mutableStateOf(InitialPositionEditor.fromSfen(initialSfen)) }
    var flip by rememberSaveable { mutableStateOf(initialFlip) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ReportBackHandler(onBack = onClose)
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(labelInitialPositionChangeTitle) },
            text = { Text(labelInitialPositionDiscardMoves) },
            confirmButton = { TextButton(onClick = { onApply(editor.toSfen()) }) { Text(labelInitialPositionStart) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(labelKentoCancelButton) } },
        )
    }
    EditorDragHost(onDrop = { source, target ->
        val selected = when (source) {
            is EditorPieceLocation.Board -> editor.selectBoard(source.square)
            is EditorPieceLocation.Hand -> editor.selectHand(source.side, source.type)
            is EditorPieceLocation.Box -> editor.selectBox(source.type)
        }
        editor = when (target) {
            is EditorDropTarget.Square -> selected.moveToSquare(target.square)
            is EditorDropTarget.Hand -> selected.moveToHand(target.side)
            EditorDropTarget.Box -> selected.moveToBox()
        }
    }, pieceLabel = { source ->
        val type = when (source) {
            is EditorPieceLocation.Board -> editor.pieces[source.square]?.type
            is EditorPieceLocation.Hand -> source.type
            is EditorPieceLocation.Box -> source.type
        }
        type?.editorName().orEmpty()
    }) {
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(labelInitialPositionCreate) },
                actions = { TextButton(onClick = { flip = !flip }) { Text(labelBoardFlip) } },
                navigationIcon = { IconButton(onClick = onClose) { Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back), labelBack) } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShogiSecondaryButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(labelKentoCancelButton) }
                Button(onClick = {
                    if (hasMoves && editor.toSfen() != InitialPositionEditor.fromSfen(initialSfen).toSfen()) confirmClear = true
                    else onApply(editor.toSfen())
                }, enabled = editor.hasBothKings, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text(labelInitialPositionStart) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).adaptiveContentWidth().verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(labelBoardTurn, style = MaterialTheme.typography.bodySmall)
                for (side in Side.entries) {
                    FilterChip(selected = editor.turn == side, onClick = { editor = editor.withTurn(side) },
                        label = { Text(if (side == Side.BLACK) labelTextSideSente2 else labelTextSideGote2) })
                }
            }
            val selectedHand = editor.selected as? EditorPieceLocation.Hand
            ShogiBoardView(
                sfen = editor.toSfen(),
                flip = flip,
                selectedFrom = (editor.selected as? EditorPieceLocation.Board)?.square,
                selectedDropType = selectedHand?.type,
                onSquareTapped = { editor = editor.tapSquare(it) },
                editorActions = BoardEditorActions(
                    selectedHandSide = selectedHand?.side,
                    onHandSelected = { side, type ->
                        editor = if (editor.selected != null && selectedHand?.side != side) editor.moveToHand(side)
                        else editor.selectHand(side, type)
                    },
                    onHandRegionTapped = { editor = editor.moveToHand(it) },
                ),
            )
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("editor_piece_box").editorDropTarget(EditorDropTarget.Box).padding(horizontal = 16.dp).clickable { editor = editor.moveToBox() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(labelPieceBox, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((type, count) in editor.pieceBox) {
                        FilterChip(
                            modifier = Modifier.editorDragSource(EditorPieceLocation.Box(type)),
                            selected = (editor.selected as? EditorPieceLocation.Box)?.type == type,
                            onClick = {
                                editor = if (editor.selected != null && editor.selected !is EditorPieceLocation.Box) editor.moveToBox()
                                else editor.selectBox(type)
                            },
                            label = { Text("${type.editorName()}${if (count > 1) "×$count" else ""}") },
                        )
                    }
                }
            }
            HorizontalDivider()
            if (!editor.hasBothKings) Text(labelInitialPositionMissingKings, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { editor = InitialPositionEditor.fromSfen() }, modifier = Modifier.align(Alignment.End)) {
                Text(labelInitialPositionReset)
            }
        }
    }
}

}

private fun PieceType.editorName(): String = when (this) {
    PieceType.PAWN -> "歩"
    PieceType.LANCE -> "香"
    PieceType.KNIGHT -> "桂"
    PieceType.SILVER -> "銀"
    PieceType.GOLD -> "金"
    PieceType.BISHOP -> "角"
    PieceType.ROOK -> "飛"
    PieceType.KING -> "玉"
    PieceType.PROM_PAWN -> "と"
    PieceType.PROM_LANCE -> "杏"
    PieceType.PROM_KNIGHT -> "圭"
    PieceType.PROM_SILVER -> "全"
    PieceType.PROM_BISHOP -> "馬"
    PieceType.PROM_ROOK -> "龍"
}
