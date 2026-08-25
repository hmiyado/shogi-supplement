package dev.miyado.shogisupplement.opening

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 資料（docs/opening）が定義データと一致していることを保証する。
 *
 * 判定条件を変えたのに資料が古いままだと、読んだ人が実際とは違う条件を信じる。
 * 差分が出たら `./gradlew generateOpeningDocs` で生成し直す。
 */
class OpeningDocsUpToDateTest {

    @Test
    fun 生成した資料と保存されている資料が一致する() {
        val committed = File("../docs/opening")
        assertTrue(committed.isDirectory, "資料のディレクトリが無い: ${committed.absolutePath}")

        val generated = createTempDirectory("opening-docs").toFile()
        main(arrayOf(generated.absolutePath))

        val expected = generated.listFiles().orEmpty().associate { it.name to it.readText() }
        val actual = committed.listFiles().orEmpty().associate { it.name to it.readText() }
        assertEquals(
            expected.keys.sorted(),
            actual.keys.sorted(),
            "ファイルの顔ぶれが違う。generateOpeningDocs を実行する",
        )
        expected.forEach { (name, text) ->
            assertEquals(text, actual[name], "$name の内容が古い。generateOpeningDocs を実行する")
        }
    }
}
