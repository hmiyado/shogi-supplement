package dev.miyado.shogisupplement.ui.strength

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.EngineMatchRate
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.rating.ShogiRank
import dev.miyado.shogisupplement.rating.bestServiceRank
import dev.miyado.shogisupplement.strength.StrengthEstimator
import dev.miyado.shogisupplement.strength.StrengthNorm
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import dev.miyado.shogisupplement.ui.common.formatShortDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 推定棋力詳細画面のロードを担う協力オブジェクト（[dev.miyado.shogisupplement.ui.home.HomeViewModel]と同じ方針）。
 *
 * ホーム画面の推定棋力カードと同じ集計（[StrengthEstimator.aggregate]）を使うため、
 * 全体の偏差値・推定範囲はホーム画面と必ず一致する。
 */
class StrengthDetailViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {

    /** 推移グラフに出す最大局数。 */
    private val trendGameLimit = 8

    /** @return 解析済みでuser_sideが分かっている対局が1局も無ければ null。 */
    suspend fun loadStrengthDetail(): StrengthDetailData? = withContext(ioDispatcher) {
        val games = gameRepository.getGamesWithUserSide()
        var totalMoves = 0
        val ratings = mutableListOf<Int>()
        for (game in games) {
            val side = game.userSide ?: continue
            totalMoves += userMoveCount(game.moveCount, side)
            ratings += game.rating.toInt()
        }
        if (ratings.isEmpty()) return@withContext null

        val estimate = StrengthEstimator.aggregate(ratings, totalMoves)
        val deviation = StrengthNorm.deviationScore(estimate.rating)
        val width = StrengthNorm.deviationWidth(estimate.errorMargin)

        // 推移グラフは解析日時の新しい順にtrendGameLimit局取り、グラフ左→右のため古い順へ戻す。
        val recentGames = games.sortedByDescending { it.analyzedAt }.take(trendGameLimit).sortedBy { it.analyzedAt }
        val trend = recentGames.map { buildTrendPoint(it) }

        val serviceRanks = settingsRepository.getAllServiceRanks()
        val serviceAccounts = settingsRepository.getAllServiceAccounts()
        val ratingSettings = settingsRepository.getRatingSettings()
        val hasSavedRating = settingsRepository.hasUserSavedRatingSettings()

        StrengthDetailData(
            deviation = deviation,
            rangeLow = deviation - width,
            rangeHigh = deviation + width,
            bestRank = buildBestRank(serviceRanks),
            trend = trend,
            services = buildServices(serviceRanks, serviceAccounts, ratingSettings, hasSavedRating),
        )
    }

    private fun buildTrendPoint(game: GameRecord): StrengthTrendPoint {
        val side = requireNotNull(game.userSide)
        val userMoves = userMoveCount(game.moveCount, side)
        val estimate = StrengthEstimator.aggregate(listOf(game.rating.toInt()), userMoves)
        val deviation = StrengthNorm.deviationScore(estimate.rating)
        val width = StrengthNorm.deviationWidth(estimate.errorMargin)

        val reports = gameRepository.getReports(game.id)
        val positionEvals = gameRepository.getPositionEvals(game.id)
        val matchResult = EngineMatchRate.compute(game.movesUsi, positionEvals, side)

        return StrengthTrendPoint(
            gameId = game.id,
            dateLabel = formatShortDate(game.analyzedAt),
            deviation = deviation,
            deviationWidth = width,
            blunderRateText = blunderRateText(reports, matchResult),
            matchRateText = matchResult?.let {
                AppStrings.matchRateValue((it.rate * 100).roundToInt(), it.matched, it.sampleMoves)
            },
        )
    }

    private fun blunderRateText(reports: List<BlunderRecord>, matchResult: EngineMatchRate.Result?): String? =
        matchResult?.takeIf { it.sampleMoves > 0 }?.let {
            val pct = (reports.size.toDouble() / it.sampleMoves * 100).roundToInt()
            AppStrings.blunderRateValue(pct, reports.size, it.sampleMoves)
        }

    private fun buildBestRank(serviceRanks: Map<String, Map<String, Int>>): StrengthDetailBestRank? {
        val best = bestServiceRank(serviceRanks) ?: return null
        val rankLabel = ShogiRank.fromRaw(best.raw)?.toDisplayString() ?: return null
        return StrengthDetailBestRank(
            label = AppStrings.strengthDetailBestRankValue(AppStrings.serviceLabel(best.service), rankLabel),
            ruleLabel = AppStrings.ruleLabel(best.service, best.ruleId),
        )
    }

    /** 何も入力していないサービスは含めない（対局サービスカードには出さない）。 */
    private fun buildServices(
        serviceRanks: Map<String, Map<String, Int>>,
        serviceAccounts: Map<String, String>,
        ratingSettings: RatingSettings,
        hasSavedRating: Boolean,
    ): List<StrengthDetailService> {
        val result = mutableListOf<StrengthDetailService>()

        val warsAccount = serviceAccounts["shogi_wars"]
        val warsRanks = serviceRanks["shogi_wars"].orEmpty()
        if (warsAccount != null || warsRanks.isNotEmpty()) {
            result += StrengthDetailService(
                serviceId = "shogi_wars",
                label = AppStrings.serviceLabel("shogi_wars"),
                accountName = warsAccount,
                rules = AppStrings.warsRules.map { (ruleId, ruleLabel) ->
                    StrengthDetailServiceRule(
                        ruleLabel = ruleLabel,
                        rankLabel = warsRanks[ruleId]?.let { ShogiRank.fromRaw(it)?.toDisplayString() },
                    )
                },
            )
        }

        // lishogiのレーティングは単一行のsettings（rating_service列）に紐づくため、
        // service=="lishogi"のときだけ有効（RatingSettingsDialogと同じ制約。buildDeclaredRankLine参照）。
        val lishogiAccount = serviceAccounts["lishogi"]
        val lishogiRating = if (ratingSettings.service == "lishogi" && hasSavedRating) ratingSettings.ratingRaw else null
        if (lishogiAccount != null || lishogiRating != null) {
            result += StrengthDetailService(
                serviceId = "lishogi",
                label = AppStrings.serviceLabel("lishogi"),
                accountName = lishogiAccount,
                ratingText = lishogiRating?.toString(),
            )
        }

        val kiouAccount = serviceAccounts["kiou"]
        val kiouRanks = serviceRanks["kiou"].orEmpty()
        if (kiouAccount != null || kiouRanks.isNotEmpty()) {
            result += StrengthDetailService(
                serviceId = "kiou",
                label = AppStrings.serviceLabel("kiou"),
                accountName = kiouAccount,
                rules = AppStrings.kiouRules.map { (ruleId, ruleLabel) ->
                    StrengthDetailServiceRule(
                        ruleLabel = ruleLabel,
                        rankLabel = kiouRanks[ruleId]?.let { ShogiRank.fromRaw(it)?.toDisplayString() },
                    )
                },
            )
        }

        return result
    }

    /** 総手数から user_side の手数を算出する。先手: ceil(total/2), 後手: floor(total/2)。 */
    private fun userMoveCount(totalMoves: Long, userSide: String): Int {
        val t = totalMoves.toInt()
        return if (userSide == "sente") (t + 1) / 2 else t / 2
    }
}
