package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository

/** 取り込みと、その後どこへ進むかの判断。 */
class GameImportFlow(private val gameRepository: GameRepository) {

    sealed interface Next {

        /** 未解析の新規棋譜。 */
        data class Analyze(val game: GameRecord) : Next

        /** 解析済み、または保存直後の棋譜を読み出せなかった場合。 */
        data class OpenReport(val gameId: Long) : Next

        data class Failed(val message: String) : Next
    }

    /**
     * 同じ内容の棋譜が既にあれば解析し直さない。固定ノード数の解析結果は同じ棋譜なら変わらず、
     * 待ち時間だけが増えるため。
     */
    fun import(
        kifContent: String,
        fileName: String,
        userSide: String?,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
    ): Next = when (
        val outcome = GameImporter(gameRepository).importGame(
            kifContent = kifContent,
            fileName = fileName,
            userSide = userSide,
            ratingService = ratingService,
            ratingRaw = ratingRaw,
            ratingRule = ratingRule,
        )
    ) {
        is GameImporter.Outcome.Failed -> Next.Failed(outcome.message)
        is GameImporter.Outcome.Imported -> {
            val game = if (outcome.alreadyExisted) null else gameRepository.getGameById(outcome.gameId)
            if (game != null) Next.Analyze(game) else Next.OpenReport(outcome.gameId)
        }
    }
}
