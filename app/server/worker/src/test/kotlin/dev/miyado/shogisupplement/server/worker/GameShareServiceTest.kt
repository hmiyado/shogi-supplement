package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.gameshare.*
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import dev.miyado.shogisupplement.server.worker.repo.StaticGameShareRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GameShareServiceTest {
    class Repository : StaticGameShareRepository {
        var sourceExists = true
        var ownerDeletedDuringUpload = false
        var snapshot: GameShareSnapshotJson? = null
        var reserveCalls = 0
        override suspend fun reserve(id: String, owner: String, hash: String): Boolean { reserveCalls++; return sourceExists }
        override suspend fun upload(snapshot: GameShareSnapshotJson) { this.snapshot = snapshot }
        override suspend fun finish(id: String, owner: String) = !ownerDeletedDuringUpload
        override suspend fun cleanup() = 0
    }
    private val request = CreateGameShareRequest(gameKey="game-1", initialSfen=ShogiBoard().toSfen(), mainline=listOf("7g7f"), selectedPly=1,userSide="sente",endReason="private comment")
    private fun service(r: Repository) = GameShareService(r,FakeAuthVerifier(mapOf("token" to "owner")),shareIdGenerator={"AbCdEfGhIjKlMnOpQrStUv"})
    @Test fun issuesOnlyGameplaySnapshot() = runTest {
        val r=Repository();val result=service(r).create(request,"Bearer token",null,null,null)
        assertIs<CreateGameShareOutcome.Success>(result);assertNull(r.snapshot!!.endReason);assertEquals(listOf("7g7f"),r.snapshot!!.mainline)
    }
    @Test fun requiresExistingSource() = runTest {
        val r=Repository().apply {sourceExists=false};assertIs<CreateGameShareOutcome.SourceMissing>(service(r).create(request,"Bearer token",null,null,null));assertNull(r.snapshot)
    }
    @Test fun deletionDuringUploadDoesNotIssueLink() = runTest {
        val r=Repository().apply {ownerDeletedDuringUpload=true};assertIs<CreateGameShareOutcome.SourceMissing>(service(r).create(request,"Bearer token",null,null,null))
    }
    @Test fun deniesAnonymousBeforeReservation() = runTest {
        val r=Repository();assertIs<CreateGameShareOutcome.Unauthorized>(service(r).create(request,null,null,null,null));assertEquals(0,r.reserveCalls)
    }
}
