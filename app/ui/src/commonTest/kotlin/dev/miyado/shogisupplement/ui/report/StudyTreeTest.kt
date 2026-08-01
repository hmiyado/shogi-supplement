package dev.miyado.shogisupplement.ui.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyTreeTest {

    @Test
    fun `空の木でmovesは空pathになる`() {
        val tree = StudyTree()
        assertEquals(emptyList(), tree.pathForMoves(listOf("7g7f")))
        assertEquals(StudyEvalState.None, tree.evalStateAt(listOf("7g7f")))
    }

    @Test
    fun `1手指すとノードが追加されmovesから辿れる`() {
        val tree = StudyTree().withMovePlayed(emptyList(), "7g7f", newId = 1L)
        assertEquals(listOf(0), tree.pathForMoves(listOf("7g7f")))
        assertEquals(1, tree.rootChildren.size)
        assertEquals("7g7f", tree.rootChildren[0].moveUsi)
    }

    @Test
    fun `同じ手を指し直しても兄弟は増えない(再利用)`() {
        var tree = StudyTree().withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 2L)
        assertEquals(1, tree.rootChildren.size, "同じ手なら既存ノードを再利用し重複しない")
        assertEquals(1L, tree.rootChildren[0].id, "既存ノードのidが保たれる（新規idは使われない）")
    }

    @Test
    fun `違う手を指し直すと兄弟ノードが増え既存の変化は保持される`() {
        var tree = StudyTree()
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(emptyList(), "2g2f", newId = 2L)
        assertEquals(2, tree.rootChildren.size)
        assertEquals(setOf("7g7f", "2g2f"), tree.rootChildren.map { it.moveUsi }.toSet())
        // 既存の変化（7g7f）は消えていない。
        assertEquals(listOf(0), tree.pathForMoves(listOf("7g7f")))
        assertEquals(listOf(1), tree.pathForMoves(listOf("2g2f")))
    }

    @Test
    fun `深い階層での指し直しでも既存の兄弟系統は保持される`() {
        var tree = StudyTree()
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(listOf("7g7f"), "3c3d", newId = 2L)
        // 1手目まで戻って別の2手目を指す。
        tree = tree.withMovePlayed(listOf("7g7f"), "8c8d", newId = 3L)

        val siblingsAtDepth1 = tree.siblingsAtDepth(listOf("7g7f", "8c8d"), depth = 1)
        assertEquals(setOf("3c3d", "8c8d"), siblingsAtDepth1.map { it.moveUsi }.toSet())
        // 元の変化（7g7f 3c3d）はまだ辿れる。
        assertEquals(listOf(0, 0), tree.pathForMoves(listOf("7g7f", "3c3d")))
        assertEquals(listOf(0, 1), tree.pathForMoves(listOf("7g7f", "8c8d")))
    }

    @Test
    fun `branchFlagsは兄弟が複数あるplyだけtrueになる`() {
        var tree = StudyTree()
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(listOf("7g7f"), "3c3d", newId = 2L)
        // ルート直下は1本道のまま（兄弟なし）。2手目だけ後で分岐を作る。
        tree = tree.withMovePlayed(listOf("7g7f"), "8c8d", newId = 3L)

        val flags = tree.branchFlags(listOf("7g7f", "3c3d"))
        assertEquals(listOf(false, true), flags, "1手目は兄弟なし・2手目は兄弟(3c3d/8c8d)ありのためtrue")
    }

    @Test
    fun `evalStateAtは指定ノードの解析結果を返す`() {
        var tree = StudyTree().withMovePlayed(emptyList(), "7g7f", newId = 1L)
        assertEquals(StudyEvalState.None, tree.evalStateAt(listOf("7g7f")))

        tree = tree.withEvalState(listOf("7g7f"), StudyEvalState.Loading)
        assertEquals(StudyEvalState.Loading, tree.evalStateAt(listOf("7g7f")))
    }

    @Test
    fun `withEvalStateは対象ノード以外の兄弟に影響しない`() {
        var tree = StudyTree()
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(emptyList(), "2g2f", newId = 2L)
        tree = tree.withEvalState(listOf("7g7f"), StudyEvalState.Error)

        assertEquals(StudyEvalState.Error, tree.evalStateAt(listOf("7g7f")))
        assertEquals(StudyEvalState.None, tree.evalStateAt(listOf("2g2f")), "別の兄弟は変化しない")
    }

    @Test
    fun `木にない手のpathForMovesは見つかった分だけで打ち切る`() {
        val tree = StudyTree().withMovePlayed(emptyList(), "7g7f", newId = 1L)
        assertEquals(listOf(0), tree.pathForMoves(listOf("7g7f", "3c3d")), "3c3dはまだ木に無いので1手目分だけ")
    }

    @Test
    fun `siblingsAtDepth0はrootChildren`() {
        var tree = StudyTree()
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(emptyList(), "2g2f", newId = 2L)
        val siblings = tree.siblingsAtDepth(listOf("7g7f"), depth = 0)
        assertEquals(tree.rootChildren, siblings)
        assertTrue(siblings.size > 1)
    }

    @Test
    fun `分岐のない1本道ではbranchFlagsが全てfalse`() {
        var tree = StudyTree()
        tree = tree.withMovePlayed(emptyList(), "7g7f", newId = 1L)
        tree = tree.withMovePlayed(listOf("7g7f"), "3c3d", newId = 2L)
        val flags = tree.branchFlags(listOf("7g7f", "3c3d"))
        assertFalse(flags.any { it })
    }
}
