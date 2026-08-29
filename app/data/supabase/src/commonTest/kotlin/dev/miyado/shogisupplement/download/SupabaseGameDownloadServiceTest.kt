package dev.miyado.shogisupplement.download

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.crypto.PrivateEncCodec
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.kifu.PrivateKifuFields
import dev.miyado.shogisupplement.pipeline.BlunderReport
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

/**
 * [SupabaseGameDownloadService] のPostgrest HTTPをMockEngineで差し替えたテスト
 * （HTTPのやり取り自体は[dev.miyado.shogisupplement.transfer.TransferRestoreServiceTest]と同じ流儀。
 * こちらはpostgrest-ktのSupabaseClient経由のためcreateSupabaseClientのhttpEngine差し替えを使う）。
 */
@OptIn(ExperimentalEncodingApi::class)
class SupabaseGameDownloadServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val secret = ByteArray(16) { it.toByte() }

    private class FakeAuthRepository(loggedIn: Boolean) : AuthRepository {
        override val currentUser: StateFlow<AuthUser?> =
            MutableStateFlow(if (loggedIn) AuthUser(id = "user-1") else null)
        override suspend fun signInAnonymously(): Result<Unit> = Result.success(Unit)
        override suspend fun accessToken(): String? = null
        override suspend fun refreshSession(): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun importSession(refreshToken: String): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeTransferSecretStore(private val secret: ByteArray?) : TransferSecretStore {
        override suspend fun load(): ByteArray? = secret
        override suspend fun save(secret: ByteArray) = Unit
        override suspend fun clear() = Unit
    }

    /** getByHash/updateUploadedAtだけを検証対象にし、他は使われない前提でUnsupportedにする。 */
    private class FakeGameRepository(
        private val existingHashes: Set<String> = emptySet(),
    ) : GameRepository {
        val uploadedAtCalls = mutableListOf<Long>()

        override fun saveAnalysis(
            fileName: String, contentHash: String, moves: List<String>, headers: Map<String, String>,
            reports: List<BlunderReport>, rating: Int, ratingSampleMoves: Int?, coefVersion: String,
            analyzedAt: Long, kifText: String?, userSide: String?, ratingService: String?, ratingRaw: Long?,
            ratingRule: String?, sourcePlace: String?, gameWinner: String?, endReason: String?,
            openingStyle: String?, openingCastle: String?, openingTags: String?,
            senteRating: Long?, goteRating: Long?,
            timeControlKind: String?, timeControlBaseMinutes: Long?, timeControlIncrementSeconds: Long?,
        ): Long = error("not used by SupabaseGameDownloadService")

        override fun seedFixtureBlunder(
            fileName: String, contentHash: String, rating: Int, coefVersion: String, report: BlunderReport,
            sfenBefore: String, userSide: String?, senteName: String?, goteName: String?, analyzedAt: Long,
        ): Long = error("not used")

        override fun getByHash(contentHash: String): Long? =
            if (contentHash in existingHashes) contentHash.hashCode().toLong() else null

        override fun getAllGames(): List<GameRecord> = error("not used")
        override fun getGameById(gameId: Long): GameRecord? = error("not used")
        override fun getNotUploadedGames(): List<GameRecord> = error("not used")
        override fun getUploadedGameCount(): Int = error("not used")
        override fun getGamesWithUserSide(): List<GameRecord> = error("not used")
        override fun updateUploadedAt(gameId: Long, epochSeconds: Long) {
            uploadedAtCalls += gameId
        }
        override fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?) =
            error("not used")
        override fun resetAllUploadedAt() = error("not used")
        override fun getReports(gameId: Long): List<BlunderRecord> = error("not used")
        override fun updateBestPv(blunderId: Long, newPv: String) = error("not used")
        override fun savePositionEvals(gameId: Long, rows: List<PositionEvalRow>) = error("not used")
        override fun getPositionEvals(gameId: Long): List<PositionEvalRow> = error("not used")
        override fun deleteGame(gameId: Long) = Unit
        override fun deleteAllLocalData() = Unit
    }


    private fun service(
        engine: MockEngine,
        authRepository: AuthRepository = FakeAuthRepository(loggedIn = true),
        transferSecretStore: TransferSecretStore = FakeTransferSecretStore(secret),
        gameRepository: GameRepository = FakeGameRepository(),
    ): SupabaseGameDownloadService {
        val client = createSupabaseClient(supabaseUrl = "https://example.supabase.co", supabaseKey = "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }
        return SupabaseGameDownloadService(client, transferSecretStore, authRepository, gameRepository)
    }

    private suspend fun encryptedPrivateEnc(contentHash: String, private: PrivateKifuFields): String {
        val kEnc = TransferSecretKeys.deriveEncKey(secret)
        val blob = PrivateEncCodec.encrypt(kEnc, private, aad = contentHash.encodeToByteArray())
        return Base64.encode(blob)
    }

    private fun rowsResponseEngine(json: String) = MockEngine { _ ->
        respond(content = ByteReadChannel(json), status = HttpStatusCode.OK, headers = jsonHeaders)
    }

    // ─── countRemoteGames ──────────────────────────────────────────────────

    @Test
    fun `countRemoteGamesはContent-Rangeヘッダの総数を返す`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentRange, "*/5"),
            )
        }
        val result = service(engine).countRemoteGames()
        assertEquals(5, result.getOrNull())
    }

    // ─── downloadAndImport: 認証・鍵の前提条件 ──────────────────────────────

    @Test
    fun `未ログインならHTTPに触れずNotAuthenticatedを返す`() = runTest {
        var requested = false
        val engine = MockEngine { _ -> requested = true; respond("", HttpStatusCode.InternalServerError) }
        val result = service(engine, authRepository = FakeAuthRepository(loggedIn = false))
            .downloadAndImport { GameImportOutcome(success = true, gameId = 1L) }
        assertEquals(GameDownloadOutcome.NotAuthenticated, result)
        assertTrue(!requested)
    }

    @Test
    fun `端末シークレット未生成ならHTTPに触れずNoSecretを返す`() = runTest {
        var requested = false
        val engine = MockEngine { _ -> requested = true; respond("", HttpStatusCode.InternalServerError) }
        val result = service(engine, transferSecretStore = FakeTransferSecretStore(null))
            .downloadAndImport { GameImportOutcome(success = true, gameId = 1L) }
        assertEquals(GameDownloadOutcome.NoSecret, result)
        assertTrue(!requested)
    }

    // ─── downloadAndImport: 正常系・復号・再構成 ────────────────────────────

    @Test
    fun `private_enc付きの行を復号しKIF再構成してimportGameへ渡し完了件数を返す`() = runTest {
        val contentHash = "hash-1"
        val privateEnc = encryptedPrivateEnc(
            contentHash,
            PrivateKifuFields(senteName = "太郎", goteName = "花子", extraHeaders = emptyMap(), comments = emptyList()),
        )
        val json = """
            [{"id":"row-1","content_hash":"$contentHash","moves_usi":["7g7f","3c3d"],
              "headers":{"開始日時":"2026/01/01 00:00","手合割":"平手"},"result":"投了",
              "source_place":"wars","side":"sente","private_enc":"$privateEnc",
              "rating_service":"shogi_wars","rating_raw":1600,"rating_rule":"standard"}]
        """.trimIndent()

        val captured = mutableListOf<ReconstructedGame>()
        val repository = FakeGameRepository()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = service(rowsResponseEngine(json), gameRepository = repository).downloadAndImport(
            onProgress = { done, total -> progress += done to total },
        ) { game ->
            captured += game
            GameImportOutcome(success = true, gameId = 42L)
        }

        assertEquals(GameDownloadOutcome.Completed(total = 1, succeeded = 1, failed = 0), result)
        assertEquals(1, captured.size)
        assertEquals("sente", captured[0].userSide)
        assertEquals(contentHash, captured[0].contentHash)
        assertEquals("wars", captured[0].sourcePlaceOverride)
        assertTrue(captured[0].kifText.contains("太郎"), "復号済みの対局者名がKIFに反映されるはず")
        assertEquals(listOf(0 to 1, 1 to 1), progress)
        assertEquals(listOf(42L), repository.uploadedAtCalls, "取込成功後はuploaded_atを確定させ再アップロード対象から外すはず")
    }

    @Test
    fun `source_placeがotherの行はoverrideを渡さず再構成KIFからの判定に委ねる`() = runTest {
        val contentHash = "hash-other"
        val privateEnc = encryptedPrivateEnc(
            contentHash,
            PrivateKifuFields(senteName = "太郎", goteName = "花子", extraHeaders = emptyMap(), comments = emptyList()),
        )
        val json = """
            [{"id":"row-1","content_hash":"$contentHash","moves_usi":["7g7f","3c3d"],
              "headers":{"開始日時":"2026/01/01 00:00","手合割":"平手"},"result":"投了",
              "source_place":"other","side":"sente","private_enc":"$privateEnc"}]
        """.trimIndent()

        val captured = mutableListOf<ReconstructedGame>()
        service(rowsResponseEngine(json)).downloadAndImport { game ->
            captured += game
            GameImportOutcome(success = true, gameId = 1L)
        }

        assertEquals(null, captured[0].sourcePlaceOverride)
    }

    @Test
    fun `private_encが無い行はマスク再構成で進める`() = runTest {
        val contentHash = "hash-no-private"
        val json = """
            [{"id":"row-2","content_hash":"$contentHash","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":"gote","private_enc":null}]
        """.trimIndent()

        val captured = mutableListOf<ReconstructedGame>()
        val result = service(rowsResponseEngine(json)).downloadAndImport { game ->
            captured += game
            GameImportOutcome(success = true, gameId = 1L)
        }

        assertEquals(GameDownloadOutcome.Completed(total = 1, succeeded = 1, failed = 0), result)
        assertTrue(captured[0].kifText.contains("opponent"), "先後判明時のマスク名（相手側）が入るはず")
    }

    @Test
    fun `復号できない行もマスク再構成で棋譜を戻す`() = runTest {
        val contentHash = "hash-broken-private"
        val json = """
            [{"id":"row-3","content_hash":"$contentHash","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":"gote",
              "private_enc":"YnJva2VuLWJsb2I="}]
        """.trimIndent()

        val captured = mutableListOf<ReconstructedGame>()
        val result = service(rowsResponseEngine(json)).downloadAndImport { game ->
            captured += game
            GameImportOutcome(success = true, gameId = 1L)
        }

        assertEquals(GameDownloadOutcome.Completed(total = 1, succeeded = 1, failed = 0), result)
        assertTrue(captured[0].kifText.contains("opponent"), "先後判明時のマスク名（相手側）が入るはず")
    }

    // ─── downloadAndImport: 冪等スキップ・部分失敗 ───────────────────────────

    @Test
    fun `既存content_hashはimportGameを呼ばずスキップしつつuploaded_atは確定させる`() = runTest {
        val contentHash = "already-local"
        val json = """
            [{"id":"row-3","content_hash":"$contentHash","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":null,"private_enc":null}]
        """.trimIndent()

        var importCalled = false
        val repository = FakeGameRepository(existingHashes = setOf(contentHash))
        val result = service(rowsResponseEngine(json), gameRepository = repository).downloadAndImport {
            importCalled = true
            GameImportOutcome(success = true, gameId = 999L)
        }

        assertEquals(GameDownloadOutcome.Completed(total = 1, succeeded = 1, failed = 0), result)
        assertTrue(!importCalled, "既存content_hashは再解析せずスキップするはず")
        assertEquals(listOf(contentHash.hashCode().toLong()), repository.uploadedAtCalls)
    }

    @Test
    fun `1局の取込失敗は他局を止めずfailedに計上する`() = runTest {
        val json = """
            [{"id":"row-4","content_hash":"hash-ok","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":null,"private_enc":null},
             {"id":"row-5","content_hash":"hash-fail","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":null,"private_enc":null}]
        """.trimIndent()

        val result = service(rowsResponseEngine(json)).downloadAndImport { game ->
            if (game.contentHash == "hash-fail") GameImportOutcome(success = false) else GameImportOutcome(success = true, gameId = 1L)
        }

        assertEquals(GameDownloadOutcome.Completed(total = 2, succeeded = 1, failed = 1), result)
    }

    @Test
    fun `importGameが例外を投げても1局の失敗として継続する`() = runTest {
        val json = """
            [{"id":"row-6","content_hash":"hash-throws","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":null,"private_enc":null},
             {"id":"row-7","content_hash":"hash-ok2","moves_usi":["7g7f"],
              "headers":{},"result":null,"source_place":null,"side":null,"private_enc":null}]
        """.trimIndent()

        val result = service(rowsResponseEngine(json)).downloadAndImport { game ->
            if (game.contentHash == "hash-throws") throw RuntimeException("boom")
            GameImportOutcome(success = true, gameId = 1L)
        }

        assertEquals(GameDownloadOutcome.Completed(total = 2, succeeded = 1, failed = 1), result)
    }

    @Test
    fun `HTTP通信自体が失敗すればNetworkErrorを返す`() = runTest {
        val engine = MockEngine { _ -> throw RuntimeException("connection failed") }
        val result = service(engine).downloadAndImport { GameImportOutcome(success = true, gameId = 1L) }
        assertTrue(result is GameDownloadOutcome.NetworkError)
    }

    @Test
    fun `対象0件ならimportGameを呼ばずCompletedを全て0件で返す`() = runTest {
        var called = false
        val result = service(rowsResponseEngine("[]")).downloadAndImport {
            called = true
            GameImportOutcome(success = true, gameId = 1L)
        }
        assertEquals(GameDownloadOutcome.Completed(total = 0, succeeded = 0, failed = 0), result)
        assertTrue(!called)
    }
}
