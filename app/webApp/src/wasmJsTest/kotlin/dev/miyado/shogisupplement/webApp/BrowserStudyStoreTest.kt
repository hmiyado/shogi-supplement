@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserStudyStoreTest {
    @Test
    fun savingAnalysisBeforeMigrationPreservesLegacyStudy() = runTest {
        val original = "棋戦：legacy-analysis-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
        val legacyKif = "*既存のメモ\n$original"
        val key = dev.miyado.shogisupplement.util.sha256Hex(original)
        val legacy = BrowserStudyStore()
        assertTrue(legacy.save(key, null, legacyKif))
        val reports = BrowserReportStore()
        val report = dev.miyado.shogisupplement.webApp.report.WebReportData(
            dev.miyado.shogisupplement.db.GameRecord(1, "legacy.kif", "", 1, null, null, 1, 1500,
                coefVersion = "saved", kifText = original, movesUsi = listOf("7g7f")),
            emptyList(), emptyList(), null, null, null,
        )
        assertTrue(reports.save(report, null))
        assertEquals(legacyKif, BrowserStudyDocument.load(original).document.savedKif)
        reports.list().first { it.report.game.kifText == original }.let { assertTrue(reports.delete(it)) }
    }

    @Test
    fun restoredReportKeepsCloudBranchesAcrossRestartAndPreservesLocalEdits() = runTest {
        val bridge = installOfflineStudyBridge()
        val original = "棋戦：restore-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 ３四歩(33)"
        val cloud = "$original\n変化：2手\n2 ３四歩(33)\n*クラウド分岐\n&再確認"
        val report = dev.miyado.shogisupplement.webApp.report.WebReportData(
            dev.miyado.shogisupplement.db.GameRecord(1, "restore.kif", "", 1, null, null, 1, 1500,
                coefVersion = "saved", kifText = original, studyKif = cloud, movesUsi = listOf("7g7f", "3c3d")),
            emptyList(), emptyList(), null, null, null,
        )
        val vm = KentoViewModel(this)
        val reopened = KentoViewModel(this)
        try {
            assertTrue(vm.openRestoredReport(report))
            assertEquals(cloud, vm.savedKifForExport(original))
            val session = BrowserStudyDocument.load(original)
            val branch = session.document.tree.rootChildren.single().children[1]
            val edited = session.document.tree.selectChild(listOf("7g7f"), branch.id)
                .withNotes(listOf("7g7f", "3c3d"), dev.miyado.shogisupplement.kifu.KifuPositionNotes(comments = listOf("ローカル編集")))
            session.document.update(emptyList(), edited, session.document.tree)
            assertTrue(session.save())
            assertTrue(reopened.openRestoredReport(report))
            val restored = BrowserStudyDocument.load(original).document.tree.rootChildren.single().children
            assertEquals(2, restored.size)
            assertEquals(listOf("ローカル編集"), restored[1].notes.comments)
            assertEquals(session.document.savedKif, reopened.savedKifForExport(original))
        } finally {
            vm.dispose()
            reopened.dispose()
            val store = BrowserReportStore()
            store.list().firstOrNull { it.report.game.kifText == original }?.let { store.delete(it) }
            bridge.restore()
        }
    }

    @Test
    fun browserLeaveGuardChecksCurrentDirtyStateAndRemovesListener() {
        val target = createLeaveTestTarget()
        var dirty = false
        val guard = installBrowserLeaveGuard({ dirty }, target)
        assertFalse(target.probe())
        dirty = true
        assertTrue(target.probe())
        dirty = false
        assertFalse(target.probe())
        dirty = true
        guard.dispose()
        assertFalse(target.probe())
    }

    @Test
    fun conflictingLegacyMigrationKeepsBothVersions() = runTest {
        val original = "棋戦：conflict-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
        val key = dev.miyado.shogisupplement.util.sha256Hex(original)
        val legacy = BrowserStudyStore()
        val storage = BrowserStudyStore("shogi-supplement-migration-conflict-tests")
        val studyKey = BrowserStudyDocument.storageKey(original)
        val old = "$original\n*旧タブだけの編集"
        val current = "$original\n*新タブだけの編集"
        assertTrue(legacy.save(key, null, old))
        assertTrue(storage.save(studyKey, null, current))
        repeat(2) {
            kotlin.test.assertFailsWith<IllegalStateException> { BrowserStudyDocument.load(original, storage) }
            assertEquals(current, storage.load(studyKey))
            assertEquals(BrowserStudyDocument.MIGRATING_PREFIX + old, legacy.load(key))
        }
        assertTrue(storage.save(studyKey, current, old))
        assertEquals(old, BrowserStudyDocument.load(original, storage).document.savedKif)
        assertEquals("", legacy.load(key))
        storage.delete(studyKey, old)
        legacy.delete(key, "")
    }

    @Test
    fun viewModelDeletesSelectedReportAndStudy() = runTest {
        val bridge = installOfflineStudyBridge()
        val original = "棋戦：vm-delete-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
        val report = dev.miyado.shogisupplement.webApp.report.WebReportData(
            dev.miyado.shogisupplement.db.GameRecord(1, "delete.kif", "", 1, null, null, 1, 1500,
                coefVersion = "saved", kifText = original, movesUsi = listOf("7g7f")),
            emptyList(), emptyList(), "", null, null,
        )
        val store = BrowserReportStore()
        val vm = KentoViewModel(this)
        try {
            assertTrue(store.save(report, null))
            vm.showSavedReports()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.withTimeout(5000) {
                    while (vm.state.savedGames == null) kotlinx.coroutines.delay(10)
                }
            }
            val game = vm.state.savedGames!!.single { it.kifText == original }
            val result = kotlinx.coroutines.CompletableDeferred<dev.miyado.shogisupplement.upload.DeleteGameOutcome>()
            vm.deleteSavedReport(game, false) { result.complete(it) }
            assertEquals(dev.miyado.shogisupplement.upload.DeleteGameOutcome.Success, result.await())
            assertTrue(vm.state.savedGames!!.none { it.kifText == original })
            assertTrue(store.list().none { it.report.game.kifText == original })
            assertNull(store.loadLast())
        } finally {
            vm.dispose()
            val key = dev.miyado.shogisupplement.util.sha256Hex(original)
            BrowserStudyStore("shogi-supplement-reports").delete("study:$key", "")
            BrowserStudyStore().delete(key, "")
            bridge.restore()
        }
    }

    @Test
    fun legacyMigrationResumesAndClearsPrivateContentWithoutResurrection() = runTest {
        val legacy = BrowserStudyStore()
        val storage = BrowserStudyStore("shogi-supplement-migration-tests")
        for (interrupted in listOf(false, true)) {
            val original = "棋戦：migrate-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
            val edited = "$original\n*移すメモ"
            val key = dev.miyado.shogisupplement.util.sha256Hex(original)
            val studyKey = BrowserStudyDocument.storageKey(original)
            val oldValue = if (interrupted) BrowserStudyDocument.MIGRATING_PREFIX + edited else edited
            assertTrue(legacy.save(key, null, oldValue))
            val first = async { BrowserStudyDocument.load(original, storage) }
            val second = async { BrowserStudyDocument.load(original, storage) }
            assertEquals(edited, first.await().document.savedKif)
            assertEquals(edited, second.await().document.savedKif)
            assertEquals("", legacy.load(key))
            assertFalse(legacy.save(key, oldValue, "$edited\n*古いタブ"))
            assertTrue(storage.save(studyKey, edited, ""))
            kotlin.test.assertFailsWith<IllegalStateException> { BrowserStudyDocument.load(original, storage) }
            assertEquals("", storage.load(studyKey))
            storage.delete(studyKey, "")
            legacy.delete(key, "")
        }
    }

    @Test
    fun reportDeletionChecksStudyVersionAndPreventsOldTabResurrection() = runTest {
        val original = "棋戦：delete-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
        val storage = BrowserStudyStore("shogi-supplement-atomic-delete-tests")
        val reports = BrowserReportStore(storage)
        val report = dev.miyado.shogisupplement.webApp.report.WebReportData(
            dev.miyado.shogisupplement.db.GameRecord(1, "delete.kif", "", 1, null, null, 1, 1500,
                coefVersion = "saved", kifText = original, movesUsi = listOf("7g7f")),
            emptyList(), emptyList(), "", null, null,
        )
        assertTrue(reports.save(report, null))
        val oldSelection = reports.list().single()
        val study = BrowserStudyDocument.load(original, storage)
        study.document.update(emptyList(), study.document.tree.withNotes(emptyList(), dev.miyado.shogisupplement.kifu.KifuPositionNotes(listOf("保存済み"))))
        assertTrue(study.save())
        assertFalse(reports.delete(oldSelection))
        assertEquals(report, reports.loadLast())
        assertTrue(reports.delete(reports.list().single()))
        assertNull(reports.loadLast())
        study.document.update(emptyList(), study.document.tree.withNotes(emptyList(), dev.miyado.shogisupplement.kifu.KifuPositionNotes(listOf("古いタブ"))))
        assertFalse(study.save())
        kotlin.test.assertFailsWith<IllegalStateException> { BrowserStudyDocument.load(original, storage) }
        assertTrue(reports.list().isEmpty())
        assertFalse(reports.save(report, oldSelection.version))
        kotlin.test.assertFailsWith<IllegalStateException> { BrowserStudyDocument.load(original, storage) }
        assertTrue(reports.save(report, null))
        assertEquals(original, BrowserStudyDocument.load(original, storage).document.savedKif)
        assertTrue(reports.delete(reports.list().single()))
        storage.delete(BrowserStudyDocument.storageKey(original), "")
    }

    @Test
    fun listingAndDeletionPreserveConcurrentSaveAndOtherSelection() = runTest {
        val store = BrowserStudyStore("shogi-supplement-list-tests-${kotlin.random.Random.nextLong()}")
        assertTrue(store.saveAndSelect("a", null, "A", "last"))
        assertTrue(store.saveAndSelect("b", null, "B", "last"))
        assertEquals(mapOf("a" to "A", "b" to "B", "last" to "b"), store.entries())
        assertTrue(store.saveAndSelect("a", "A", "A2", "last"))
        assertFalse(store.deleteAndUnselect("a", "A", "last"))
        assertEquals("A2", store.load("a"))
        assertEquals("a", store.load("last"))
        assertTrue(store.deleteAndUnselect("b", "B", "last"))
        assertEquals("a", store.load("last"))
        assertTrue(store.deleteAndUnselect("a", "A2", "last"))
        assertEquals(emptyMap(), store.entries())
    }

    @Test
    fun contentAndSelectedKeyCommitTogetherAndConflictDoesNotChangeSelection() = runTest {
        val store = BrowserStudyStore("shogi-supplement-selection-tests")
        val prefix = "test-${kotlin.random.Random.nextLong()}"
        val selection = "$prefix-selected"
        val completed = mutableListOf<String>()
        val first = async {
            assertTrue(store.saveAndSelect("$prefix-a", null, "A", selection))
            completed.add("$prefix-a")
        }
        val second = async {
            assertTrue(store.saveAndSelect("$prefix-b", null, "B", selection))
            completed.add("$prefix-b")
        }
        first.await(); second.await()
        assertEquals(completed.last(), store.load(selection))
        assertFalse(store.saveAndSelect("$prefix-a", null, "stale", selection))
        assertEquals(completed.last(), store.load(selection))
        assertEquals("A", store.load("$prefix-a"))
        assertTrue(store.delete("$prefix-a", "A"))
        assertTrue(store.delete("$prefix-b", "B"))
        assertTrue(store.delete(selection, completed.last()))
    }

    @Test
    fun viewModelResumesSavedReportAndNotesWithoutEngine() = runTest {
        val bridge = installOfflineStudyBridge()
        val original = "棋戦：restart-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
        val reportStorage = BrowserStudyStore("shogi-supplement-reports")
        val studyStorage = BrowserStudyStore("shogi-supplement-reports")
        val report = dev.miyado.shogisupplement.webApp.report.WebReportData(
            dev.miyado.shogisupplement.db.GameRecord(1, "saved.kif", "", 1, null, null, 1, 1500,
                coefVersion = "saved", kifText = original, movesUsi = listOf("7g7f")),
            emptyList(), emptyList(), "保存時の棋力", null, null,
        )
        val key = dev.miyado.shogisupplement.util.sha256Hex(original)
        val vm = KentoViewModel(this)
        try {
            assertTrue(BrowserReportStore(reportStorage).save(report, null))
            val study = BrowserStudyDocument.load(original)
            study.document.update(emptyList(), study.document.tree.withNotes(emptyList(), dev.miyado.shogisupplement.kifu.KifuPositionNotes(listOf("再開するメモ"))))
            assertTrue(study.save())
            vm.showSavedReports()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.withTimeout(5000) {
                    while (vm.state.savedGames == null) kotlinx.coroutines.delay(10)
                }
            }
            assertEquals(dev.miyado.shogisupplement.navigation.AppDestination.KENTO_LIBRARY, vm.state.destination)
            val savedGame = vm.state.savedGames!!.single { it.kifText == original }
            vm.openSavedReport(savedGame)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.withTimeout(5000) {
                    while (vm.state.report == null) kotlinx.coroutines.delay(10)
                }
            }
            assertEquals(report, vm.state.report)
            assertNull(vm.state.savedGames)
            assertFalse(vm.hasUnsavedStudy())
            assertEquals(study.document.savedKif, vm.savedKifForExport(original))
            assertNull(vm.savedKifForExport("別の棋譜"))
            vm.startStudy(dev.miyado.shogisupplement.board.ShogiBoard().toSfen(), false, false, 0, null, 0,
                dev.miyado.shogisupplement.ui.report.StudyOrigin("開始局面", null), null, null)
            vm.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
            vm.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
            assertEquals(study.document.savedKif, vm.savedKifForExport(original))
            assertTrue(vm.hasUnsavedStudy())
            vm.goHome()
            assertTrue(vm.state.confirmDiscardStudy)
            vm.cancelDiscardStudy()
            assertFalse(vm.state.confirmDiscardStudy)
            assertEquals(report, vm.state.report)
            assertTrue(vm.hasUnsavedStudy())
            vm.endStudy()
            val nextSfen = dev.miyado.shogisupplement.ui.report.computeSfenAtStep(null, listOf("7g7f"), 1)
            vm.startStudy(nextSfen, false, false, 1, null, 1,
                dev.miyado.shogisupplement.ui.report.StudyOrigin("1手目", null), null, null)
            vm.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(8, 3))
            vm.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(8, 4))
            kotlinx.coroutines.withTimeout(5000) {
                while (vm.hasUnsavedStudy()) kotlinx.coroutines.delay(10)
            }
            assertFalse(vm.studyState.value!!.saveFailed)
            val reopened = BrowserStudyDocument.load(original).document
            assertEquals(listOf("再開するメモ"), reopened.tree.notesAt(emptyList())?.comments)
            assertNotNull(reopened.tree.subtree(listOf("2g2f")))
            assertNotNull(reopened.tree.subtree(listOf("7g7f", "8c8d")))
            assertTrue(vm.deleteStudyBranch(nextSfen, listOf("8c8d"), vm.studyState.value!!.nodeId))
            kotlinx.coroutines.withTimeout(5000) {
                while (vm.hasUnsavedStudy()) kotlinx.coroutines.delay(10)
            }
            val afterDelete = BrowserStudyDocument.load(original).document
            assertNull(afterDelete.tree.subtree(listOf("7g7f", "8c8d")))
            assertNotNull(afterDelete.tree.subtree(listOf("2g2f")))
        } finally {
            vm.dispose()
            reportStorage.load(key)?.let { reportStorage.delete(key, it) }
            reportStorage.delete("last", key)
            val studyKey = BrowserStudyDocument.storageKey(original)
            studyStorage.load(studyKey)?.let { studyStorage.delete(studyKey, it) }
            bridge.restore()
        }
    }

    @Test
    fun savedReportReopensWithoutAnalysisAndRejectsOldWriter() = runTest {
        val storage = BrowserStudyStore("shogi-supplement-report-tests")
        val first = BrowserReportStore(storage)
        val second = BrowserReportStore(storage)
        val original = "棋戦：test-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 投了"
        val game = dev.miyado.shogisupplement.db.GameRecord(
            1, "test.kif", "", 1, "先手", "後手", 123, 1400,
            coefVersion = "old-coefficients", kifText = original, movesUsi = listOf("7g7f"),
            engineMetaJson = "{\"version\":\"old-engine\"}",
        )
        val blunder = dev.miyado.shogisupplement.db.BlunderRecord(
            2, 1, 1, "sente", "7g7f", "2g2f", 0.1, "sfen", "category", 0, 0, false, null,
            "verdict", "note", "type", 1.0, bestPv = "2g2f 8c8d", cpBefore = 200, cpAfter = -100,
        )
        val report = dev.miyado.shogisupplement.webApp.report.WebReportData(
            game, listOf(blunder), listOf(dev.miyado.shogisupplement.db.PositionEvalRow(0, 200, null, "2g2f")),
            "保存時の棋力", "保存時の一致率", "保存時の悪手率",
        )
        val before = first.version(original)
        assertTrue(first.save(report, before))
        assertEquals(report, second.loadLast())
        val saved = second.list().single()
        assertEquals(report, saved.report)
        assertEquals(report, second.load(saved.key))
        assertFalse(second.save(report.copy(strengthText = "古いタブの結果"), before))
        assertEquals(report, first.loadLast())
        assertTrue(second.save(report.copy(strengthText = "明示的な再解析結果"), second.version(original)))
        assertEquals("明示的な再解析結果", first.loadLast()?.strengthText)
        assertFalse(first.delete(saved))
        assertTrue(first.delete(first.list().single()))
        assertEquals(emptyList(), first.list())
        assertNull(first.loadLast())
    }

    @Test
    fun studyDocumentReopensEditsAndRejectsStaleTab() = runTest {
        val original = "棋戦：test-${kotlin.random.Random.nextLong()}\n1 ７六歩(77)\n2 ３四歩(33)\n3 投了"
        val store = BrowserStudyStore("shogi-supplement-study-tests")
        val first = BrowserStudyDocument.load(original, store)
        val stale = BrowserStudyDocument.load(original, store)
        val path = listOf("7g7f")
        val notes = dev.miyado.shogisupplement.kifu.KifuPositionNotes(listOf("ブラウザの検討"), listOf("確認"))
        val initial = first.document.tree.subtree(path)!!
        first.document.update(path, initial.withMovePlayed(emptyList(), "8c8d", 99).withNotes(listOf("8c8d"), notes), initial)
        assertTrue(first.save())
        val reopened = BrowserStudyDocument.load(original, store)
        assertEquals(notes, reopened.document.tree.notesAt(path + "8c8d"))
        stale.document.update(emptyList(), stale.document.tree.withNotes(emptyList(), notes))
        assertFalse(stale.save())
        assertTrue(stale.document.isDirty)
        assertEquals(notes, BrowserStudyDocument.load(original, store).document.tree.notesAt(path + "8c8d"))
        reopened.document.update(emptyList(), reopened.document.tree.withoutBranch(path + "8c8d"))
        assertTrue(reopened.save())
        assertNull(BrowserStudyDocument.load(original, store).document.tree.notesAt(path + "8c8d"))
        val key = BrowserStudyDocument.storageKey(original)
        assertTrue(store.delete(key, store.load(key)!!))
    }

    @Test
    fun persistedKifCanBeReopenedAndConcurrentWritersCannotBothWin() = runTest {
        val key = "test-" + kotlin.random.Random.nextLong().toString()
        val store = BrowserStudyStore("shogi-supplement-study-tests")
        val reopened = BrowserStudyStore("shogi-supplement-study-tests")
        assertNull(store.load(key))
        assertTrue(store.save(key, null, "原文\n*メモ"))
        assertEquals("原文\n*メモ", reopened.load(key))
        val first = async { store.save(key, "原文\n*メモ", "編集A") }
        val second = async { reopened.save(key, "原文\n*メモ", "編集B") }
        assertEquals(1, listOf(first.await(), second.await()).count { it })
        val saved = reopened.load(key)!!
        assertTrue(saved == "編集A" || saved == "編集B")
        assertFalse(store.delete(key, "古い内容"))
        assertTrue(store.delete(key, saved))
        assertNull(reopened.load(key))
    }
}

private external interface StudyBridgeBackup : JsAny {
    fun restore()
}

@JsFun("""() => {
    const previous = window.kentoBridge;
    window.kentoBridge = {
        checkAssetsAvailable: (_, done) => done(false),
        runAnalysis: () => { throw new Error('Resume must not run analysis'); },
        createStudyEngine: () => { throw new Error('Resume must not start an engine'); }
    };
    return {restore: () => { window.kentoBridge = previous; }};
}""")
private external fun installOfflineStudyBridge(): StudyBridgeBackup

private external interface LeaveTestTarget : JsAny {
    fun probe(): Boolean
}

@JsFun("""() => {
    const target = new EventTarget();
    target.probe = () => {
        const event = new Event('beforeunload', {cancelable: true});
        target.dispatchEvent(event);
        return event.defaultPrevented;
    };
    return target;
}""")
private external fun createLeaveTestTarget(): LeaveTestTarget
