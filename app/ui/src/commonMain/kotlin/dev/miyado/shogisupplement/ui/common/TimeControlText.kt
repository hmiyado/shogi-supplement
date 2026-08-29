package dev.miyado.shogisupplement.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily

/**
 * 持ち時間の表示テキスト。[mainRaw] はKIF「持ち時間」ヘッダの原文、[byoyomiRaw] は
 * 「秒読み」ヘッダの原文。[sourcePlace] は対局サービス（[dev.miyado.shogisupplement.kifu.KifuSource.wireValue]）。
 * mainRawが空欄なら非表示。
 */
fun buildTimeControlLine(sourcePlace: String?, mainRaw: String?, byoyomiRaw: String?): AnnotatedString? {
    if (mainRaw.isNullOrBlank()) return null
    val (label, displayText) = resolveTimeControlDisplay(sourcePlace, mainRaw, byoyomiRaw)
    return buildAnnotatedString {
        if (label != null) append("$label（")
        appendWithMonoNumbers(displayText)
        if (label != null) append("）")
    }
}

private fun AnnotatedString.Builder.appendWithMonoNumbers(text: String) {
    var lastIndex = 0
    for (match in Regex("""[+-]?\d+""").findAll(text)) {
        if (match.range.first > lastIndex) append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(match.value) }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}
