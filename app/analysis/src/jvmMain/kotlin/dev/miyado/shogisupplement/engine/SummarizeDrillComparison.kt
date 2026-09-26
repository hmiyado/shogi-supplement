package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.classify.BlunderClassifier
import dev.miyado.shogisupplement.pipeline.PositionEval
import java.io.File
import kotlin.math.abs
import kotlinx.serialization.json.*

/** 実測済み全局面を本番の悪手判定・分類へ通し、条件間の差を集計する。 */
fun main(args: Array<String>) {
    summarizeDrillComparison(args)
}

/** 完了したペアだけを集計し、未完了の測定は失敗させる。 */
fun summarizeDrillComparison(args: Array<String>) {
    require(args.size == 2) { "measurement-directory output.json" }
    val directory = File(args[0])
    val manifest = Json.parseToJsonElement(File(directory, "manifest.json").readText()).jsonObject
    val nodes = manifest.getValue("nodes").jsonArray.map { it.jsonPrimitive.content }
    require(nodes.size == 2 && nodes.first() == "400000")
    val files = directory.listFiles()!!.filter { it.extension == "json" && it.name != "manifest.json" }
    require(files.size == manifest.getValue("count").jsonPrimitive.int) { "Measurement is incomplete" }
    fun score(value: JsonElement): Score {
        val obj = value.jsonObject
        return obj["cp"]?.jsonPrimitive?.intOrNull?.let { Score.Cp(it) }
            ?: Score.Mate(obj.getValue("mate").jsonPrimitive.int)
    }
    fun evaluation(search: JsonObject): PositionEval {
        val pv = search.getValue("pvs").jsonObject.getValue("1").jsonObject
        return PositionEval(score(pv.getValue("score")), pv.getValue("pv").jsonArray.map { it.jsonPrimitive.content })
    }
    fun distribution(values: List<Double>): JsonObject = buildJsonObject {
        put("count", values.size)
        if (values.isNotEmpty()) {
            val sorted = values.sorted()
            fun percentile(p: Double): Double {
                val index = p * (sorted.size - 1)
                val lower = index.toInt()
                return sorted[lower] + (sorted[minOf(lower + 1, sorted.lastIndex)] - sorted[lower]) * (index - lower)
            }
            put("mean", values.average())
            put("p05", percentile(.05))
            put("p50", percentile(.5))
            put("p95", percentile(.95))
            put("min", sorted.first())
            put("max", sorted.last())
        }
    }
    val rows = files.sortedBy { it.name }.map { file ->
        val result = Json.parseToJsonElement(file.readText()).jsonObject
        val position = result.getValue("position").jsonObject
        val move = position.getValue("move").jsonPrimitive.content
        val sfen = position.getValue("sfen_before").jsonPrimitive.content
        val board = ShogiBoard()
        position.getValue("moves_before").jsonArray.forEach { board.push(ShogiMove.fromUsi(it.jsonPrimitive.content)) }
        require(board.toSfen() == sfen) { "History/SFEN mismatch: ${file.name}" }
        board.push(ShogiMove.fromUsi(move))
        require(board.toSfen() == position.getValue("sfen_after").jsonPrimitive.content)
        val conditions = nodes.map { budget ->
            val pair = result.getValue("searches").jsonObject.getValue(budget).jsonObject
            val beforeSearch = pair.getValue("before").jsonObject
            val afterSearch = pair.getValue("after").jsonObject
            val before = evaluation(beforeSearch)
            val after = evaluation(afterSearch)
            val judgement = BlunderJudge.judge(before.score!!, after.score!!, move, before.pv.firstOrNull())
            val category = if (judgement.isBlunder) BlunderClassifier.classify(
                ShogiBoard.fromSfen(sfen), ShogiMove.fromUsi(move), before, after,
            ).category else null
            val bounded = listOf(beforeSearch, afterSearch).any {
                it.getValue("pvs").jsonObject.getValue("1").jsonObject.getValue("bounded").jsonPrimitive.boolean
            }
            buildJsonObject {
                put("nodes", budget)
                put("bestmove", beforeSearch.getValue("bestmove"))
                put("pv_first", before.pv.firstOrNull())
                put("cp_before", BlunderJudge.toCp(before.score))
                put("cp_after", BlunderJudge.toCp(after.score))
                put("cp_only", before.score is Score.Cp && after.score is Score.Cp)
                put("bounded", bounded)
                put("is_blunder", judgement.isBlunder)
                put("type", judgement.type?.name)
                put("category", category)
                put("seconds", beforeSearch.getValue("seconds").jsonPrimitive.double + afterSearch.getValue("seconds").jsonPrimitive.double)
            }
        }
        buildJsonObject {
            put("game_id", position.getValue("game_id"))
            put("ply", position.getValue("ply"))
            put("conditions", JsonArray(conditions))
        }
    }
    fun condition(row: JsonObject, index: Int) = row.getValue("conditions").jsonArray[index].jsonObject
    fun flag(obj: JsonObject, name: String) = obj.getValue(name).jsonPrimitive.boolean
    fun summary(selected: List<JsonObject>): JsonObject = buildJsonObject {
        put("count", selected.size)
        for (key in listOf("bestmove", "pv_first", "is_blunder", "type", "category")) {
            put("${key}_changed", selected.count { condition(it, 0)[key] != condition(it, 1)[key] })
        }
        put("baseline_not_blunder", selected.count { !flag(condition(it, 0), "is_blunder") })
        put("blunder_to_not_blunder", selected.count { flag(condition(it, 0), "is_blunder") && !flag(condition(it, 1), "is_blunder") })
        val numeric = selected.filter { flag(condition(it, 0), "cp_only") && flag(condition(it, 1), "cp_only") }
        for (key in listOf("cp_before", "cp_after")) {
            val differences = numeric.map { condition(it, 1).getValue(key).jsonPrimitive.double - condition(it, 0).getValue(key).jsonPrimitive.double }
            put("${key}_delta", distribution(differences))
            put("${key}_absolute_delta", distribution(differences.map(::abs)))
        }
    }
    val output = buildJsonObject {
        put("manifest", manifest)
        put("all_latest_values", summary(rows))
        put("unbounded_latest_values_only", summary(rows.filter { !flag(condition(it, 0), "bounded") && !flag(condition(it, 1), "bounded") }))
        put("pair_seconds", buildJsonObject {
            nodes.forEachIndexed { index, budget -> put(budget, distribution(rows.map { condition(it, index).getValue("seconds").jsonPrimitive.double })) }
        })
        put("rows", JsonArray(rows))
    }
    File(args[1]).writeText(Json.encodeToString(JsonObject.serializer(), output))
    println("Summarized ${rows.size} complete paired positions; bound values reported separately")
}
