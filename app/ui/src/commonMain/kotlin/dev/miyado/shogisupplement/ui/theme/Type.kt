package dev.miyado.shogisupplement.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── FontFamily 定義 ──────────────────────────────────────────────────────────
//
// フォントバイトの読み込みはプラットフォーム別（expect/actual）:
//   Android: androidx リソースフォント（旧 androidApp と全く同じロード方式・
//     同一バイトのTTFを :ui モジュール自身の res/font から参照。同期・ゼロリスク）
//   iOS: compose resources（commonMain/composeResources/files/font/）から
//     バイト列を同期読み込みし、in-memory Font(identity, data, weight, style) で構築。
// どちらも `Font` オブジェクト自体はComposableコンテキストを必要としないため、
// 呼び出し側（MainActivity 等）は従来どおり `val` プロパティとして参照できる。

internal expect fun buildShipporiMinchoFamily(): FontFamily
internal expect fun buildIbmPlexSansJpFamily(): FontFamily
internal expect fun buildIbmPlexMonoFamily(): FontFamily

/**
 * Shippori Mincho: Display/見出し専用（700のみ）。
 * 画面タイトル・ドリルの問い・レポート見出し。棋書の風格。
 * 小サイズ本文には使わない。
 */
val ShipporiMinchoFamily: FontFamily = buildShipporiMinchoFamily()

/**
 * IBM Plex Sans JP: Body/UI（400/700）。
 * 本文・ボタン・ラベル・説明文。
 */
val IbmPlexSansJpFamily: FontFamily = buildIbmPlexSansJpFamily()

/**
 * IBM Plex Mono: Data（400/600）。
 * 勝率・損失・レート・件数・手数・USI表記など数値と符号は例外なくMono。
 * tabular-nums を前提とした等幅フォント。
 */
val IbmPlexMonoFamily: FontFamily = buildIbmPlexMonoFamily()

// ─── スケール定義 ─────────────────────────────────────────────────────────────

/**
 * DESIGN.md のタイポグラフィスケール:
 * display 28–34 / headline 22 / title 17 / body 15 / label·caption 12 /
 * data-large 34 (Mono) / data 13 (Mono)。行間: 本文1.75、数値1.3
 */
val ShogiTypography = Typography(
    // ─── Display: 画面タイトル・強さ指標 ──────────────────────────────────
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

    // ─── Headline: レポート見出し・ドリルの問い ────────────────────────────
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

    // ─── Title: n手目見出し・カードタイトル ────────────────────────────────
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

    // ─── Body: 本文・説明文 ─────────────────────────────────────────────────
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

    // ─── Label: ラベル・キャプション ────────────────────────────────────────
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

// ─── データ表示用テキストスタイル（Typography 外） ────────────────────────────

/** data-large: 強さ指標・勝率の大きな数値（Mono 34sp、行間1.3） */
val TextStyleDataLarge = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = (34 * 1.3).sp,
)

/** data: 符号・手数・ちいさな数値（Mono 13sp） */
val TextStyleData = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = (13 * 1.3).sp,
)

/** data-move: 指し手表示用（Mono 13-15sp、SemiBold） */
val TextStyleDataMove = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = (14 * 1.4).sp,
)
