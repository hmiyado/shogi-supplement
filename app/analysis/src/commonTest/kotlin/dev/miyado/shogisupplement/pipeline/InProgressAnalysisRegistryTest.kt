package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InProgressAnalysisRegistryTest {

    private fun cp(value: Int): List<PvInfo> = listOf(PvInfo(multipv = 1, score = Score.Cp(value), pv = emptyList(), nodes = 0L))

    private val moves = listOf("7g7f", "3c3d", "8h2b")

    @Test
    fun `startで登録した直後は0手目未反映の初期状態`() {
        val registry = InProgressAnalysisRegistry()
        registry.start("hash1", "game1.kif", moves, userSide = "sente")

        val session = registry.snapshot("hash1")
        assertEquals("hash1", session?.id)
        assertEquals("game1.kif", session?.fileName)
        assertEquals("sente", session?.userSide)
        assertEquals(0, session?.progressive?.confirmedThrough)
        assertTrue(registry.sessions.value.containsKey("hash1"))
    }

    @Test
    fun `updatePositionはProgressiveReportStateのwatermarkをそのまま畳み込む`() {
        val registry = InProgressAnalysisRegistry()
        registry.start("hash1", "game1.kif", moves, userSide = null)

        registry.updatePosition("hash1", 0, cp(10))
        registry.updatePosition("hash1", 1, cp(20))

        val session = registry.snapshot("hash1")
        assertEquals(2, session?.progressive?.confirmedThrough)
    }

    @Test
    fun `未登録idへのupdatePositionは無視される`() {
        val registry = InProgressAnalysisRegistry()
        registry.updatePosition("unknown", 0, cp(10))
        assertNull(registry.snapshot("unknown"))
        assertTrue(registry.sessions.value.isEmpty())
    }

    @Test
    fun `finishでセッションが一覧・スナップショット双方から消える`() {
        val registry = InProgressAnalysisRegistry()
        registry.start("hash1", "game1.kif", moves, userSide = null)
        registry.finish("hash1")

        assertNull(registry.snapshot("hash1"))
        assertTrue(registry.sessions.value.isEmpty())
    }

    @Test
    fun `startは同じidの既存セッションを初期状態で上書きする（フォアグラウンド復帰の再送想定）`() {
        val registry = InProgressAnalysisRegistry()
        registry.start("hash1", "game1.kif", moves, userSide = null)
        registry.updatePosition("hash1", 0, cp(10))
        assertEquals(1, registry.snapshot("hash1")?.progressive?.confirmedThrough)

        registry.start("hash1", "game1.kif", moves, userSide = null)
        assertEquals(0, registry.snapshot("hash1")?.progressive?.confirmedThrough)
    }

    @Test
    fun `複数セッションを独立して管理できる`() {
        val registry = InProgressAnalysisRegistry()
        registry.start("hash1", "game1.kif", moves, userSide = "sente")
        registry.start("hash2", "game2.kif", moves, userSide = "gote")

        registry.updatePosition("hash1", 0, cp(10))

        assertEquals(1, registry.snapshot("hash1")?.progressive?.confirmedThrough)
        assertEquals(0, registry.snapshot("hash2")?.progressive?.confirmedThrough)
        assertEquals(2, registry.sessions.value.size)

        registry.finish("hash1")
        assertEquals(1, registry.sessions.value.size)
        assertNull(registry.snapshot("hash1"))
    }
}
