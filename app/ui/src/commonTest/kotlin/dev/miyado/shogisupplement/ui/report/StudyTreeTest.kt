package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.kifu.KifuPositionNotes
import dev.miyado.shogisupplement.kifu.KifTreeParser
import dev.miyado.shogisupplement.kifu.KifTreeWriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class StudyTreeTest {

    @Test
    fun `古いキャッシュから兄弟を別々に削除しても終局メモを保持する`() {
        val source = StudyTree.fromKifu(KifTreeParser().parse(
            "1 ７六歩(77)\n2 ３四歩(33)\n3 投了\n変化：2手\n2 ８四歩(83)\n3 投了\n変化：2手\n2 ４四歩(43)\n3 投了\n変化：2手\n2 投了\n*残す終局のメモ",
        ))
        val current = source.withoutBranch(listOf("7g7f", "8c8d"))
        val edited = source.withoutBranch(listOf("7g7f", "4c4d"))
        val merged = current.mergeEdits(source, edited)
        val branch = merged.rootChildren.single()
        assertEquals(listOf("3c3d"), branch.children.map { it.moveUsi })
        assertEquals(1, branch.endings.single().beforeMoveIndex)
        assertEquals(listOf("残す終局のメモ"), branch.endings.single().notes.comments)
        val restored = StudyTree.fromKifu(KifTreeParser().parse(KifTreeWriter().write(merged.toKifu(emptyMap()))))
        assertEquals(branch.endings, restored.rootChildren.single().endings)
        val rootMerged = current.subtree(listOf("7g7f"))!!.mergeEdits(
            source.subtree(listOf("7g7f"))!!, edited.subtree(listOf("7g7f"))!!,
        )
        assertEquals(branch.endings, rootMerged.rootEndings)
    }

    @Test
    fun `不正な終局位置を黙って保存から落とさない`() {
        val tree = StudyTree(rootEndings = listOf(StudyEnding(1, "投了", null, KifuPositionNotes())))
        assertFailsWith<IllegalArgumentException> { tree.toKifu(emptyMap()) }
    }

    @Test
    fun `兄弟の終局理由と時間と注釈を別々に保存する`() {
        val raw = "1 ７六歩(77)\n2 投了 (0:05/00:00:05)\n*本譜終局\n変化：2手\n2 中断 (0:07/00:00:07)\n&検討終了"
        val parsed = KifTreeParser().parse(raw)
        val study = StudyTree.fromKifu(parsed)
        val endings = study.rootChildren.single().endings
        assertEquals(listOf("投了", "中断"), endings.map { it.reason })
        assertEquals(listOf(5, 7), endings.map { it.timeSeconds })
        assertEquals(listOf("本譜終局"), endings[0].notes.comments)
        assertEquals(listOf("検討終了"), endings[1].notes.bookmarks)
        assertEquals(parsed, KifTreeParser().parse(KifTreeWriter().write(study.toKifu(parsed.headers))))
    }

    @Test
    fun `同じ手の兄弟を黙って到達不能にしない`() {
        val parsed = KifTreeParser().parse("1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n変化：2手\n2 ３四歩(33)\n3 ６八銀(79)")
        val tree = StudyTree.fromKifu(parsed)
        val siblings = tree.rootChildren.single().children
        val selected = tree.selectChild(listOf("7g7f"), siblings[1].id)
        assertEquals(listOf("7g7f", "3c3d", "7i6h"), selected.continuation(emptyList()))
        val edited = selected.withNotes(listOf("7g7f", "3c3d"), KifuPositionNotes(comments = listOf("別分岐")))
        val merged = tree.mergeEdits(tree, edited)
        assertEquals(emptyList(), merged.rootChildren.single().children[0].notes.comments)
        assertEquals(listOf("別分岐"), merged.rootChildren.single().children[1].notes.comments)
        val restored = StudyTree.fromKifu(KifTreeParser().parse(KifTreeWriter().write(merged.toKifu(parsed.headers))))
        assertEquals(listOf("別分岐"), restored.rootChildren.single().children[1].notes.comments)
        val deleted = edited.withoutBranch(listOf("7g7f", "3c3d"))
        assertEquals(siblings[0], deleted.rootChildren.single().children.single())
        assertEquals(parsed, KifTreeParser().parse(KifTreeWriter().write(parsed)))
    }

    @Test
    fun `分岐の編集と削除をKIF保存して読み戻せる`() {
        val source = KifTreeParser().parse("手合割：平手\n1 ７六歩(77)\n2 ３四歩(33)\n変化：2手\n2 ８四歩(83)\n*削除対象")
        val note = KifuPositionNotes(listOf("追加した検討"), listOf("再確認"))
        val edited = StudyTree.fromKifu(source)
            .withoutBranch(listOf("7g7f", "8c8d"))
            .withMovePlayed(listOf("7g7f"), "4c4d", 99)
            .withNotes(listOf("7g7f", "4c4d"), note)
        val saved = KifTreeWriter().write(edited.toKifu(source.headers))
        val restored = StudyTree.fromKifu(KifTreeParser().parse(saved))
        assertEquals(null, restored.notesAt(listOf("7g7f", "8c8d")))
        assertEquals(note, restored.notesAt(listOf("7g7f", "4c4d")))
        assertEquals(listOf("3c3d", "4c4d"), restored.rootChildren.single().children.map { it.moveUsi })
        assertEquals(saved, KifTreeWriter().write(restored.toKifu(source.headers)))
    }

    @Test
    fun `KIFから検討ツリーへ変換しても分岐ごとの注釈と時間を保つ`() {
        val parsed = KifTreeParser().parse("*開始\n1 ７六歩(77)\n2 ３四歩(33)\n3 投了\n変化：2手\n2 ８四歩(83) (0:03/00:00:03)\n*分岐メモ\n&分岐名\n3 中断")
        val tree = StudyTree.fromKifu(parsed)
        assertEquals(listOf("開始"), tree.rootNotes.comments)
        assertEquals(listOf(false, true), tree.branchFlags(listOf("7g7f", "8c8d")))
        assertEquals(KifuPositionNotes(listOf("分岐メモ"), listOf("分岐名")), tree.notesAt(listOf("7g7f", "8c8d")))
        val branch = tree.nodesAtPath(tree.pathForMoves(listOf("7g7f", "8c8d"))).last()
        assertEquals(3, branch.timeSeconds)
        assertEquals("中断", branch.endings.single().reason)
        assertEquals("投了", tree.rootChildren.single().children.first().endings.single().reason)
        val fromBranch = StudyTree.fromKifu(parsed, branch.id.toInt())
        assertEquals(branch.notes, fromBranch.rootNotes)
        assertEquals("中断", fromBranch.rootEndings.single().reason)
        assertTrue(fromBranch.rootChildren.isEmpty())
    }

    @Test
    fun `局面メモを変更しても同じ手数の別分岐と開始局面は変わらない`() {
        val root = KifuPositionNotes(listOf("開始"))
        val note = KifuPositionNotes(listOf("", "メモ  "), listOf("しおり"))
        val tree = StudyTree(rootNotes = root)
            .withMovePlayed(emptyList(), "7g7f", 1)
            .withMovePlayed(emptyList(), "2g2f", 2)
            .withNotes(listOf("7g7f"), note)
        assertEquals(root, tree.notesAt(emptyList()))
        assertEquals(note, tree.notesAt(listOf("7g7f")))
        assertEquals(KifuPositionNotes(), tree.notesAt(listOf("2g2f")))
        assertEquals(KifuPositionNotes(), tree.withNotes(listOf("7g7f"), KifuPositionNotes()).notesAt(listOf("7g7f")))
        assertEquals(tree, tree.withNotes(listOf("7g7f", "3c3d"), note))
        assertEquals(null, tree.notesAt(listOf("7g7f", "3c3d")))
    }

    @Test
    fun `分岐削除は子孫のメモも削除し兄弟のIDとメモは維持する`() {
        val note = KifuPositionNotes(bookmarks = listOf("残す"))
        val tree = StudyTree()
            .withMovePlayed(emptyList(), "7g7f", 1)
            .withMovePlayed(listOf("7g7f"), "3c3d", 2)
            .withMovePlayed(listOf("7g7f", "3c3d"), "2g2f", 3)
            .withNotes(listOf("7g7f", "3c3d", "2g2f"), note)
            .withMovePlayed(listOf("7g7f"), "8c8d", 4)
            .withNotes(listOf("7g7f", "8c8d"), note)
        val deleted = tree.withoutBranch(listOf("7g7f", "3c3d"))
        assertEquals(null, deleted.notesAt(listOf("7g7f", "3c3d", "2g2f")))
        assertEquals(note, deleted.notesAt(listOf("7g7f", "8c8d")))
        assertEquals(4L, deleted.rootChildren.single().children.single().id)
        assertEquals(deleted, deleted.withoutBranch(listOf("7g7f", "3c3d")))
        assertEquals(deleted, deleted.withoutBranch(emptyList()))
    }

    @Test
    fun `存在しない親への指し手を既存の祖先に追加しない`() {
        val tree = StudyTree().withMovePlayed(emptyList(), "7g7f", 1)
        assertEquals(tree, tree.withMovePlayed(listOf("7g7f", "3c3d"), "2g2f", 2))
    }

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
