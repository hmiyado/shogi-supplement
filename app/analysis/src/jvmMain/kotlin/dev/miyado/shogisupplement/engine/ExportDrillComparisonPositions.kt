package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.PositionEval
import dev.miyado.shogisupplement.pipeline.ReportPipeline
import java.io.File
import kotlinx.serialization.json.*

/** 既存解析を本番の出題判定へ通し、条件比較用の局面をJSONで出力する。 */
fun main(args: Array<String>) {
    require(args.size == 4) { "sample.json eval-directory coefficients.json output.json" }
    val samples = Json.parseToJsonElement(File(args[0]).readText()).jsonArray
    val coefficients = CoefficientTable.fromJson(File(args[2]).readText())
    fun score(value: JsonElement?): Score? {
        val obj = value as? JsonObject ?: return null
        return obj["cp"]?.jsonPrimitive?.intOrNull?.let { Score.Cp(it) }
            ?: obj["mate"]?.jsonPrimitive?.intOrNull?.let { Score.Mate(it) }
    }
    val positions = buildJsonArray {
        for (sample in samples) {
            val game = sample.jsonObject.getValue("game").jsonObject
            val id = game.getValue("id").jsonPrimitive.content
            val moves = game.getValue("moves").jsonPrimitive.content.split(" ").filter { it.isNotBlank() }
            val cached = Json.parseToJsonElement(File(args[1], "$id.json").readText()).jsonObject
            require(cached.getValue("engine").jsonPrimitive.content == "hao")
            val evals = cached.getValue("evals").jsonArray.map { entry ->
                val obj = entry.jsonObject
                val second = obj["pvs"]?.jsonObject?.get("2")?.jsonObject
                PositionEval(
                    score(obj["score"]),
                    obj.getValue("pv").jsonArray.map { it.jsonPrimitive.content },
                    score(second?.get("score")),
                    second?.get("pv")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content,
                )
            }
            val targets = listOf("sente", "gote").flatMap { side ->
                ReportPipeline.analyze(moves, evals, sides = setOf(side), coef = coefficients).reports
            }
                .filter { it.judgement.kind != VerdictKind.SKIP }.associateBy { it.ply }
            val board = ShogiBoard()
            moves.forEachIndexed { index, move ->
                val before = board.toSfen()
                board.push(ShogiMove.fromUsi(move))
                targets[index + 1]?.let { report ->
                    add(buildJsonObject {
                        put("game_id", id)
                        put("ply", index + 1)
                        put("stratum", sample.jsonObject.getValue("stratum"))
                        put("sfen_before", before)
                        put("sfen_after", board.toSfen())
                        put("moves_before", JsonArray(moves.take(index).map(::JsonPrimitive)))
                        put("move", move)
                        put("verdict", report.judgement.kind.name)
                        put("side", report.side)
                        put("baseline_best", report.bestUsi)
                        put("baseline_cp_before", report.cpBefore)
                        put("baseline_cp_after", report.cpAfter)
                    })
                }
            }
        }
    }
    File(args[3]).writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), positions))
    println("Exported ${positions.size} drill-eligible positions from ${samples.size} games")
}
