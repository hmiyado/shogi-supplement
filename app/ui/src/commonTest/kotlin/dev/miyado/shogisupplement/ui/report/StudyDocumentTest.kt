package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.kifu.KifuPositionNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyDocumentTest {
    @Test
    fun savesPreserveMainlineAndBranchCumulativeClocks() {
        val raw = "1 ７六歩(77) (0:02/01:02:03)\n2 ３四歩(33) (0:07/00:00:17)\n3 投了 (0:05/01:02:08)\n変化：2手\n2 ８四歩(83) (0:03/00:00:13)\n3 中断 (0:06/01:02:09)"
        var saved = raw
        val document = StudyDocument(raw) { _, edited -> saved = edited; true }
        val baseline = document.tree
        document.update(emptyList(), baseline.withMovePlayed(emptyList(), "2g2f", 999), baseline)
        assertTrue(document.save())
        val parser = dev.miyado.shogisupplement.kifu.KifTreeParser()
        assertEquals(
            parser.parse(raw).nodes.mapNotNull { it.cumulativeTimeSeconds },
            parser.parse(saved).nodes.mapNotNull { it.cumulativeTimeSeconds },
        )
        assertTrue(saved.contains("/01:02:03)"))
        assertTrue(saved.contains("/01:02:09)"))
        assertEquals(parser.parse(raw).mainLineContent(), parser.parse(saved).mainLineContent())
    }

    @Test
    fun batchDoesNotResurrectDeletedOriginAndRejectsEditsInsideIt() {
        for (editChild in listOf(false, true)) {
            val document = StudyDocument(original) { _, _ -> true }
            document.update(emptyList(), document.tree.withMovePlayed(emptyList(), "2g2f", 999))
            assertTrue(document.save())
            val parent = document.tree
            val child = parent.subtree(listOf("2g2f"))!!
            val childEdit = if (editChild) child.withNotes(emptyList(), KifuPositionNotes(listOf("子の編集"))) else child
            val edits = listOf(
                StudyDocument.Edit(emptyList(), parent.withoutBranch(listOf("2g2f")), parent),
                StudyDocument.Edit(listOf("2g2f"), childEdit, child),
            )
            if (editChild) {
                assertFailsWith<IllegalStateException> { document.updateAll(edits) }
                assertEquals(parent, document.tree)
                assertFalse(document.isDirty)
            } else {
                document.updateAll(edits)
                assertEquals(null, document.tree.subtree(listOf("2g2f")))
                assertTrue(document.save())
            }
        }
    }

    @Test
    fun batchConflictRollsBackAllInMemoryEdits() {
        val document = StudyDocument(original) { _, _ -> true }
        val baseline = document.tree
        val first = baseline.withNotes(emptyList(), KifuPositionNotes(listOf("A")))
        val second = baseline.withNotes(emptyList(), KifuPositionNotes(listOf("B")))
        assertFailsWith<IllegalStateException> {
            document.updateAll(listOf(StudyDocument.Edit(emptyList(), first, baseline), StudyDocument.Edit(emptyList(), second, baseline)))
        }
        assertEquals(baseline, document.tree)
        assertFalse(document.isDirty)
        assertEquals(original, document.savedKif)
    }

    @Test
    fun exportIncludesOnlySuccessfullySavedChanges() {
        var accept = true
        val document = StudyDocument(original) { _, _ -> accept }
        document.update(emptyList(), document.tree.withNotes(emptyList(), KifuPositionNotes(listOf("保存するメモ"))))
        assertEquals(original, document.savedKif)
        assertTrue(document.save())
        val saved = document.savedKif
        assertTrue(saved.contains("保存するメモ"))
        document.update(emptyList(), document.tree.withNotes(emptyList(), KifuPositionNotes(listOf("未保存のメモ"))))
        accept = false
        assertFalse(document.save())
        assertEquals(saved, document.savedKif)
        val reopened = StudyDocument(saved) { _, _ -> false }
        assertEquals(saved, reopened.savedKif)
    }

    @Test
    fun editDuringAsyncSaveRemainsDirtyAndNextSaveUsesSavedVersion() = kotlinx.coroutines.test.runTest {
        val document = StudyDocument(original) { _, _ -> false }
        val first = document.tree.withNotes(emptyList(), KifuPositionNotes(listOf("先の編集")))
        document.update(emptyList(), first)
        var savedKif = ""
        assertTrue(document.saveUsing { expected, edited ->
            assertEquals(original, expected)
            savedKif = edited
            document.update(emptyList(), first.withNotes(emptyList(), KifuPositionNotes(listOf("保存待ち中の編集"))))
            true
        })
        assertTrue(document.isDirty)
        assertTrue(document.saveUsing { expected, edited ->
            assertEquals(savedKif, expected)
            assertTrue(edited.contains("保存待ち中の編集"))
            true
        })
        assertFalse(document.isDirty)
    }

    @Test
    fun editsFromOverlappingOriginsDoNotEraseAlreadySavedBranches() {
        var stored = original
        val document = StudyDocument(stored) { expected, edited ->
            if (stored != expected) false else { stored = edited; true }
        }
        val outerPath = listOf("7g7f")
        val innerPath = listOf("7g7f", "3c3d")
        val outerBase = assertNotNull(document.tree.subtree(outerPath))
        val innerBase = assertNotNull(document.tree.subtree(innerPath))
        val innerEdit = innerBase.withMovePlayed(emptyList(), "7i6h", 100)
        document.update(innerPath, innerEdit, innerBase)
        assertTrue(document.save())
        val outerEdit = outerBase.withMovePlayed(emptyList(), "4c4d", 101)
        document.update(outerPath, outerEdit, outerBase)
        assertTrue(document.save())
        val restored = StudyDocument(stored) { _, _ -> false }
        assertNotNull(restored.tree.subtree(innerPath + "7i6h"))
        assertNotNull(restored.tree.subtree(outerPath + "4c4d"))
    }

    @Test
    fun conflictingNotesAreNotOverwritten() {
        val document = StudyDocument(original) { _, _ -> true }
        val baseline = document.tree
        document.update(emptyList(), baseline.withNotes(emptyList(), KifuPositionNotes(listOf("先に保存"))), baseline)
        assertTrue(document.save())
        assertFailsWith<IllegalStateException> {
            document.update(emptyList(), baseline.withNotes(emptyList(), KifuPositionNotes(listOf("古い編集"))), baseline)
        }
        assertEquals(listOf("先に保存"), document.tree.rootNotes.comments)
    }
    @Test
    fun terminalTimeEditIsRejectedBeforePersistence() {
        val document = StudyDocument("1 ７六歩(77)\n2 投了 (0:12/00:00:12)") { _, _ -> error("Must not save") }
        val before = document.tree
        val child = before.rootChildren.single()
        val changed = before.copy(rootChildren = listOf(child.copy(endings = child.endings.map { it.copy(timeSeconds = 34) })))
        assertFailsWith<IllegalArgumentException> { document.update(emptyList(), changed) }
        assertEquals(before, document.tree)
        assertFalse(document.isDirty)
    }
    private val original = "手合割：平手\n1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n4 投了\n変化：2手\n2 ８四歩(83)\n*別の検討"

    @Test
    fun editsFromAMidgameOriginPreserveOtherBranchesAndSurviveReload() {
        var stored = original
        val document = StudyDocument(stored) { expected, edited ->
            if (stored != expected) false else { stored = edited; true }
        }
        val origin = listOf("7g7f", "3c3d")
        val subtree = assertNotNull(document.tree.subtree(origin))
            .withMovePlayed(emptyList(), "7i6h", 99)
            .withNotes(listOf("7i6h"), KifuPositionNotes(listOf("新しい検討"), listOf("確認")))
        document.update(origin, subtree)
        assertTrue(document.isDirty)
        assertTrue(document.save())
        assertFalse(document.isDirty)
        val reopened = StudyDocument(stored) { _, _ -> false }
        assertEquals(listOf("別の検討"), reopened.tree.notesAt(listOf("7g7f", "8c8d"))?.comments)
        assertEquals(listOf("新しい検討"), reopened.tree.notesAt(origin + "7i6h")?.comments)
    }

    @Test
    fun conflictKeepsUnsavedEditsAndDoesNotRetryAgainstNewerData() {
        val expectedValues = mutableListOf<String>()
        val document = StudyDocument(original) { expected, _ -> expectedValues.add(expected); false }
        val edited = document.tree.withNotes(emptyList(), KifuPositionNotes(listOf("未保存")))
        document.update(emptyList(), edited)
        repeat(2) { assertFalse(document.save()) }
        assertEquals(listOf(original, original), expectedValues)
        assertTrue(document.isDirty)
        assertEquals(edited, document.tree)
    }

    @Test
    fun deletingMainLineIsRejectedWithoutMutatingDocument() {
        val document = StudyDocument(original) { _, _ -> error("Must not save") }
        val before = document.tree
        assertFailsWith<IllegalArgumentException> {
            document.update(emptyList(), before.withoutBranch(listOf("7g7f", "3c3d")))
        }
        assertEquals(before, document.tree)
        assertFalse(document.isDirty)
    }
}
