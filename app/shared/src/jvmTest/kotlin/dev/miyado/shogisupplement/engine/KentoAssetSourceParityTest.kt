package dev.miyado.shogisupplement.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KentoAssetCache.swift のダウンロード対象一覧と、配信側（docs/）の実体・
 * 配置スクリプトの対応検証。SwiftとシェルスクリプトはKotlin定数を参照共有
 * できないため、両者のリテラルをテストで突き合わせて乖離を機械ブロックする。
 *
 * - kentoFiles: docs/kento/ に同名ファイルが実在すること（配信元から消えた・
 *   改名されたファイルをアプリが取りにいき404でWASMバイナリ一式が不成立になる事故の防止）
 * - engineFiles: docs/copy-kento-assets.sh がエンジンビルド成果物からコピーする
 *   ファイル集合と一致すること（VERSIONはバージョン判定用マーカーとして別扱い）
 */
class KentoAssetSourceParityTest {

    private val swiftSource = File("../iosApp/iosApp/KentoAssetCache.swift")
    private val copyScript = File("../../docs/copy-kento-assets.sh")
    private val kentoDir = File("../../docs/kento")

    private fun swiftList(name: String): List<String> {
        val text = swiftSource.readText()
        val body = Regex("""let $name = \[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)
            ?: error("KentoAssetCache.swift に $name の配列リテラルが見つからない")
        return Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `kentoFilesの各ファイルはdocs-kentoに実在する`() {
        val kentoFiles = swiftList("kentoFiles")
        assertTrue(kentoFiles.isNotEmpty())
        for (f in kentoFiles) {
            assertTrue(File(kentoDir, f).isFile, "docs/kento/$f が存在しない（改名・削除ならSwift側の一覧も直すこと）")
        }
    }

    @Test
    fun `engineFilesはコピースクリプトのエンジン成果物一覧と一致する`() {
        val engineFiles = swiftList("engineFiles").toSet()
        val scriptRefs = Regex("""\${'$'}SRC_OUT_BROWSER"?/([A-Za-z0-9._-]+)""")
            .findAll(copyScript.readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(engineFiles + "VERSION", scriptRefs)
    }
}
