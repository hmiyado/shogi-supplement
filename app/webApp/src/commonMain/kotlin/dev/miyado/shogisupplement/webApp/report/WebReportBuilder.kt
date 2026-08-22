package dev.miyado.shogisupplement.webApp.report

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.EngineMatchRate
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.kifu.KifuDecomposer
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.pipeline.PositionEval
import dev.miyado.shogisupplement.pipeline.ReportPipeline
import dev.miyado.shogisupplement.strength.StrengthEstimator
import dev.miyado.shogisupplement.strength.toDisplayString
import dev.miyado.shogisupplement.text.AppStrings
import kotlin.math.roundToInt

/** ReportScreen へそのまま渡せる表示状態一式。DBを持たないWeb版はセッション内メモリのみで保持する。 */
data class WebReportData(
    val game: GameRecord,
    val reports: List<BlunderRecord>,
    val positionEvals: List<PositionEvalRow>,
    val strengthText: String?,
    val matchRateText: String?,
    val blunderRateText: String?,
)

/** KIF/SFENと解析結果をReportScreenのドメイン型へ変換する。DBへ保存せず、nullのuserSideでは全体を対象にする。 @param userSide ユーザーの先後。 */
fun buildWebReport(
    fileName: String,
    moves: List<String>,
    headers: Map<String, String>,
    evals: List<PositionEval>,
    endReason: String?,
    winner: String?,
    kifText: String?,
    coef: CoefficientTable,
    userSide: String? = null,
): WebReportData {
    val sides = if (userSide != null) setOf(userSide) else setOf("sente", "gote")
    val analysisResult = ReportPipeline.analyze(moves = moves, evals = evals, sides = sides, coef = coef)

    val sfenAtPly = buildSfenSequence(moves)
    val reports = analysisResult.reports.mapIndexed { index, report ->
        toBlunderRecord(id = index.toLong(), gameId = 0L, report = report, sfenAtPly = sfenAtPly, moves = moves)
    }

    val positionEvalRows = evals.mapIndexedNotNull { t, posEval -> toPositionEvalRow(t, posEval) }

    val game = GameRecord(
        id = 0L,
        fileName = fileName,
        contentHash = "",
        moveCount = moves.size.toLong(),
        senteName = headers["先手"],
        goteName = headers["後手"],
        // Web版はDB永続化を持たず「解析した時刻」という概念がないため0（日時不明の
        // センチネル）を渡す。
        analyzedAt = 0L,
        rating = analysisResult.estimatedRating.toLong(),
        ratingSampleMoves = analysisResult.ratingSampleMoves.toLong(),
        coefVersion = coef.version,
        kifText = kifText,
        movesUsi = moves,
        userSide = userSide,
        sourcePlace = KifuDecomposer.classifySource(kifText ?: "", headers["場所"]).wireValue,
        gameWinner = winner,
        endReason = endReason,
    )

    val matchRateResult = EngineMatchRate.compute(moves, positionEvalRows, userSide)
    val matchRateText = matchRateResult?.let {
        AppStrings.matchRateValue((it.rate * 100).roundToInt(), it.matched, it.sampleMoves)
    }
    val blunderRateText = matchRateResult?.takeIf { it.sampleMoves > 0 }?.let {
        val pct = (reports.size.toDouble() / it.sampleMoves * 100).roundToInt()
        AppStrings.blunderRateValue(pct, reports.size, it.sampleMoves)
    }
    val strengthText = userSide?.let {
        val userMoves = userMoveCount(moves.size, it)
        if (userMoves == 0) {
            null
        } else {
            StrengthEstimator.aggregate(listOf(analysisResult.estimatedRating), userMoves).toDisplayString()
        }
    }

    return WebReportData(
        game = game,
        reports = reports,
        positionEvals = positionEvalRows,
        strengthText = strengthText,
        matchRateText = matchRateText,
        blunderRateText = blunderRateText,
    )
}

private fun userMoveCount(totalMoves: Int, userSide: String): Int =
    if (userSide == "sente") (totalMoves + 1) / 2 else totalMoves / 2

private fun toPositionEvalRow(ply: Int, posEval: PositionEval): PositionEvalRow? {
    val score = posEval.score ?: return null
    val flip = ply % 2 == 1
    val bestUsi = posEval.pv.firstOrNull()
    val (secondScoreCp, secondMateIn) = when (val pv2Score = posEval.pv2Score) {
        null -> null to null
        is Score.Cp -> BlunderJudge.toCp(pv2Score).let { if (flip) -it else it } to null
        is Score.Mate -> null to pv2Score.plies.let { if (flip) -it else it }
    }
    return when (score) {
        is Score.Cp -> PositionEvalRow(
            ply = ply,
            scoreCp = BlunderJudge.toCp(score).let { if (flip) -it else it },
            mateIn = null,
            bestUsi = bestUsi,
            secondScoreCp = secondScoreCp,
            secondMateIn = secondMateIn,
            secondUsi = posEval.pv2MoveUsi,
        )
        is Score.Mate -> PositionEvalRow(
            ply = ply,
            scoreCp = null,
            mateIn = score.plies.let { if (flip) -it else it },
            bestUsi = bestUsi,
            secondScoreCp = secondScoreCp,
            secondMateIn = secondMateIn,
            secondUsi = posEval.pv2MoveUsi,
        )
    }
}

private fun toBlunderRecord(
    id: Long,
    gameId: Long,
    report: BlunderReport,
    sfenAtPly: List<String>,
    moves: List<String>,
): BlunderRecord {
    val sfenBefore = sfenAtPly.getOrElse(report.ply - 1) {
        "startpos moves " + moves.take(report.ply - 1).joinToString(" ")
    }
    return BlunderRecord(
        id = id,
        gameId = gameId,
        ply = report.ply.toLong(),
        side = report.side,
        moveUsi = report.moveUsi,
        bestUsi = report.bestUsi,
        lossWp = report.lossWp,
        sfenBefore = sfenBefore,
        category = report.classification.category,
        diffMaterial = report.classification.diffMaterial.toLong(),
        punishChecks = report.classification.punishChecks.toLong(),
        tookMovedPiece = report.classification.tookMovedPiece,
        missedMateIn = report.classification.missedMateIn?.toLong(),
        verdict = report.judgement.verdict,
        note = report.judgement.note,
        problemType = report.judgement.problem,
        priority = report.judgement.priority,
        bestPv = report.bestPv,
        punishPv = report.punishPv,
        cpBefore = report.cpBefore?.toLong(),
        cpAfter = report.cpAfter?.toLong(),
        secondUsi = report.secondUsi,
        secondCp = report.secondCp?.toLong(),
    )
}

/** sfenAtPly[i] = i 手目を指す直前の局面（i=0 が初期局面）。 */
private fun buildSfenSequence(moves: List<String>): List<String> {
    val board = ShogiBoard()
    val result = ArrayList<String>(moves.size + 1)
    result.add(board.toSfen())
    for (usiStr in moves) {
        try {
            board.push(ShogiMove.fromUsi(usiStr))
            result.add(board.toSfen())
        } catch (_: Exception) {
            break
        }
    }
    return result
}
