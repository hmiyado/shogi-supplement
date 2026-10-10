package dev.miyado.shogisupplement.classify

import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

data class BlunderCategoryLabel(private val resource: StringResource?, private val fallback: String) {
    val label: String
        @Composable get() = resource?.let { stringResource(it) } ?: fallback
    suspend fun resolveLabel(): String = resource?.let { getString(it) } ?: fallback
}

object BlunderCategoryLabels {
    private val mapping = mapOf(
        "詰み見逃し" to Res.string.blunder_category_missed_mate,
        "頓死" to Res.string.blunder_category_mated,
        "駒損（即取り）" to Res.string.blunder_category_hanging_piece,
        "駒損（タクティクス）" to Res.string.blunder_category_tactic,
        "玉の危険（寄せ）" to Res.string.blunder_category_king_danger,
        "位置的・その他" to Res.string.blunder_category_positional,
    )
    fun of(internalKey: String): BlunderCategoryLabel = BlunderCategoryLabel(mapping[internalKey], internalKey)
    val knownKeys: Set<String> get() = mapping.keys
}
