package dev.miyado.shogisupplement.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 検討ページ資産のSHA-256マニフェスト（docs/generate-kento-manifest.sh が生成）の解釈。
 *
 * 配信側は資産ディレクトリ配下を全列挙するため、アプリが取得しないファイルも載る。
 * 参照する側は自分が必要とするパスだけを引く。
 */
object KentoAssetManifest {

    /** 解釈済みマニフェスト。[hashes] は docs/ からの相対パス → 小文字16進のSHA-256。 */
    data class Parsed(val hashes: Map<String, String>) {

        /** [path] の期待ハッシュ。載っていなければnull（照合できない＝完全とみなさない）。 */
        fun expected(path: String): String? = hashes[path]
    }

    /** マニフェストJSONを解釈する。壊れている・想定の形でない場合はnull（検証不能）。 */
    fun parse(json: String): Parsed? {
        val files = runCatching {
            Json.parseToJsonElement(json).jsonObject["files"]?.jsonObject
        }.getOrNull() ?: return null

        val hashes = files.mapValues { (_, v) ->
            runCatching { v.jsonPrimitive.content }.getOrNull() ?: return null
        }
        return Parsed(hashes.mapValues { it.value.lowercase() })
    }

    /** 実測ハッシュが期待と一致するか。大文字小文字は無視する（採取側の表記に依存させない）。 */
    fun matches(expected: String?, actual: String): Boolean =
        expected != null && expected.equals(actual, ignoreCase = true)
}
