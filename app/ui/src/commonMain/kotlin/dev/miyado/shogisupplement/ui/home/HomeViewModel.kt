package dev.miyado.shogisupplement.ui.home

import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.rating.ShogiRank
import dev.miyado.shogisupplement.strength.StrengthEstimator
import dev.miyado.shogisupplement.strength.toDisplayString
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** ホームのゲーム一覧、推定棋力カード、今日の問題に必要な表示データを計算する。 */
class HomeViewModel(
    private val gameRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {

    data class HomeResult(
        val games: List<GameRecord>,
        val strengthCard: StrengthCardData?,
        val todaysDrillHint: TodaysDrillHint?,
    )

    /** ホーム画面（過去の解析一覧）表示用データをロードする。 */
    suspend fun loadHomeData(): HomeResult = withContext(ioDispatcher) {
        val g = gameRepository.getAllGames()
        val drillCandidates = drillRepository.getDrillCandidates()
        val sc = computeStrengthCard(gameRepository.getGamesWithUserSide())
        val hint = drillCandidates.firstOrNull()?.let { blunder ->
            val gameExists = g.any { it.id == blunder.gameId }
            if (gameExists) TodaysDrillHint(blunder.ply) else null
        }
        HomeResult(g, sc, hint)
    }

    /** user_side設定済みゲームから強さ指標カードを計算する。Why not悪手を再集計しない: 推定器v2は局単位の予測を平均するため。 */
    private fun computeStrengthCard(games: List<GameRecord>): StrengthCardData? {
        val settings = settingsRepository.getRatingSettings()
        if (games.isEmpty()) return null
        var totalMoves = 0
        val ratings = mutableListOf<Int>()
        for (game in games) {
            val side = game.userSide ?: continue
            totalMoves += userMoveCount(game.moveCount, side)
            ratings += game.rating.toInt()
        }
        if (ratings.isEmpty()) return null
        val estimate = StrengthEstimator.aggregate(ratings, totalMoves)
        // ルール別申告棋力の整形（service_rank テーブル + lishogi 単一値）
        val declaredRankLine = buildDeclaredRankLine(settings)
        return StrengthCardData(
            displayText = estimate.toDisplayString(),
            detailText = AppStrings.strengthDetail(games.size),
            savedService = settings.service,
            savedRatingRaw = settings.ratingRaw,
            savedRatingRule = settings.ratingRule,
            declaredRankLine = declaredRankLine,
        )
    }

    private fun buildDeclaredRankLine(settings: RatingSettings): String? {
        val serviceRanks = settingsRepository.getAllServiceRanks()
        val entries = mutableListOf<String>()
        // ルール別（ウォーズ・棋桜）
        for ((svc, rules) in serviceRanks) {
            val shortName = AppStrings.serviceShortName(svc)
            for ((ruleId, raw) in rules) {
                val rankLabel = ShogiRank.fromRaw(raw)?.toDisplayString() ?: continue
                val ruleLabel = AppStrings.ruleLabel(svc, ruleId)
                entries += "$shortName$ruleLabel $rankLabel"
            }
        }
        // lishogi は単一レート（明示的に保存済みの場合のみ表示）
        if (settings.service == "lishogi" && settingsRepository.hasUserSavedRatingSettings()) {
            entries += "lishogi ${settings.ratingRaw}"
        }
        return if (entries.isEmpty()) null
        else AppStrings.strengthDeclaredLine(entries.joinToString(" ／ "))
    }

    /** 総手数から user_side の手数を算出する。先手: ceil(total/2), 後手: floor(total/2)。 */
    private fun userMoveCount(totalMoves: Long, userSide: String): Int {
        val t = totalMoves.toInt()
        return if (userSide == "sente") (t + 1) / 2 else t / 2
    }
}
