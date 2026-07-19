package dev.miyado.shogisupplement.ui.home

/**
 * ホーム画面の UI 状態のうち、プラットフォーム非依存のデータ型。
 *
 * lifecycle 依存のないプレーンな Kotlin 型。
 */

/** 強さ指標カードの表示データ。 */
data class StrengthCardData(
    val displayText: String,
    val detailText: String,
    /** 保存済みのレートサービス（申告レート行表示用）。 */
    val savedService: String? = null,
    /** 保存済みのrawレート（申告レート行表示用。ウォーズ/棋桜は段級位整数エンコード）。 */
    val savedRatingRaw: Int? = null,
    /** 保存済みのルール（申告レート行表示用）。 */
    val savedRatingRule: String? = null,
    /** ルール別申告棋力の整形済み1行文（null=未設定）。 */
    val declaredRankLine: String? = null,
)

/** 「今日の1問」ヒント（ネタバレなし: 手数のみで出典局名は表示しない）。 */
data class TodaysDrillHint(
    val ply: Long,
)
