package dev.miyado.shogisupplement.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import dev.miyado.shogisupplement.ui.generated.resources.Res

// Android/iOS実装が使う同期ロード（バイト読み込みをrunBlockingで待つ）はwasmJsに
// actualが無く使えない（フォント読み込み自体が本来ネットワーク経由の非同期I/Oのため）。
// [preloadShogiWebFonts] をComposeViewport起動前（:webAppのmain()）で待ち切ることで、
// 初回コンポーズの時点では常にロード済みの状態にする。これによりTheme.kt・
// ShipporiMinchoFamily等のトップレベルval・呼び出し側11箇所超のcommonMainコードは
// android/iOSと同じ「同期に見えるAPI」のまま変更せずに済む（Composable化して
// 呼び出し側全体を書き換えるより変更範囲が小さい）。[preloadShogiWebFonts] を
// 呼ばずに実行した場合（テスト等）はシステムフォントへフォールバックする。

private var shipporiMinchoBoldBytes: ByteArray? = null
private var ibmPlexSansJpRegularBytes: ByteArray? = null
private var ibmPlexSansJpBoldBytes: ByteArray? = null
private var ibmPlexMonoRegularBytes: ByteArray? = null
private var ibmPlexMonoSemiBoldBytes: ByteArray? = null

/**
 * DESIGN.mdの実書体（Shippori Mincho/IBM Plex Sans JP/Mono）をfetchで取得し、
 * 以降の [buildShipporiMinchoFamily] 等の呼び出しに反映させる。ComposeViewportで
 * 最初のコンポーズを始める前に一度だけ呼ぶこと（呼び出し側は :webApp の main()）。
 */
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
