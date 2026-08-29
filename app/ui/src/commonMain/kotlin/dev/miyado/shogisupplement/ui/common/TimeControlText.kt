package dev.miyado.shogisupplement.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.miyado.shogisupplement.kifu.TimeControlKind
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily

/** 持ち時間の表示テキスト。判定できない（kind・baseMinutes未確定）場合はnull。 */
fun buildTimeControlLine(kind: String?, baseMinutes: Long?, incrementSeconds: Long?): AnnotatedString? {
    if (baseMinutes == null) return null
    val timeControlKind = TimeControlKind.entries.firstOrNull { it.wireValue == kind } ?: return null
    if (timeControlKind != TimeControlKind.SUDDEN_DEATH && incrementSeconds == null) return null
    return buildAnnotatedString {
        fun AnnotatedString.Builder.appendMono(value: Long) {
            withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(value.toString()) }
        }
        fun AnnotatedString.Builder.appendMono(text: String) {
            withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(text) }
        }
        when (timeControlKind) {
            TimeControlKind.FISCHER -> {
                appendMono(baseMinutes)
                append(AppStrings.TIME_CONTROL_FISCHER_MID)
                appendMono("+${incrementSeconds!!}")
                append(AppStrings.TIME_CONTROL_FISCHER_SUFFIX)
            }
            TimeControlKind.SUDDEN_DEATH -> {
                appendMono(baseMinutes)
                append(AppStrings.TIME_CONTROL_SUDDEN_DEATH_SUFFIX)
            }
            TimeControlKind.BYOYOMI -> {
                if (baseMinutes > 0) {
                    appendMono(baseMinutes)
                    append(AppStrings.TIME_CONTROL_BYOYOMI_MID)
                } else {
                    append(AppStrings.TIME_CONTROL_BYOYOMI_ONLY_PREFIX)
                }
                appendMono(incrementSeconds!!)
                append(AppStrings.TIME_CONTROL_SECONDS_SUFFIX)
            }
        }
    }
}
