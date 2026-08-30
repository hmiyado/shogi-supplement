package dev.miyado.shogisupplement.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.miyado.shogisupplement.kifu.timeControlDisplayText
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily

/**
 * 持ち時間の表示テキスト。[mainRaw] はKIF「持ち時間」ヘッダの原文、[byoyomiRaw] は
 * 「秒読み」ヘッダの原文。[sourcePlace] は対局サービス（[dev.miyado.shogisupplement.kifu.KifuSource.wireValue]）。
 * mainRawが空欄なら非表示。
 */
fun buildTimeControlLine(sourcePlace: String?, mainRaw: String?, byoyomiRaw: String?): AnnotatedString? {
    val text = timeControlDisplayText(sourcePlace, mainRaw, byoyomiRaw) ?: return null
    return withMonoNumbers(text)
}

/** 文中の数値と符号だけをmonoにする（DESIGN.md「数値と符号は例外なくmono」）。 */
fun withMonoNumbers(text: String): AnnotatedString = buildAnnotatedString { appendWithMonoNumbers(text) }

private fun AnnotatedString.Builder.appendWithMonoNumbers(text: String) {
    var lastIndex = 0
    for (match in Regex("""[+-]?\d+""").findAll(text)) {
        if (match.range.first > lastIndex) append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(match.value) }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}
