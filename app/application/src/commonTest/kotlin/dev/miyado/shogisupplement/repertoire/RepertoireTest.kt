package dev.miyado.shogisupplement.repertoire

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.SavedGameFilter
import dev.miyado.shogisupplement.db.filterGames
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RepertoireTest {
    @Test fun labelPathFindsBranchAndEarliestMatchingShape() {
        val board = ShogiBoard()
        val document = RepertoireDocument("test", board.toSfen(), listOf(
            RepertoireLine("7g7f", emptyList()), RepertoireLine("2g2f", listOf(RepertoireLine("3c3d", emptyList())))))
        assertEquals(emptyList<String>(), document.firstLabelPath(positionLabelKey(board.toSfen(), PositionLabelScope.WHITE)))
        board.push(ShogiMove.fromUsi("2g2f"))
        board.push(ShogiMove.fromUsi("3c3d"))
        assertEquals(listOf("2g2f", "3c3d"), document.firstLabelPath(positionLabelKey(board.toSfen())))
        assertNull(document.firstLabelPath("missing"))
    }

    @Test fun singleLabelPerScopeAndConfirmedSnapshotDeletion() {
        val repo = MemoryRepository()
        val sfen = ShogiBoard().toSfen()
        PositionLabelScope.entries.forEach { assertTrue(repo.addPositionLabel("a", sfen, it, it.title)) }
        assertEquals(3, repo.entries("a").size)
        assertFalse(repo.addPositionLabel("a", sfen, PositionLabelScope.ALL, "上書き"))
        val entry = repo.entries("a").first { RepertoireCodec.labels(it.payload).positionKey == positionLabelKey(sfen) }
        assertTrue(repo.removePositionLabel("a", entry, "局面全体"))
        val empty = repo.entries("a").first { it.id == entry.id }
        assertTrue(empty.dirty)
        assertTrue(RepertoireCodec.labels(empty.payload).labels.isEmpty())
        assertTrue(repo.addPositionLabel("a", sfen, PositionLabelScope.ALL, "再登録"))
        assertFalse(repo.removePositionLabel("a", entry, "局面全体"))
        assertEquals(listOf("再登録"), RepertoireCodec.labels(repo.entries("a").first { it.id == entry.id }.payload).labels)
        assertFailsWith<IllegalArgumentException> { repo.addPositionLabel("a", sfen, PositionLabelScope.ALL, "二行\n入力") }
    }

    @Test fun sideShapesIncludeOwnHandAndIgnoreOpponentAndTurn() {
        val sfen = ShogiBoard().toSfen()
        for (scope in listOf(PositionLabelScope.BLACK, PositionLabelScope.WHITE)) {
            val opponentMove = if (scope == PositionLabelScope.BLACK) "3c3d" else "7g7f"
            val board = ShogiBoard.fromSfen(sfen.replace(" b ", if (scope == PositionLabelScope.BLACK) " w " else " b "))
            board.push(ShogiMove.fromUsi(opponentMove))
            assertEquals(positionLabelKey(sfen, scope), positionLabelKey(board.toSfen(), scope))
            val ownHand = if (scope == PositionLabelScope.BLACK) "P" else "p"
            val otherHand = if (scope == PositionLabelScope.BLACK) "p" else "P"
            assertNotEquals(positionLabelKey(sfen, scope), positionLabelKey(sfen.replace(" - ", " $ownHand "), scope))
            assertEquals(positionLabelKey(sfen, scope), positionLabelKey(sfen.replace(" - ", " $otherHand "), scope))
        }
        assertNotEquals(positionLabelKey(sfen, PositionLabelScope.BLACK), positionLabelKey(sfen.replace("PPPPPPPPP", "+PPPPPPPPP"), PositionLabelScope.BLACK))
    }

    @Test fun repertoireCollectsLabelsAcrossSiblingBranchesWithoutDuplicates() {
        val repo = MemoryRepository()
        val initial = ShogiBoard().toSfen()
        val pawn = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")) }.toSfen()
        val rook = ShogiBoard().also { it.push(ShogiMove.fromUsi("2g2f")) }.toSfen()
        listOf(initial to "開始", pawn to "角道", rook to "飛車先").forEachIndexed { index, (sfen, name) ->
            repo.save("a", "$index", "labels", RepertoireCodec.encode(PositionLabels(positionLabelKey(sfen), listOf(name))))
        }
        repo.save("a", "side", "labels", RepertoireCodec.encode(PositionLabels(positionLabelKey(initial, PositionLabelScope.WHITE), listOf("平手"))))
        val doc = RepertoireDocument("分岐", initial, listOf(RepertoireLine("7g7f"), RepertoireLine("2g2f")))
        assertEquals(setOf("開始", "角道", "飛車先", "後手：平手"), doc.allLabels(repo.entries("a")).toSet())
        assertEquals(4, doc.allLabels(repo.entries("a")).size)
        val old = RepertoireCodec.labels("""{"positionKey":"old","labels":["旧ラベル"]}""")
        assertEquals(listOf("旧ラベル"), old.displayLabels())
    }

    @Test fun labelsIgnoreTurnAndMoveNumberButKeepHandsAndPieceSides() {
        val sfen = ShogiBoard().toSfen()
        assertEquals(positionLabelKey(sfen), positionLabelKey(sfen.replace(" b - 1", " w - 99")))
        assertNotEquals(positionLabelKey(sfen), positionLabelKey(sfen.replace(" b - 1", " b P 1")))
        assertNotEquals(positionLabelKey(sfen.replace(" b - 1", " b P 1")), positionLabelKey(sfen.replace(" b - 1", " b p 1")))
        assertNotEquals(positionLabelKey(sfen), positionLabelKey(sfen.replace("PPPPPPPPP", "pPPPPPPPP")))
    }

    @Test fun filteringFindsIntermediatePositionsAcrossGamesAndCombinesConditions() {
        val board = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")) }
        val repo = MemoryRepository()
        repo.save("alice", "label", "labels", RepertoireCodec.encode(PositionLabels(positionLabelKey(board.toSfen()), listOf("角道"))))
        val game = GameRecord(id = 1, fileName = "one", contentHash = "one", moveCount = 2, senteName = null,
            goteName = null, analyzedAt = 0, rating = 0, coefVersion = "", movesUsi = listOf("7g7f", "3c3d"), sourcePlace = "wars")
        val games = listOf(game, game.copy(id = 2, sourcePlace = "lishogi"), game.copy(id = 3, movesUsi = listOf("2g2f")))
        val labelled = repo.labelledGames("alice", games)
        assertEquals(listOf(1L, 2L), labelled.filterGames(GameListFilter(positionLabel = "角道")).map { it.id })
        assertEquals(listOf(1L), labelled.filterGames(GameListFilter(positionLabel = "角道", source = "wars")).map { it.id })
        assertEquals(games, repo.labelledGames("bob", games))
        assertEquals(games, repo.labelledGames("bob", labelled))
        val filter = GameListFilter(positionLabel = "角道")
        assertEquals(filter, SavedGameFilter.fromFilter("name", filter, 0).conditionFilter())
    }

    @Test fun documentRoundTripKeepsArbitraryInitialPositionAndBranches() {
        val doc = RepertoireDocument("定跡", "9/9/9/9/9/9/9/9/9 w 2R2B4G4S4N4L18P 1",
            listOf(RepertoireLine("P*5e", listOf(RepertoireLine("P*5d"), RepertoireLine("P*4d")))))
        assertEquals(doc, RepertoireCodec.document(RepertoireCodec.encode(doc)))
    }

    @Test fun accountSwitchDuringUploadDoesNotAcknowledgeOrDownload() = runTest {
        val repo = MemoryRepository()
        repo.save("alice", "one", "line", "local")
        var owner = "alice"
        val remote = object : RepertoireRemote {
            override suspend fun put(owner: String, entry: RepertoireEntry): String { ownerChange(); return "v1" }
            fun ownerChange() { owner = "bob" }
            override suspend fun list(owner: String): List<RemoteRepertoireEntry> = error("Must not download after account switch")
        }
        assertFalse(RepertoireSync(repo, remote) { owner }.synchronize())
        assertTrue(repo.entries("alice").single().dirty)
    }

    @Test fun conflictPreservesPendingLocalEdit() = runTest {
        val repo = MemoryRepository()
        repo.save("alice", "one", "line", "local")
        val remote = object : RepertoireRemote {
            override suspend fun put(owner: String, entry: RepertoireEntry): String? = null
            override suspend fun list(owner: String) = listOf(RemoteRepertoireEntry("one", "line", "remote", "v2"))
        }
        assertFalse(RepertoireSync(repo, remote) { "alice" }.synchronize())
        assertEquals("local", repo.entries("alice").single().payload)
    }
}

private class MemoryRepository : RepertoireRepository {
    private val values = mutableMapOf<Pair<String, String>, RepertoireEntry>()
    override fun entries(owner: String) = values.filterKeys { it.first == owner }.values.toList()
    override fun save(owner: String, id: String, kind: String, payload: String) {
        val old = values[owner to id]
        values[owner to id] = RepertoireEntry(id, kind, payload, (old?.revision ?: 0) + 1, old?.remoteVersion, true)
    }
    override fun acknowledge(owner: String, id: String, revision: Long, remoteVersion: String) {
        values[owner to id]?.let { values[owner to id] = it.copy(remoteVersion = remoteVersion, dirty = it.revision != revision) }
    }
    override fun acceptRemote(owner: String, id: String, kind: String, payload: String, remoteVersion: String) {
        if (values[owner to id]?.dirty != true) values[owner to id] = RepertoireEntry(id, kind, payload, 1, remoteVersion, false)
    }
}
