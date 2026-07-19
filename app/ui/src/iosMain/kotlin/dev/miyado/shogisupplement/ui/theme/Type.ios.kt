package dev.miyado.shogisupplement.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import dev.miyado.shogisupplement.ui.generated.resources.Res
import kotlinx.coroutines.runBlocking

// iOS: compose resources（commonMain/composeResources/files/font/）から
// バイト列を同期読み込みし、in-memory Font(identity, data, weight, style) で構築する。
// 起動時に一度だけ（アプリの寿命内でキャッシュ）読み込むため実害のあるブロッキングにはならない。

private fun readFontBytes(path: String): ByteArray = runBlocking { Res.readBytes(path) }

private val shipporiMinchoBoldBytes by lazy { readFontBytes("files/font/shippori_mincho_bold.ttf") }
private val ibmPlexSansJpRegularBytes by lazy { readFontBytes("files/font/ibm_plex_sans_jp_regular.ttf") }
private val ibmPlexSansJpBoldBytes by lazy { readFontBytes("files/font/ibm_plex_sans_jp_bold.ttf") }
private val ibmPlexMonoRegularBytes by lazy { readFontBytes("files/font/ibm_plex_mono_regular.ttf") }
private val ibmPlexMonoSemiBoldBytes by lazy { readFontBytes("files/font/ibm_plex_mono_semi_bold.ttf") }

internal actual fun buildShipporiMinchoFamily(): FontFamily = FontFamily(
    Font("ShipporiMincho-Bold", shipporiMinchoBoldBytes, FontWeight.Bold),
    Font("ShipporiMincho-Bold", shipporiMinchoBoldBytes, FontWeight.Normal),
)

internal actual fun buildIbmPlexSansJpFamily(): FontFamily = FontFamily(
    Font("IBMPlexSansJP-Regular", ibmPlexSansJpRegularBytes, FontWeight.Normal),
    Font("IBMPlexSansJP-Bold", ibmPlexSansJpBoldBytes, FontWeight.Bold),
    Font("IBMPlexSansJP-Regular", ibmPlexSansJpRegularBytes, FontWeight.Medium),
)

internal actual fun buildIbmPlexMonoFamily(): FontFamily = FontFamily(
    Font("IBMPlexMono-Regular", ibmPlexMonoRegularBytes, FontWeight.Normal),
    Font("IBMPlexMono-SemiBold", ibmPlexMonoSemiBoldBytes, FontWeight.SemiBold),
    Font("IBMPlexMono-Regular", ibmPlexMonoRegularBytes, FontWeight.Medium),
)
