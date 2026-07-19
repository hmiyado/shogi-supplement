package dev.miyado.shogisupplement.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Light tokens ─────────────────────────────────────────────────────────────

/** 画面背景（生成り・和紙） */
val LightBg = Color(0xFFF7F3EA)

/** カード面 */
val LightSurface = Color(0xFFFFFDF7)

/** 控えめな面（バッジ背景等） */
val LightSurface2 = Color(0xFFEFE9DC)

/** 主文字（濃墨） */
val LightInk = Color(0xFF211E1A)

/** 副文字 */
val LightInk2 = Color(0xFF5C564C)

/** キャプション（鼠系） */
val LightInk3 = Color(0xFF8C857B)

/** 罫線 */
val LightLine = Color(0xFFDDD5C4)

/** 紺青: ボタン・リンク・最善手・改善値 */
val LightPrimary = Color(0xFF3A4B7C)

/** 紺青の淡い面（済みバッジ等） */
val LightPrimarySoft = Color(0xFFE4E8F2)

/** 卵黄: 選択マス・今日の1問の座布団（面専用・文字色不可） */
val LightHighlight = Color(0xFFEEDD77)

/** 卵黄の淡い面 */
val LightHighlightSoft = Color(0xFFF7EFC5)

/** 朱: 悪手・損失・不正解・削除 */
val LightLoss = Color(0xFFC73E3A)

/** 朱の淡い面 */
val LightLossSoft = Color(0xFFF5E0DE)

/** 盤（淡い榧） */
val LightBoard = Color(0xFFE9C98E)

/** 盤線 */
val LightBoardLine = Color(0xFFA98B54)

// ─── Dark tokens ──────────────────────────────────────────────────────────────

val DarkBg = Color(0xFF1A1815)
val DarkSurface = Color(0xFF242119)
val DarkSurface2 = Color(0xFF2E2A21)
val DarkInk = Color(0xFFEAE4D6)
val DarkInk2 = Color(0xFFB5AC9C)
val DarkInk3 = Color(0xFF857D6E)
val DarkLine = Color(0xFF3B362B)
val DarkPrimary = Color(0xFF8FA3D4)
val DarkPrimarySoft = Color(0xFF2A3147)
val DarkHighlight = Color(0xFFD9C766)
val DarkHighlightSoft = Color(0xFF3B371F)
val DarkLoss = Color(0xFFE06B62)
val DarkLossSoft = Color(0xFF43261F)
val DarkBoard = Color(0xFFB69A62)
val DarkBoardLine = Color(0xFF8A7040)

// ─── Semantic aliases（コードベース内で使う意味名） ────────────────────────────

/** 正解・改善・進捗 = primary系 */
val ColorCorrect get() = LightPrimary

/** 損失・不正解・削除 = loss系 */
val ColorLoss get() = LightLoss

/** 注目面 = highlight */
val ColorHighlight get() = LightHighlight
