package dev.miyado.shogisupplement.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ForceUpdateJudge.evaluate] の判定ロジックの単体テスト。 */
class ForceUpdateJudgeTest {

    private val androidRow = AppPolicyRow(platform = "android", minBuild = 42, storeUrl = "https://play.example/app", message = null)

    @Test
    fun `build がminBuild未満ならブロック`() {
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 41, rows = listOf(androidRow))
        assertTrue(decision.blocked)
    }

    @Test
    fun `build がminBuildと同値なら非ブロック`() {
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 42, rows = listOf(androidRow))
        assertFalse(decision.blocked)
    }

    @Test
    fun `build がminBuildを上回れば非ブロック`() {
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 100, rows = listOf(androidRow))
        assertFalse(decision.blocked)
    }

    @Test
    fun `自分のプラットフォーム行が無ければ非ブロック（fail-open側）`() {
        val decision = ForceUpdateJudge.evaluate(
            "ios",
            currentBuild = 1,
            rows = listOf(androidRow),
        )
        assertFalse(decision.blocked)
    }

    @Test
    fun `min_build未設定行（初期値）は非ブロック`() {
        val row = androidRow.copy(minBuild = null)
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(row))
        assertFalse(decision.blocked)
    }

    @Test
    fun `store_urlが空文字ならnull（ボタンを出さない判断は呼び出し側に委ねる）`() {
        val row = androidRow.copy(storeUrl = "")
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(row))
        assertNull(decision.storeUrl)
    }

    @Test
    fun `store_urlが設定されていればそのまま返す`() {
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(androidRow))
        assertEquals("https://play.example/app", decision.storeUrl)
    }

    @Test
    fun `messageが無ければnull`() {
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(androidRow))
        assertNull(decision.message)
    }

    @Test
    fun `プラットフォーム行のmessageのみでも表示される`() {
        val row = androidRow.copy(message = "既知の不具合について")
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(row))
        assertEquals("既知の不具合について", decision.message)
    }

    @Test
    fun `プラットフォーム行とcommon行のmessageは改行で合成される`() {
        val row = androidRow.copy(message = "Android向けのお知らせ")
        val common = AppPolicyRow(platform = "common", minBuild = null, storeUrl = null, message = "メンテナンス予定")
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(row, common))
        assertEquals("Android向けのお知らせ\nメンテナンス予定", decision.message)
    }

    @Test
    fun `common行のmessageが空白のみなら無視される`() {
        val common = AppPolicyRow(platform = "common", minBuild = null, storeUrl = null, message = "   ")
        val decision = ForceUpdateJudge.evaluate("android", currentBuild = 1, rows = listOf(androidRow, common))
        assertNull(decision.message)
    }
}
