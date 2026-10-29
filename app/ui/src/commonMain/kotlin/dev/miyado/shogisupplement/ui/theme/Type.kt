package dev.miyado.shogisupplement.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import dev.miyado.shogisupplement.ui.generated.resources.*

val ShipporiMinchoFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.shippori_mincho_bold, FontWeight.Bold),
        Font(Res.font.shippori_mincho_bold, FontWeight.Normal),
    )
val IbmPlexSansJpFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.ibm_plex_sans_jp_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_sans_jp_bold, FontWeight.Bold),
        Font(Res.font.ibm_plex_sans_jp_regular, FontWeight.Medium),
    )
val IbmPlexMonoFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_semi_bold, FontWeight.SemiBold),
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Medium),
    )

/**
 * DESIGN.md のタイポグラフィスケール:
 * display 28–34 / headline 22 / title 17 / body 15 / label·caption 12 /
 * data-large 34 (Mono) / data 13 (Mono)。行間: 本文1.75、数値1.3
 */
val ShogiTypography: Typography
    @Composable get() = Typography(
    displayLarge = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = (34 * 1.3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = (28 * 1.3).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = (28 * 1.3).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = (22 * 1.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = (22 * 1.45).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = (22 * 1.45).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = ShipporiMinchoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = (17 * 1.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = (17 * 1.5).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = (15 * 1.5).sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = (15 * 1.75).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = (15 * 1.75).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = (13 * 1.75).sp,
    ),

    labelLarge = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = (12 * 1.5).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = (12 * 1.5).sp,
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexSansJpFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = (12 * 1.5).sp,
    ),
)


/** data-large: 強さ指標・勝率の大きな数値（Mono 34sp、行間1.3） */
val TextStyleDataLarge: TextStyle
    @Composable get() = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = (34 * 1.3).sp,
)

/** data: 符号・手数・ちいさな数値（Mono 13sp） */
val TextStyleData: TextStyle
    @Composable get() = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = (13 * 1.3).sp,
)

/** data-move: 指し手表示用（Mono 13-15sp、SemiBold） */
val TextStyleDataMove: TextStyle
    @Composable get() = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = (14 * 1.4).sp,
)
