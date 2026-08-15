package dev.miyado.shogisupplement.engine

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/generate-kento-manifest.sh の出力検証。配信側のシェルスクリプトとアプリ側の
 * 期待をKotlin定数で共有できないため、スクリプトを実際に走らせて突き合わせる。
 */
class KentoManifestGeneratorTest {

    private val script = File("../../docs/generate-kento-manifest.sh")

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun run(docsDir: File, out: File): String {
        val process = ProcessBuilder("bash", script.absolutePath, docsDir.absolutePath, out.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "スクリプトが失敗した: $output")
        return out.readText()
    }

    private fun fixture(): File {
        val docs = File.createTempFile("kento-manifest", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        File(docs, "kento").mkdirs()
        File(docs, "kento-assets/yo-1234").mkdirs()
        File(docs, "kento/study-worker.js").writeText("worker")
        File(docs, "kento/wasm-analysis-host.html").writeText("host")
        File(docs, "kento-assets/VERSION").writeText("yo-1234")
        File(docs, "kento-assets/yo-1234/nn.bin").writeText("eval")
        return docs
    }

    @Test
    fun `配下の全ファイルをdocsからの相対パスとハッシュで載せる`() {
        val docs = fixture()
        val parsed = KentoAssetManifest.parse(run(docs, File(docs, "kento-assets/MANIFEST.json")))
        assertNotNull(parsed)
        assertEquals(
            setOf(
                "kento/study-worker.js",
                "kento/wasm-analysis-host.html",
                "kento-assets/VERSION",
                "kento-assets/yo-1234/nn.bin",
            ),
            parsed.hashes.keys,
        )
        assertEquals(sha256("worker"), parsed.expected("kento/study-worker.js"))
        assertEquals(sha256("eval"), parsed.expected("kento-assets/yo-1234/nn.bin"))
    }

    @Test
    fun `マニフェスト自身は載せない`() {
        val docs = fixture()
        val out = File(docs, "kento-assets/MANIFEST.json")
        out.writeText("前回の生成物")
        val parsed = KentoAssetManifest.parse(run(docs, out))
        assertNotNull(parsed)
        assertFalse(parsed.hashes.containsKey("kento-assets/MANIFEST.json"))
    }

    @Test
    fun `アプリが取得するdocs-kentoのファイルはすべて載る`() {
        val docs = File("../../docs")
        val out = File.createTempFile("kento-manifest-real", ".json")
        val parsed = KentoAssetManifest.parse(run(docs, out))
        assertNotNull(parsed)
        val swift = File("../iosApp/iosApp/KentoAssetCache.swift").readText()
        val body = Regex("""let kentoFiles = \[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(swift)!!.groupValues[1]
        for (name in Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }) {
            assertTrue(
                parsed.hashes.containsKey("kento/$name"),
                "kento/$name がマニフェストに載らない（アプリが検証できずWASM解析が不成立になる）",
            )
        }
    }
}
