package dev.miyado.shogisupplement.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.miyado.shogisupplement.ui.R

// Android: 旧 androidApp と全く同じロード方式（androidx リソースフォント・同期）。
// フォントファイルはバイト単位で同一のものを :ui/src/androidMain/res/font に配置。

internal actual fun buildShipporiMinchoFamily(): FontFamily = FontFamily(
    Font(R.font.shippori_mincho_bold, FontWeight.Bold),
    // W700 を W400 にも割り当て（見出し限定なので単一ウェイトで十分）
    Font(R.font.shippori_mincho_bold, FontWeight.Normal),
)

internal actual fun buildIbmPlexSansJpFamily(): FontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_jp_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_jp_bold, FontWeight.Bold),
    // 500 → Regular で代用
    Font(R.font.ibm_plex_sans_jp_regular, FontWeight.Medium),
)

internal actual fun buildIbmPlexMonoFamily(): FontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semi_bold, FontWeight.SemiBold),
    // 500 → Regular で代用
    Font(R.font.ibm_plex_mono_regular, FontWeight.Medium),
)
