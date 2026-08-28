package dev.miyado.shogisupplement.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily

/** レートは DESIGN.md の規約（数値と符号は例外なくmono）に従い、この書体で出す。 */
internal fun AnnotatedString.Builder.appendPlayerRating(rating: Long?) {
    if (rating == null) return
    withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
        append("($rating)")
    }
}
