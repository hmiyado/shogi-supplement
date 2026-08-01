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
 * ブラウザ上でKIFテキストをパースするための窓口。
 *
 * パッケージを持たないデフォルトパッケージに置く: Kotlin/JSはpackageの階層をそのまま
 * ネストしたオブジェクトとしてJS側へ公開する仕様のため、内部の実パッケージ
 * (dev.miyado.shogisupplement.kifu)に置くとJS側の呼び出しパスがそれに連動してしまう。
 * デフォルトパッケージに置くことで公開する関数がJSモジュール直下のフラットな名前になり、
 * 内部のパッケージ構成を変えてもJS側の呼び出し方(モジュール名.関数名)が変わらない。
 *
 * `@JsExport`のdata class制約（プロパティ公開範囲・null許容の相互運用に制約がある）を避けるため、
 * 結果はJSON文字列で返す。呼び出し側は`JSON.parse`してから使う。パース処理自体は
 * アプリ本体(Kotlin)と同一実装（[KifParser]）を使う（JS側での再実装はしない）。
 *
 * 返却JSONの形（プロパティは常に揃っているが、対応する値が無い場合は null）:
 * - 成功時: `{"ok":true,"moves":[...],"displayMoves":[...],"sente":string|null,
 *   "gote":string|null,"endReason":string|null,"winner":"sente"|"gote"|null}`
 * - 失敗時: `{"ok":false,"error":{"message":string}}`
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
