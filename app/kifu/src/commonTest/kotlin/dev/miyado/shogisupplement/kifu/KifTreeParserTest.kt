package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KifTreeParserTest {
    @Test
    fun nestedAndSiblingVariationsUseCurrentAncestors() {
        val text = """
            手合割：平手
            *開始
            1 ７六歩(77)
            2 ３四歩(33)
            3 ２六歩(27)
            4 投了
            変化：2手
            2 ８四歩(83)
            *A
            3 ６八銀(79)
            4 ８五歩(84)
            変化：3手
            3 ２六歩(27)
            &B
            変化：3手
            3 ７八金(69)
            *C
            変化：2手
            2 ４四歩(43)
            *D
        """.trimIndent()
        val nodes = KifTreeParser().parse(text).nodes
        assertEquals(listOf(null, 0, 1, 2, 3, 1, 5, 6, 5, 5, 1), nodes.map { it.parentId })
        assertEquals(listOf("開始"), nodes[0].notes.comments)
        assertEquals("投了", nodes[4].endReason)
        assertEquals(listOf("A"), nodes[5].notes.comments)
        assertEquals(listOf("B"), nodes[8].notes.bookmarks)
        assertEquals(listOf("C"), nodes[9].notes.comments)
        assertEquals(listOf("D"), nodes[10].notes.comments)
    }

    @Test
    fun sameDestinationAtBranchStartUsesParentNotPreviousSectionTail() {
        val text = "1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n変化：2手\n2 同　歩(75) (0:03/00:00:03)\n*分岐\n3 中断\n&終局"
        val nodes = KifTreeParser().parse(text).nodes
        val branch = nodes[nodes.lastIndex - 1]
        assertEquals("7e7f", branch.moveUsi)
        assertEquals(1, branch.parentId)
        assertEquals(3, branch.timeSeconds)
        assertEquals("中断", nodes.last().endReason)
        assertEquals(KifuPositionNotes(listOf("分岐")), branch.notes)
        assertEquals(KifuPositionNotes(bookmarks = listOf("終局")), nodes.last().notes)
    }

    @Test
    fun inconsistentOrMissingBranchParentsAreRejected() {
        for (suffix in listOf("変化：9手\n9 ２六歩(27)", "変化：1手\n2 ２六歩(27)", "変化：1手", "変化：1手\n*曖昧", "変化：1手\n変化：1手\n1 ２六歩(27)")) {
            assertFailsWith<KifuParseException> { KifTreeParser().parse("1 ７六歩(77)\n$suffix") }
        }
    }
}
