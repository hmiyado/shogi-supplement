package dev.miyado.shogisupplement.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ─── カスタムカラートークン（Material3 ColorScheme にマップされない追加色） ──

/**
 * DESIGN.md のカスタムトークンを保持するクラス。
 * MaterialTheme.colorScheme に含まれない色はここから参照する。
 */
data class ShogiColors(
    /** 控えめな面（バッジ背景等）*/
    val surface2: Color,
    /** 副文字 */
    val ink2: Color,
    /** キャプション */
    val ink3: Color,
    /** 罫線 */
    val line: Color,
    /** 卵黄: 選択マス・今日の1問の座布団（面専用） */
    val highlight: Color,
    /** 卵黄の淡い面 */
    val highlightSoft: Color,
    /** 朱: 悪手・損失・不正解・成駒 */
    val loss: Color,
    /** 朱の淡い面 */
    val lossSoft: Color,
    /** 紺青の淡い面 */
    val primarySoft: Color,
    /** 盤（淡い榧） */
    val board: Color,
    /** 盤線 */
    val boardLine: Color,
)

val LocalShogiColors = staticCompositionLocalOf {
    ShogiColors(
        surface2 = LightSurface2,
        ink2 = LightInk2,
        ink3 = LightInk3,
        line = LightLine,
        highlight = LightHighlight,
        highlightSoft = LightHighlightSoft,
        loss = LightLoss,
        lossSoft = LightLossSoft,
        primarySoft = LightPrimarySoft,
        board = LightBoard,
        boardLine = LightBoardLine,
    )
}

/** コードベースから `MaterialTheme.shogiColors.xxx` でアクセスするための拡張プロパティ */
val MaterialTheme.shogiColors: ShogiColors
    @Composable
    get() = LocalShogiColors.current

// ─── ColorScheme ─────────────────────────────────────────────────────────────

private val LightColors: ColorScheme = lightColorScheme(
    background = LightBg,
    surface = LightSurface,
    onBackground = LightInk,
    onSurface = LightInk,
    onSurfaceVariant = LightInk2,

    primary = LightPrimary,
    onPrimary = LightSurface,
    primaryContainer = LightPrimarySoft,
    onPrimaryContainer = LightPrimary,

    // error = loss（朱）
    error = LightLoss,
    onError = LightSurface,
    errorContainer = LightLossSoft,
    onErrorContainer = LightLoss,

    outline = LightLine,
    outlineVariant = LightLine,
    surfaceVariant = LightSurface2,

    // Card等のデフォルト面が M3 ベースライン（紫系）にフォールバックしないよう明示
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface2,
    surfaceContainerHighest = LightSurface2,
    surfaceBright = LightSurface,
    surfaceDim = LightBg,
    // tonal elevation で primary が面に混ざらないよう無効化
    surfaceTint = LightSurface,

    // secondary = surface2系（控えめな面の代替）
    secondary = LightInk2,
    onSecondary = LightSurface,
    secondaryContainer = LightSurface2,
    onSecondaryContainer = LightInk,
)

private val DarkColors: ColorScheme = darkColorScheme(
    background = DarkBg,
    surface = DarkSurface,
    onBackground = DarkInk,
    onSurface = DarkInk,
    onSurfaceVariant = DarkInk2,

    primary = DarkPrimary,
    onPrimary = DarkBg,
    primaryContainer = DarkPrimarySoft,
    onPrimaryContainer = DarkPrimary,

    error = DarkLoss,
    onError = DarkBg,
    errorContainer = DarkLossSoft,
    onErrorContainer = DarkLoss,

    outline = DarkLine,
    outlineVariant = DarkLine,
    surfaceVariant = DarkSurface2,

    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface2,
    surfaceContainerHighest = DarkSurface2,
    surfaceBright = DarkSurface2,
    surfaceDim = DarkBg,
    surfaceTint = DarkSurface,

    secondary = DarkInk2,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurface2,
    onSecondaryContainer = DarkInk,
)

// ─── Shapes ──────────────────────────────────────────────────────────────────

/**
 * DESIGN.md: カード12・ボタン8・バッジ4。full は使わない（チップのみ 999 可）。
 */
private val ShogiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // バッジ・チップ
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),        // ボタン
    large = RoundedCornerShape(12.dp),        // カード
    extraLarge = RoundedCornerShape(12.dp),
)

// ─── Theme Composable ─────────────────────────────────────────────────────────

/**
 * 将棋サプリのテーマ。
 * MaterialTheme をラップし、DESIGN.md のカラートークン・タイポグラフィ・シェイプを適用する。
 * Dynamic color（Material You）は使わない——ブランド色が意味を担うため固定。
 *
 * @param themeMode 'system'（デフォルト）/ 'light' / 'dark'。
 *   system = isSystemInDarkTheme() に委譲。
 */
@Composable
fun ShogiTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val shogiColors = if (darkTheme) {
        ShogiColors(
            surface2 = DarkSurface2,
            ink2 = DarkInk2,
            ink3 = DarkInk3,
            line = DarkLine,
            highlight = DarkHighlight,
            highlightSoft = DarkHighlightSoft,
            loss = DarkLoss,
            lossSoft = DarkLossSoft,
            primarySoft = DarkPrimarySoft,
            board = DarkBoard,
            boardLine = DarkBoardLine,
        )
    } else {
        ShogiColors(
            surface2 = LightSurface2,
            ink2 = LightInk2,
            ink3 = LightInk3,
            line = LightLine,
            highlight = LightHighlight,
            highlightSoft = LightHighlightSoft,
            loss = LightLoss,
            lossSoft = LightLossSoft,
            primarySoft = LightPrimarySoft,
            board = LightBoard,
            boardLine = LightBoardLine,
        )
    }

    CompositionLocalProvider(LocalShogiColors provides shogiColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShogiTypography,
            shapes = ShogiShapes,
            content = content,
        )
    }
}
