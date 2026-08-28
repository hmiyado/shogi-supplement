package dev.miyado.shogisupplement.ui.home

/** ホーム画面のプラットフォーム非依存な状態型。 */

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

/**
 * 学習の記録カードの表示データ。積み上げ値（後退しない数字）だけを持つ。
 * 正答率のような増減する指標は意図的に含めない。分散学習・交互練習を取り入れると
 * 正答率は一時的に下がるのが正常なため。
 */
data class DrillRecordCardData(
    /** 直近[windowDays]日のうち、次の一手問題を1問以上解いた日数。 */
    val activeDaysInWindow: Int,
    val windowDays: Int,
    /** 全期間の累計解答数。 */
    val totalAttempts: Int,
    /** 連続取組が7日進むごとに1回加算する累計回数（14日連続なら2回）。 */
    val weekStreakCount: Int,
)
