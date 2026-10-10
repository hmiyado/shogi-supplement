package dev.miyado.shogisupplement.ui.text

import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay.EvalLabel
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay.EvalText
import dev.miyado.shogisupplement.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

private fun EvalText.resource(): Pair<StringResource, List<Any>> = when (this) {
    is EvalText.Literal -> Res.string.literal_text to listOf(text)
    is EvalText.Cp -> when {
        value > 0 -> Res.string.text_cp_signed_label to listOf(value)
        value < 0 -> Res.string.text_cp_signed_label_2 to listOf(-value)
        else -> Res.string.text_cp_signed_label_3 to emptyList()
    }
    is EvalText.WinProbability -> Res.string.position_eval_probability to listOf(percent)
    is EvalText.Mate -> (if (moves > 0) Res.string.position_eval_mate_win else Res.string.position_eval_mate_loss) to listOf(kotlin.math.abs(moves))
    EvalText.MateWin -> Res.string.position_eval_mate_zero_win to emptyList()
    EvalText.MateLoss -> Res.string.position_eval_mate_zero_loss to emptyList()
}

val EvalLabel.text: String
    @Composable get() {
        val (resource, args) = value.resource()
        return stringResource(resource, *args.toTypedArray())
    }

suspend fun EvalLabel.resolveText(): String {
    val (resource, args) = value.resource()
    return getString(resource, *args.toTypedArray())
}
