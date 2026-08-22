package dev.miyado.shogisupplement.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import dev.miyado.shogisupplement.ui.generated.resources.Res

// wasmJsは非同期fetchで事前ロードし、未ロード時はシステムフォントへフォールバックする。

private var shipporiMinchoBoldBytes: ByteArray? = null
private var ibmPlexSansJpRegularBytes: ByteArray? = null
private var ibmPlexSansJpBoldBytes: ByteArray? = null
private var ibmPlexMonoRegularBytes: ByteArray? = null
private var ibmPlexMonoSemiBoldBytes: ByteArray? = null

/** ComposeViewport開始前にDESIGN.mdの実書体をfetchして登録する。 */
suspend fun preloadShogiWebFonts() {
    shipporiMinchoBoldBytes = Res.readBytes("files/font/shippori_mincho_bold.ttf")
    ibmPlexSansJpRegularBytes = Res.readBytes("files/font/ibm_plex_sans_jp_regular.ttf")
    ibmPlexSansJpBoldBytes = Res.readBytes("files/font/ibm_plex_sans_jp_bold.ttf")
    ibmPlexMonoRegularBytes = Res.readBytes("files/font/ibm_plex_mono_regular.ttf")
    ibmPlexMonoSemiBoldBytes = Res.readBytes("files/font/ibm_plex_mono_semi_bold.ttf")
}

internal actual fun buildShipporiMinchoFamily(): FontFamily {
    val bytes = shipporiMinchoBoldBytes ?: return FontFamily.Serif
    return FontFamily(
        Font("ShipporiMincho-Bold", bytes, FontWeight.Bold),
        Font("ShipporiMincho-Bold", bytes, FontWeight.Normal),
    )
}

internal actual fun buildIbmPlexSansJpFamily(): FontFamily {
    val regular = ibmPlexSansJpRegularBytes ?: return FontFamily.SansSerif
    val bold = ibmPlexSansJpBoldBytes ?: return FontFamily.SansSerif
    return FontFamily(
        Font("IBMPlexSansJP-Regular", regular, FontWeight.Normal),
        Font("IBMPlexSansJP-Bold", bold, FontWeight.Bold),
        Font("IBMPlexSansJP-Regular", regular, FontWeight.Medium),
    )
}

internal actual fun buildIbmPlexMonoFamily(): FontFamily {
    val regular = ibmPlexMonoRegularBytes ?: return FontFamily.Monospace
    val semiBold = ibmPlexMonoSemiBoldBytes ?: return FontFamily.Monospace
    return FontFamily(
        Font("IBMPlexMono-Regular", regular, FontWeight.Normal),
        Font("IBMPlexMono-SemiBold", semiBold, FontWeight.SemiBold),
        Font("IBMPlexMono-Regular", regular, FontWeight.Medium),
    )
}
