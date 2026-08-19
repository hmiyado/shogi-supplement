package dev.miyado.shogisupplement.ui.strength

/**
 * 推定棋力詳細画面（ホーム画面の推定棋力カードタップで遷移）の表示データ。
 *
 * プラットフォーム非依存のプレーンな Kotlin 型（[dev.miyado.shogisupplement.ui.home.StrengthCardData]と同じ方針）。
 */
data class StrengthDetailData(
    /** 現在の推定棋力（偏差値）。 */
    val deviation: Int,
    /** 推定範囲の下限（偏差値）。 */
    val rangeLow: Int,
    /** 推定範囲の上限（偏差値）。 */
    val rangeHigh: Int,
    /** 対局サービスでの最高段級位（null = どのサービスにも段級位申告が無い）。 */
    val bestRank: StrengthDetailBestRank?,
    /** 対局ごとの推移（解析日時の古い順。最大8局）。 */
    val trend: List<StrengthTrendPoint>,
    /** 対局サービス一覧（何も入力していないサービスは含めない）。 */
    val services: List<StrengthDetailService>,
)

/** 対局サービスでの最高段級位の表示データ。 */
data class StrengthDetailBestRank(
    /** 例: "将棋ウォーズ 初段" */
    val label: String,
    /** 由来ルール名。例: "3分切れ負け" */
    val ruleLabel: String,
)

/** 推移グラフの1点（1局分）。 */
data class StrengthTrendPoint(
    val gameId: Long,
    /** x軸ラベル。例: "8/17" */
    val dateLabel: String,
    /** この対局単独の推定棋力（偏差値）。 */
    val deviation: Int,
    /** 推定範囲の±幅（偏差値）。 */
    val deviationWidth: Int,
    /** 悪手率の値表示（例: "8%(3/38)"）。算出不能なら null。 */
    val blunderRateText: String?,
    /** 一致率の値表示（例: "47%(18/38)"）。算出不能なら null。 */
    val matchRateText: String?,
)

/** 対局サービス1件の表示データ。 */
data class StrengthDetailService(
    val serviceId: String,
    /** 例: "将棋ウォーズ" */
    val label: String,
    /** null = アカウント名未入力。 */
    val accountName: String?,
    /** ルール別段級位（将棋ウォーズ・棋桜のみ）。lishogiは空。 */
    val rules: List<StrengthDetailServiceRule> = emptyList(),
    /** lishogiのレーティング表示（数値文字列）。null = 未入力 or ルール制サービス。 */
    val ratingText: String? = null,
)

/** サービス内の1ルール分の段級位。 */
data class StrengthDetailServiceRule(
    /** 例: "3分切れ負け" */
    val ruleLabel: String,
    /** null = 未入力。 */
    val rankLabel: String?,
)
