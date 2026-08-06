package dev.miyado.shogisupplement.policy

import kotlin.test.Test
import kotlin.test.assertEquals

/** [resolvePolicyPlatform] のdev行キー選択の単体テスト。 */
class PolicyPlatformKeyTest {

    @Test
    fun `Debugビルドはdevサフィックス付きのplatformキーを返す`() {
        assertEquals("android-dev", resolvePolicyPlatform("android", isDebugBuild = true))
        assertEquals("ios-dev", resolvePolicyPlatform("ios", isDebugBuild = true))
    }

    @Test
    fun `Releaseビルドは本番のplatformキーをそのまま返す`() {
        assertEquals("android", resolvePolicyPlatform("android", isDebugBuild = false))
        assertEquals("ios", resolvePolicyPlatform("ios", isDebugBuild = false))
    }
}
