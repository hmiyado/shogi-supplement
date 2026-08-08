package dev.miyado.shogisupplement.ui

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * res/raw と :ui composeResources に複製されている aboutlibraries.json の
 * バイト一致検証。依存更新でJSONを再生成したとき片側だけ更新される事故を機械ブロックする
 * （2つの配置はAndroid=res/raw同期読み込み・iOS/wasm=composeResourcesという
 * プラットフォーム都合の複製で、参照共有ができない）。
 */
class AboutLibrariesResourceParityTest {

    @Test
    fun `res-rawとcomposeResourcesのaboutlibrariesはバイト一致する`() {
        val androidCopy = File("src/main/res/raw/aboutlibraries.json")
        val composeCopy = File("../ui/src/commonMain/composeResources/files/aboutlibraries.json")
        assertTrue("res/raw側が見つからない: ${androidCopy.absolutePath}", androidCopy.isFile)
        assertTrue("composeResources側が見つからない: ${composeCopy.absolutePath}", composeCopy.isFile)
        assertArrayEquals(
            "aboutlibraries.jsonの複製が不一致。依存更新時は両方を同じ生成物で更新すること",
            androidCopy.readBytes(),
            composeCopy.readBytes(),
        )
    }
}
