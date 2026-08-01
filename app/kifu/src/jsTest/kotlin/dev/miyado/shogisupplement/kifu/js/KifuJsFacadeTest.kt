package dev.miyado.shogisupplement.kifu.js

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [parseKifToJson] の往復テスト（nodejsランナーで実行）。 */
class KifuJsFacadeTest {

    @Test
    fun `KIFをパースしてJSONへ往復できる`() {
        val kif = """
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 投了
        """.trimIndent()

        val json = Json.parseToJsonElement(parseKifToJson(kif)).jsonObject
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        assertEquals(
            listOf("7g7f", "3c3d"),
            json.getValue("moves").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("７六歩(77)", "３四歩(33)"),
            json.getValue("displayMoves").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("太郎", json.getValue("sente").jsonPrimitive.content)
        assertEquals("花子", json.getValue("gote").jsonPrimitive.content)
        assertEquals("投了", json.getValue("endReason").jsonPrimitive.content)
        // 2手(偶数)指された後の3手目で投了 → 次の手番(先手)が投了 → 後手の勝ち
        assertEquals("gote", json.getValue("winner").jsonPrimitive.content)
    }

    @Test
    fun `平手以外はok falseとエラーメッセージを返す`() {
        val kif = """
            手合割：二枚落ち
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()

        val json = Json.parseToJsonElement(parseKifToJson(kif)).jsonObject
        assertEquals(false, json.getValue("ok").jsonPrimitive.boolean)
        assertTrue(
            json.getValue("error").jsonObject.getValue("message").jsonPrimitive.content.contains("平手"),
        )
    }
}
