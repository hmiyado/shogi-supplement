package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.*

class DrillComparisonSummaryTest {
    @Test
    fun `uses opponent perspective after move and separates bounded pairs`() {
        val root = Files.createTempDirectory("drill-summary-test").toFile()
        try {
            val input = root.resolve("input").apply { mkdir() }
            input.resolve("manifest.json").writeText("""{"nodes":[400000,4000000],"count":1}""")
            val board = ShogiBoard()
            val before = board.toSfen()
            board.push(ShogiMove.fromUsi("7g7f"))
            fun search(cp: Int, move: String, bounded: Boolean = false) = buildJsonObject {
                put("bestmove", move)
                put("seconds", 1.0)
                put("pvs", buildJsonObject { put("1", buildJsonObject {
                    put("score", buildJsonObject { put("cp", cp) })
                    put("pv", JsonArray(listOf(JsonPrimitive(move))))
                    put("bounded", bounded)
                }) })
            }
            val record = buildJsonObject {
                put("position", buildJsonObject {
                    put("game_id", "fixture")
                    put("ply", 1)
                    put("move", "7g7f")
                    put("sfen_before", before)
                    put("sfen_after", board.toSfen())
                    put("moves_before", JsonArray(emptyList()))
                })
                put("searches", buildJsonObject {
                    put("400000", buildJsonObject {
                        put("before", search(0, "2g2f"))
                        put("after", search(600, "3c3d"))
                    })
                    put("4000000", buildJsonObject {
                        put("before", search(0, "2g2f", true))
                        put("after", search(10, "3c3d"))
                    })
                })
            }
            input.resolve("fixture-1.json").writeText(record.toString())
            val output = root.resolve("summary.json")
            summarizeDrillComparison(arrayOf(input.path, output.path))
            val result = Json.parseToJsonElement(output.readText()).jsonObject
            val all = result.getValue("all_latest_values").jsonObject
            assertEquals(1, all.getValue("blunder_to_not_blunder").jsonPrimitive.int)
            assertEquals(-590.0, all.getValue("cp_after_delta").jsonObject.getValue("mean").jsonPrimitive.double)
            assertEquals(0, result.getValue("unbounded_latest_values_only").jsonObject.getValue("count").jsonPrimitive.int)
            input.resolve("manifest.json").writeText("""{"nodes":[400000,4000000],"count":2}""")
            assertFailsWith<IllegalArgumentException> { summarizeDrillComparison(arrayOf(input.path, output.path)) }
        } finally {
            root.deleteRecursively()
        }
    }
}
