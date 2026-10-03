package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.board.EditorPieceLocation
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.board.Side
import kotlin.math.roundToInt

sealed interface EditorDropTarget {
    data class Square(val square: ShogiSquare) : EditorDropTarget
    data class Hand(val side: Side) : EditorDropTarget
    data object Box : EditorDropTarget
}

class EditorDragState {
    val targets = mutableMapOf<EditorDropTarget, Rect>()
    var position by mutableStateOf<Offset?>(null)
    var source: EditorPieceLocation? = null
    var label by mutableStateOf("")
    var drop: (EditorPieceLocation, EditorDropTarget) -> Unit = { _, _ -> }
    var pieceLabel: (EditorPieceLocation) -> String = { "" }
    fun cancel() { position = null; source = null }
}

private val LocalEditorDrag = staticCompositionLocalOf<EditorDragState?> { null }

@Composable
fun EditorDragHost(
    onDrop: (EditorPieceLocation, EditorDropTarget) -> Unit,
    pieceLabel: (EditorPieceLocation) -> String,
    content: @Composable () -> Unit,
) {
    val state = remember { EditorDragState() }
    state.drop = onDrop
    state.pieceLabel = pieceLabel
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        CompositionLocalProvider(LocalEditorDrag provides state, content = content)
        state.position?.let { point ->
            Surface(Modifier.offset { IntOffset((point.x - origin.x - 20).roundToInt(), (point.y - origin.y - 24).roundToInt()) },
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(state.label, fontSize = 28.sp)
            }
        }
    }
}

@Composable
fun Modifier.editorDropTarget(target: EditorDropTarget): Modifier {
    val state = LocalEditorDrag.current ?: return this
    DisposableEffect(state, target) { onDispose { state.targets.remove(target) } }
    return onGloballyPositioned { state.targets[target] = it.boundsInRoot() }
}

@Composable
fun Modifier.editorDragSource(source: EditorPieceLocation, enabled: Boolean = true): Modifier {
    val state = LocalEditorDrag.current ?: return this
    val active by rememberUpdatedState(enabled)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    return onGloballyPositioned { bounds = it.boundsInRoot() }.pointerInput(state, source) {
        detectDragGestures(
            onDragStart = { point -> if (active) {
                state.source = source
                state.label = state.pieceLabel(source)
                state.position = bounds.topLeft + point
            } },
            onDragCancel = state::cancel,
            onDragEnd = {
                val from = state.source
                val point = state.position
                val target = point?.let { p -> state.targets.entries.firstOrNull { it.value.contains(p) }?.key }
                state.cancel()
                if (from != null && target != null) state.drop(from, target)
            },
            onDrag = { change, delta -> if (state.source != null) {
                change.consume()
                state.position = state.position?.plus(delta)
            } },
        )
    }
}
