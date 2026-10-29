package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KifTreeWriterTest {
    @Test
    fun siblingTerminationsKeepTheirOwnTimeAndAnnotations() {
        val raw = "1 ７六歩(77)\n2 投了 (0:05/00:00:05)\n*本譜\n変化：2手\n2 中断 (0:07/00:00:07)\n&分岐"
        val parsed = KifTreeParser().parse(raw)
        assertEquals(listOf("投了", "中断"), parsed.nodes.mapNotNull { it.endReason })
        assertEquals(parsed, KifTreeParser().parse(KifTreeWriter().write(parsed)))
    }

    @Test
    fun nestedBranchesAndEditedNotesRoundTrip() {
        val raw = """
            手合割：平手
            *開始
            &初期局面
            1 ７六歩(77)
            2 ３四歩(33)
            3 ２六歩(27)
            4 投了
            変化：2手
            2 ８四歩(83) (0:03/00:00:03)
            *A
            3 ６八銀(79)
            4 ８五歩(84)
            変化：3手
            3 ２六歩(27)
            &B
            4 中断
            変化：3手
            3 ７八金(69)
            *C
            変化：2手
            2 ４四歩(43)
            *D
        """.trimIndent()
        val parsed = KifTreeParser().parse(raw)
        val edited = parsed.copy(nodes = parsed.nodes.map {
            if (it.notes.comments == listOf("A")) it.copy(notes = KifuPositionNotes(listOf("変更  ", "", "二行目"), listOf("確認"))) else it
        })
        val restored = KifTreeParser().parse(KifTreeWriter().write(edited))
        assertEquals(edited, restored)
        assertEquals(listOf("7g7f", "3c3d", "2g2f"), KifParser().parse(KifTreeWriter().write(edited)).moves)
    }

    @Test
    fun disconnectedNodesAndMultilineAnnotationsAreNotSilentlySaved() {
        val root = KifuTreeNode(0, null, null)
        assertFailsWith<IllegalArgumentException> {
            KifTreeWriter().write(KifuTree(emptyMap(), listOf(root, KifuTreeNode(1, 1, "7g7f"))))
        }
        assertFailsWith<IllegalArgumentException> {
            KifTreeWriter().write(KifuTree(emptyMap(), listOf(root.copy(notes = KifuPositionNotes(listOf("メモ\n変化：1手"))))))
        }
    }

    @Test
    fun continuationAfterTerminalIsWrittenAsVariation() {
        val parsed = KifTreeParser().parse("1 ７六歩(77)\n2 中断\n変化：2手\n2 ３四歩(33)")
        assertEquals(parsed, KifTreeParser().parse(KifTreeWriter().write(parsed)))
    }
}
