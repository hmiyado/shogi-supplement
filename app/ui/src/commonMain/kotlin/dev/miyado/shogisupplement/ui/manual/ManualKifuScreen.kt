package dev.miyado.shogisupplement.ui.manual


import org.jetbrains.compose.resources.vectorResource

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.kifu.KifuReconstructor
import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.kifu.PrivateKifuFields
import dev.miyado.shogisupplement.kifu.PublicKifuFields
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.common.ShogiSecondaryButton
import dev.miyado.shogisupplement.ui.common.ReportBackHandler
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.currentLocalDateTime
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.shogiColors

/** 手動棋譜入力で保存する情報。保存時に既存のKIF取込フローへ渡す。 */
data class ManualKifuDraft(
    val moves: List<String>,
    val senteName: String,
    val goteName: String,
    val startedAt: String,
    val place: String,
    val resigned: Boolean,
) {
    fun resignedAt(currentPly: Int): ManualKifuDraft = copy(
        moves = manualLineAfterResign(moves, currentPly),
        resigned = true,
    )

    /** KIF 1.0の平手本譜へ変換する。 */
    fun toKifText(): String = KifuReconstructor.reconstruct(
        public = PublicKifuFields(
            movesUsi = moves,
            moveTimesSeconds = List(moves.size) { null },
            headers = buildMap {
                put("手合割", "平手")
                if (startedAt.isNotBlank()) put("開始日時", startedAt.trim())
            },
            result = if (resigned) "投了" else null,
            source = KifuSource.OTHER,
        ),
        private = PrivateKifuFields(
            senteName = senteName.trim().ifBlank { null },
            goteName = goteName.trim().ifBlank { null },
            extraHeaders = buildMap {
                if (place.isNotBlank()) put("場所", place.trim())
            },
            comments = emptyList(),
        ),
    )
}

/** 途中局面から指し直すときに、本譜の後続手を置き換える純粋操作。 */
fun manualLineAfterMove(moves: List<String>, currentPly: Int, moveUsi: String): List<String> =
    moves.take(currentPly.coerceIn(0, moves.size)) + moveUsi

/** 途中局面で投了したときに、本譜の後続手を切り捨てる純粋操作。 */
fun manualLineAfterResign(moves: List<String>, currentPly: Int): List<String> =
    moves.take(currentPly.coerceIn(0, moves.size))

/** 分岐は保持せず、表示中局面から指した時点で後続の本譜を切り詰める。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualKifuScreen(
    onClose: () -> Unit,
    onSave: (ManualKifuDraft) -> Unit,
) {
    var moves by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentPly by remember { mutableStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var selectedFrom by remember { mutableStateOf<ShogiSquare?>(null) }
    var selectedDropType by remember { mutableStateOf<PieceType?>(null) }
    var promotionChoices by remember { mutableStateOf<List<ShogiMove>>(emptyList()) }
    var showMoveList by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var senteName by remember { mutableStateOf("") }
    var goteName by remember { mutableStateOf("") }
    var startedAt by remember { mutableStateOf(currentLocalDateTime()) }
    var place by remember { mutableStateOf("") }

    val hasInput = moves.isNotEmpty() || senteName.isNotBlank() ||
        goteName.isNotBlank() || place.isNotBlank()
    val board = remember(moves, currentPly) {
        ShogiBoard().also { b -> moves.take(currentPly).forEach { b.push(ShogiMove.fromUsi(it)) } }
    }
    val legalMoves = remember(board.toSfen()) { board.legalMoves() }
    val legalDestinations = remember(legalMoves, selectedFrom, selectedDropType) {
        when {
            selectedFrom != null -> legalMoves.filter { it.from == selectedFrom }.map { it.to }.toSet()
            selectedDropType != null -> legalMoves.filter { it.dropType == selectedDropType }.map { it.to }.toSet()
            else -> emptySet()
        }
    }

    fun clearSelection() {
        selectedFrom = null
        selectedDropType = null
    }

    fun applyMove(move: ShogiMove) {
        // 現在局面より先の本譜は、ここで新しい手順に置き換える。
        moves = manualLineAfterMove(moves, currentPly, move.toUsiString())
        currentPly = moves.size
        clearSelection()
    }

    fun tapSquare(square: ShogiSquare) {
        val piece = board.pieceAt(square)
        when {
            selectedFrom != null -> {
                val choices = legalMoves.filter { it.from == selectedFrom && it.to == square }
                when {
                    choices.size == 1 -> applyMove(choices.single())
                    choices.size > 1 -> promotionChoices = choices
                    else -> if (piece?.side == board.turn) {
                        selectedFrom = square
                    } else clearSelection()
                }
            }
            selectedDropType != null -> {
                legalMoves.firstOrNull { it.dropType == selectedDropType && it.to == square }
                    ?.let(::applyMove)
            }
            piece?.side == board.turn -> selectedFrom = square
        }
    }

    fun closeOrConfirm() {
        if (hasInput) showDiscard = true else onClose()
    }

    ReportBackHandler(enabled = true, onBack = {
        when {
            showDetails -> showDetails = false
            else -> closeOrConfirm()
        }
    })

    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.manual_kifu_title)) },
                navigationIcon = {
                    IconButton(onClick = ::closeOrConfirm) {
                        Icon(vectorResource(Res.drawable.ic_filled_close), contentDescription = stringResource(Res.string.close))
                    }
                },
                actions = {
                    TextButton(onClick = { showDetails = true }) { Text(stringResource(Res.string.manual_kifu_details)) }
                    TextButton(onClick = {
                        onSave(ManualKifuDraft(moves, senteName, goteName, startedAt, place, resigned = false))
                    }, enabled = moves.isNotEmpty()) { Text(stringResource(Res.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp).adaptiveContentWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShogiBoardView(
                sfen = board.toSfen(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                flip = flip,
                selectedFrom = selectedFrom,
                selectedDropType = selectedDropType,
                legalDestinations = legalDestinations,
                onSquareTapped = ::tapSquare,
                onHandPieceTapped = { pt ->
                    if (board.getHand(board.turn)[pt]?.let { it > 0 } == true) {
                        selectedFrom = null
                        selectedDropType = if (selectedDropType == pt) null else pt
                    }
                },
            )
            ManualNavRow(
                moves = moves,
                currentPly = currentPly,
                onFirst = { currentPly = 0; clearSelection() },
                onPrevious = { currentPly = (currentPly - 1).coerceAtLeast(0); clearSelection() },
                onNext = { currentPly = (currentPly + 1).coerceAtMost(moves.size); clearSelection() },
                onLast = { currentPly = moves.size; clearSelection() },
                onShowList = { showMoveList = true },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShogiSecondaryButton(onClick = { flip = !flip }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.manual_kifu_flip))
                }
                OutlinedButton(
                    onClick = {
                        val draft = ManualKifuDraft(moves, senteName, goteName, startedAt, place, resigned = false)
                            .resignedAt(currentPly)
                        onSave(draft)
                    },
                    enabled = currentPly > 0,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.shogiColors.loss,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.shogiColors.loss),
                ) { Text(stringResource(Res.string.manual_kifu_resign)) }
            }
        }
    }

    if (promotionChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { promotionChoices = emptyList() },
            title = { Text(stringResource(Res.string.manual_kifu_promotion_title)) },
            text = { Text(stringResource(Res.string.manual_kifu_promotion_body)) },
            confirmButton = {
                TextButton(onClick = {
                    promotionChoices.firstOrNull { it.promote }?.let(::applyMove)
                    promotionChoices = emptyList()
                }) { Text(stringResource(Res.string.manual_kifu_promote)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    promotionChoices.firstOrNull { !it.promote }?.let(::applyMove)
                    promotionChoices = emptyList()
                }) { Text(stringResource(Res.string.manual_kifu_not_promote)) }
            },
        )
    }

    if (showMoveList) {
        ModalBottomSheet(onDismissRequest = { showMoveList = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.move_list_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        ManualMoveRow(
                            label = stringResource(Res.string.viewer_start_position),
                            selected = currentPly == 0,
                            onClick = { currentPly = 0; clearSelection(); showMoveList = false },
                        )
                    }
                    itemsIndexed(moves) { index, move ->
                        val notation = runCatching {
                            val previous = ShogiBoard().also { b -> moves.take(index).forEach { b.push(ShogiMove.fromUsi(it)) } }
                            JapaneseNotation.format(move, previous)
                        }.getOrElse { move }
                        ManualMoveRow(
                            label = stringResource(Res.string.move_number_notation, index + 1, notation),
                            selected = currentPly == index + 1,
                            onClick = { currentPly = index + 1; clearSelection(); showMoveList = false },
                        )
                    }
                }
            }
        }
    }

    if (showDetails) {
        ManualGameInfoScreen(
            senteName = senteName,
            goteName = goteName,
            startedAt = startedAt,
            place = place,
            onSenteChange = { senteName = it },
            onGoteChange = { goteName = it },
            onStartedAtChange = { startedAt = it },
            onPlaceChange = { place = it },
            onDone = { showDetails = false },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(Res.string.manual_kifu_discard_title)) },
            text = { Text(stringResource(Res.string.manual_kifu_discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onClose() }) { Text(stringResource(Res.string.manual_kifu_discard)) }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun ManualNavRow(
    moves: List<String>,
    currentPly: Int,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onShowList: () -> Unit,
) {
    val labelMoveFirst = stringResource(Res.string.move_first)
    val labelMovePrevious = stringResource(Res.string.move_previous)
    val labelMoveNext = stringResource(Res.string.move_next)
    val labelMoveLast = stringResource(Res.string.move_last)

    val textViewerStartPosition = stringResource(Res.string.viewer_start_position)
    val notation = remember(moves, currentPly) {
        if (currentPly == 0) null else runCatching {
            val previous = ShogiBoard().also { board ->
                moves.take(currentPly - 1).forEach { board.push(ShogiMove.fromUsi(it)) }
            }
            JapaneseNotation.format(moves[currentPly - 1], previous)
        }.getOrNull()
    }
    val label = when {
        currentPly == 0 -> textViewerStartPosition
        notation != null -> stringResource(Res.string.move_number_notation_dropdown, currentPly, notation)
        else -> stringResource(Res.string.move_number_dropdown, currentPly)
    }
    Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onFirst, enabled = currentPly > 0, contentPadding = PaddingValues(0.dp)) {
            Icon(vectorResource(Res.drawable.ic_filled_first_page), contentDescription = labelMoveFirst)
        }
        TextButton(onClick = onPrevious, enabled = currentPly > 0, contentPadding = PaddingValues(0.dp)) {
            Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_keyboard_arrow_left), contentDescription = labelMovePrevious)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f).clickable(onClick = onShowList),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        TextButton(onClick = onNext, enabled = currentPly < moves.size, contentPadding = PaddingValues(0.dp)) {
            Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_keyboard_arrow_right), contentDescription = labelMoveNext)
        }
        TextButton(onClick = onLast, enabled = currentPly < moves.size, contentPadding = PaddingValues(0.dp)) {
            Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_last_page), contentDescription = labelMoveLast)
        }
    }
}

@Composable
private fun ManualMoveRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
    HorizontalDivider(color = MaterialTheme.shogiColors.line.copy(alpha = 0.5f))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ManualGameInfoScreen(
    senteName: String,
    goteName: String,
    startedAt: String,
    place: String,
    onSenteChange: (String) -> Unit,
    onGoteChange: (String) -> Unit,
    onStartedAtChange: (String) -> Unit,
    onPlaceChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.manual_kifu_info_title)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back), contentDescription = stringResource(Res.string.back)) } },
                actions = { TextButton(onClick = onDone) { Text(stringResource(Res.string.done)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).adaptiveContentWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.manual_kifu_info_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.shogiColors.ink2,
            )
            OutlinedTextField(senteName, onSenteChange, label = { Text(stringResource(Res.string.manual_kifu_sente)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(goteName, onGoteChange, label = { Text(stringResource(Res.string.manual_kifu_gote)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(startedAt, onStartedAtChange, label = { Text(stringResource(Res.string.manual_kifu_started_at)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(place, onPlaceChange, label = { Text(stringResource(Res.string.manual_kifu_place)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
