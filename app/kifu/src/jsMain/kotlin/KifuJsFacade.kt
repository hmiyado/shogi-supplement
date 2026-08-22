@file:OptIn(ExperimentalJsExport::class)

import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.KifuParseException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * ブラウザ向けKIFパースの窓口。デフォルトパッケージで公開名を平坦に保つ。
 * 結果はdata classの相互運用制約を避けるためJSON文字列で返し、パースはKifParserへ委ねる。
 * 成功時はok、手列、対局者、終局情報を返し、失敗時はerror.messageを返す。
 */
@JsExport
fun parseKifToJson(text: String): String {
    val json = try {
        val game = KifParser().parse(text)
        buildJsonObject {
            put("ok", true)
            put("moves", buildJsonArray { game.moves.forEach { add(JsonPrimitive(it)) } })
            put(
                "displayMoves",
                buildJsonArray { game.displayMoves.forEach { add(JsonPrimitive(it)) } },
            )
            put("sente", game.senteName?.let { JsonPrimitive(it) } ?: JsonNull)
            put("gote", game.goteName?.let { JsonPrimitive(it) } ?: JsonNull)
            put("endReason", game.endReason?.let { JsonPrimitive(it) } ?: JsonNull)
            put("winner", game.winner?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    } catch (e: KifuParseException) {
        buildJsonObject {
            put("ok", false)
            putJsonObject("error") {
                put("message", e.message ?: "KIFの解析に失敗しました")
            }
        }
    }
    return json.toString()
}
