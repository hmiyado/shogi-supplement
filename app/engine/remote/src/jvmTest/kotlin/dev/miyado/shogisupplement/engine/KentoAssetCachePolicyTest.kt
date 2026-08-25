package dev.miyado.shogisupplement.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [KentoAssetCachePolicy] の単体テスト。 */
class KentoAssetCachePolicyTest {

    @Test
    fun `ローカルが最新版かつ完全ならUseLocal`() {
        val decision = KentoAssetCachePolicy.decide(
            remoteVersion = "v1",
            local = KentoAssetCachePolicy.LocalState(version = "v1", isComplete = true),
        )
        assertEquals(KentoAssetCachePolicy.Decision.UseLocal("v1"), decision)
    }

    @Test
    fun `ローカルが存在しないならFetch`() {
        val decision = KentoAssetCachePolicy.decide(
            remoteVersion = "v1",
            local = KentoAssetCachePolicy.LocalState(version = null, isComplete = false),
        )
        assertEquals(KentoAssetCachePolicy.Decision.Fetch("v1"), decision)
    }

    @Test
    fun `バージョンが変わっていればローカルが完全でもFetch`() {
        val decision = KentoAssetCachePolicy.decide(
            remoteVersion = "v2",
            local = KentoAssetCachePolicy.LocalState(version = "v1", isComplete = true),
        )
        assertEquals(KentoAssetCachePolicy.Decision.Fetch("v2"), decision)
    }

    @Test
    fun `バージョンが一致してもローカルが不完全ならFetch(前回ダウンロード中断)`() {
        val decision = KentoAssetCachePolicy.decide(
            remoteVersion = "v1",
            local = KentoAssetCachePolicy.LocalState(version = "v1", isComplete = false),
        )
        assertEquals(KentoAssetCachePolicy.Decision.Fetch("v1"), decision)
    }

    @Test
    fun `Content-Lengthとバイト数が一致すれば完全`() {
        assertTrue(KentoAssetCachePolicy.isFileComplete(declaredContentLength = 100L, actualBytes = 100L))
    }

    @Test
    fun `Content-Lengthとバイト数が食い違えば不完全(中断ダウンロード)`() {
        assertFalse(KentoAssetCachePolicy.isFileComplete(declaredContentLength = 100L, actualBytes = 42L))
    }

    @Test
    fun `Content-Lengthが取得できない場合は1バイト以上あれば完全とみなす`() {
        assertTrue(KentoAssetCachePolicy.isFileComplete(declaredContentLength = null, actualBytes = 1L))
        assertFalse(KentoAssetCachePolicy.isFileComplete(declaredContentLength = null, actualBytes = 0L))
    }

    @Test
    fun `全ファイルが完全なときだけバージョン全体が完全`() {
        assertTrue(KentoAssetCachePolicy.isVersionComplete(listOf(true, true, true)))
        assertFalse(KentoAssetCachePolicy.isVersionComplete(listOf(true, false, true)))
    }

    @Test
    fun `対象ファイルが1件もなければ完全とはみなさない(呼び出し漏れの誤判定防止)`() {
        assertFalse(KentoAssetCachePolicy.isVersionComplete(emptyList()))
    }
}
