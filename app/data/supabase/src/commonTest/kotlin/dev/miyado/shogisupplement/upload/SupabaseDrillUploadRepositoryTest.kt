package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.BlunderRecord
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** [SupabaseUploadRepository] のドリル同期部分をPostgrest HTTPで検証する。 */
class SupabaseDrillUploadRepositoryTest {
    @Test
    fun `旧方式では再解析の問題置換を送信しない`() = runTest {
        val engine = MockEngine { error("旧テーブルを書き換えてはいけない") }
        val result = repository(engine).syncDrillProblems("user", "hash", listOf(problem()), replaceExisting = true)
        assertTrue(result is UploadResult.Failure)
    }

    @Test
    fun `削除は所有者と固定要求をRPCに渡し失敗時に直接DELETEしない`() = runTest {
        for (mode in listOf("success", "conflict", "failure")) {
            var count = 0
            val engine = MockEngine { request ->
                count++
                assertEquals(HttpMethod.Post, request.method)
                assertTrue(request.url.encodedPath.endsWith("rpc/delete_analysis_generation"))
                val body = kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText()) as kotlinx.serialization.json.JsonObject
                assertEquals(kotlinx.serialization.json.JsonPrimitive("owner"), body["p_expected_user_id"])
                assertEquals(kotlinx.serialization.json.JsonPrimitive("base"), body["p_expected_generation"])
                assertEquals(kotlinx.serialization.json.JsonPrimitive("request"), body["p_delete_generation"])
                if (mode == "failure") respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                else respond((mode == "success").toString(), HttpStatusCode.OK, jsonHeaders)
            }
            assertEquals(mode == "success", repository(engine).deleteAnalysisGeneration("owner", "hash",
                dev.miyado.shogisupplement.db.GameRepository.AnalysisDeleteTarget("base", "request")))
            assertEquals(1, count)
        }
    }

    @Test
    fun `世代付き回答は問題を再登録せず拒否や通信失敗を未送信に残す`() = runTest {
        for (mode in listOf("accepted", "rejected", "missing")) {
            val requests = mutableListOf<HttpRequestData>()
            val engine = MockEngine { request ->
                requests += request
                assertEquals(HttpMethod.Post, request.method)
                assertTrue(request.url.encodedPath.endsWith("rpc/record_generation_attempt"))
                val body = kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText()) as kotlinx.serialization.json.JsonObject
                assertEquals(kotlinx.serialization.json.JsonPrimitive("frozen-generation"), body["p_generation"])
                assertEquals(kotlinx.serialization.json.JsonPrimitive(41), body["p_ply"])
                val attempt = body["p_attempt"] as kotlinx.serialization.json.JsonObject
                assertEquals(kotlinx.serialization.json.JsonPrimitive("attempt-id"), attempt["client_attempt_id"])
                assertTrue("problem_id" !in attempt)
                assertEquals(kotlinx.serialization.json.JsonPrimitive("user"), attempt["user_id"])
                if (mode == "missing") respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                else respond((mode == "accepted").toString(), HttpStatusCode.OK, jsonHeaders)
            }
            val outcome = repository(engine).uploadGenerationAttempt("user", "hash", "frozen-generation", problem(),
                DrillAttemptUpload("attempt-id", "2g2f", true, null, 1780000000))
            if (mode == "accepted") assertEquals(UploadResult.Success, outcome)
            else assertTrue(outcome is UploadResult.Failure)
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `解析一括送信は固定世代を渡し競合や未対応RPCを成功扱いしない`() = runTest {
        val generation = "20000000-0000-0000-0000-000000000001"
        for (mode in listOf("applied", "already_applied", "conflict", "superseded", "wrong-generation", "unknown", "missing-rpc")) {
            val requests = mutableListOf<HttpRequestData>()
            val engine = MockEngine { request ->
                requests += request
                assertEquals(HttpMethod.Post, request.method)
                assertTrue(request.url.encodedPath.endsWith("rpc/replace_analysis_generation"))
                val body = kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText()) as kotlinx.serialization.json.JsonObject
                assertEquals(kotlinx.serialization.json.JsonPrimitive(generation), body["p_generation"])
                assertEquals(kotlinx.serialization.json.JsonNull, body["p_expected_generation"])
                assertTrue(body["p_game"] is kotlinx.serialization.json.JsonObject)
                assertEquals(1, (body["p_problems"] as kotlinx.serialization.json.JsonArray).size)
                assertTrue(!request.bodyText().contains("非公開名"))
                if (mode == "missing-rpc") respond("""{"code":"PGRST202","message":"missing"}""", HttpStatusCode.NotFound, jsonHeaders)
                else respond("""{"status":"${if (mode == "wrong-generation") "applied" else mode}","generation":"${if (mode == "wrong-generation") "different" else generation}","private_written":${mode == "applied"}}""", HttpStatusCode.OK, jsonHeaders)
            }
            val game = dev.miyado.shogisupplement.db.GameRecord(
                id = 1, fileName = "test.kif", contentHash = "a".repeat(64), moveCount = 1,
                senteName = null, goteName = null, analyzedAt = 1, rating = 1000, coefVersion = "v1",
                kifText = "先手：非公開名\n1 ７六歩(77)\n2 投了",
            )
            val snapshot = dev.miyado.shogisupplement.db.GameRepository.AnalysisUploadSnapshot(game, emptyList(), listOf(problem()), 1, generation = generation)
            val outcome = repository(engine).uploadAnalysis("user-1", snapshot,
                dev.miyado.shogisupplement.db.GameRepository.AnalysisSyncTarget(null))
            when (mode) {
                "applied" -> assertEquals(UploadRepository.AnalysisUploadOutcome.Applied(true), outcome)
                "already_applied" -> assertEquals(UploadRepository.AnalysisUploadOutcome.Applied(false), outcome)
                "conflict" -> assertEquals(UploadRepository.AnalysisUploadOutcome.Conflict(generation), outcome)
                "superseded" -> assertEquals(UploadRepository.AnalysisUploadOutcome.Superseded(generation), outcome)
                else -> assertTrue(outcome is UploadRepository.AnalysisUploadOutcome.Failure, mode)
            }
            assertEquals(1, requests.size, mode)
        }
    }

    @Test
    fun `解析同期状態は世代なしと削除済みと取得失敗を区別する`() = runTest {
        for (mode in listOf("legacy", "deleted", "failure")) {
            val engine = MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("rpc/get_analysis_sync_state"))
                if (mode == "failure") respond("{}", HttpStatusCode.InternalServerError, jsonHeaders)
                else respond("""{"generation":${if (mode == "deleted") "\"tombstone\"" else "null"},"deleted":${mode == "deleted"}}""", HttpStatusCode.OK, jsonHeaders)
            }
            val result = repository(engine).getAnalysisRemoteState("hash")
            assertEquals(when (mode) {
                "legacy" -> UploadRepository.AnalysisRemoteState(null, false)
                "deleted" -> UploadRepository.AnalysisRemoteState("tombstone", true)
                else -> null
            }, result)
        }
    }

    @Test
    fun `検討だけの送信は暗号文CASを使い競合時に成功扱いしない`() = runTest {
        val secret = ByteArray(16) { it.toByte() }
        val key = dev.miyado.shogisupplement.crypto.TransferSecretKeys.deriveEncKey(secret)
        val store = object : TransferSecretStore {
            override suspend fun load() = secret
            override suspend fun save(secret: ByteArray) = error("must not replace keys")
            override suspend fun clear() = Unit
        }
        for (mode in listOf("success", "cas-conflict", "remote-conflict", "already-saved", "legacy-empty", "legacy-notes", "legacy-variation", "variation-conflict", "original-note-conflict", "position-conflict", "long-ciphertext")) {
            val original = (if (mode in listOf("legacy-notes", "position-conflict")) "*元からあるメモ\n" else "") + "1 ７六歩(77)\n2 投了" +
                (if (mode in listOf("legacy-variation", "variation-conflict")) "\n変化：1手\n1 ２六歩(27)\n2 投了" else "")
            val desired = "$original\n*新しい検討"
            val remoteKif = when (mode) {
                "remote-conflict" -> "$original\n*他端末の検討"
                "already-saved" -> desired
                "long-ciphertext" -> "$original\n*" + "あ".repeat(4096)
                else -> null
            }
            val baseFields = dev.miyado.shogisupplement.kifu.StudyKifuBackup.decompose(original, remoteKif).private
            val privateFields = when (mode) {
                "legacy-empty", "legacy-notes" -> baseFields.copy(positionNotes = null)
                "legacy-variation" -> baseFields.copy(positionNotes = null, variationKif = null)
                "variation-conflict" -> baseFields.copy(variationKif = "変化：1手\n1 ５六歩(57)\n2 投了")
                "original-note-conflict" -> baseFields.copy(comments = listOf("*別の原文メモ"))
                "position-conflict" -> baseFields.copy(positionNotes = mapOf(1 to dev.miyado.shogisupplement.kifu.KifuPositionNotes(listOf("元からあるメモ"))))
                else -> baseFields
            }
            val ciphertext = kotlin.io.encoding.Base64.encode(dev.miyado.shogisupplement.crypto.PrivateEncCodec.encrypt(key, privateFields, "hash".encodeToByteArray()))
            val requests = mutableListOf<HttpRequestData>()
            val engine = MockEngine { request ->
                requests += request
                if (request.method == HttpMethod.Get) {
                    assertTrue(request.url.encodedPath.endsWith("uploaded_games_current"))
                    respond("""[{"private_enc":"$ciphertext"}]""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    assertEquals(HttpMethod.Post, request.method)
                    assertTrue(request.url.encodedPath.endsWith("rpc/update_study_private_enc"))
                    assertTrue(request.url.toString().length < 256)
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText()) as kotlinx.serialization.json.JsonObject
                    assertEquals(setOf("p_private_enc", "p_expected_private_enc", "p_content_hash"), json.keys)
                    assertEquals(ciphertext, (json.getValue("p_expected_private_enc") as kotlinx.serialization.json.JsonPrimitive).content)
                    val encoded = json.getValue("p_private_enc") as kotlinx.serialization.json.JsonPrimitive
                    val decoded = dev.miyado.shogisupplement.crypto.PrivateEncCodec.decrypt(key, kotlin.io.encoding.Base64.decode(encoded.content), "hash".encodeToByteArray())
                    assertEquals(desired, decoded.studyKif)
                    respond(if (mode == "cas-conflict") "false" else "true", HttpStatusCode.OK, jsonHeaders)
                }
            }
            val client = createSupabaseClient("https://example.supabase.co", "anon-key") { httpEngine = engine; install(Postgrest) }
            val result = SupabaseUploadRepository(client, store).uploadStudy("user",
                dev.miyado.shogisupplement.db.GameRepository.StudyUploadSnapshot(1, "hash", original, desired, if (mode == "long-ciphertext") remoteKif!! else original, 1))
            if (mode in listOf("success", "already-saved", "legacy-empty", "legacy-notes", "legacy-variation", "long-ciphertext")) assertEquals(UploadResult.Success, result, mode)
            else assertTrue(result is UploadResult.Failure)
            assertEquals(if (mode in listOf("remote-conflict", "already-saved", "original-note-conflict", "position-conflict", "variation-conflict")) 1 else 2, requests.size, mode)
            client.close()
        }
    }

    @Test
    fun `棋譜アップロードは検討KIFを暗号化列だけへ含める`() = runTest {
        val original = "先手：非公開名\n1 ７六歩(77)\n2 投了"
        val edited = original.replace("2 投了", "*非公開の検討メモ\n2 投了")
        var request: HttpRequestData? = null
        val engine = MockEngine { received ->
            request = received
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
        }
        val store = object : TransferSecretStore {
            var bytes: ByteArray? = null
            override suspend fun load() = bytes
            override suspend fun save(secret: ByteArray) { bytes = secret }
            override suspend fun clear() { bytes = null }
        }
        val client = createSupabaseClient("https://example.supabase.co", "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }
        val game = dev.miyado.shogisupplement.db.GameRecord(
            1, "test.kif", "test-hash", 1, null, null, 1, 1500,
            coefVersion = "test", kifText = original, studyKif = edited,
        )
        assertEquals(UploadResult.Success, SupabaseUploadRepository(client, store).uploadGame("user-1", game, emptyList()))
        val body = requireNotNull(request).bodyText()
        assertTrue(!body.contains("非公開"))
        assertTrue(!body.contains("study_kif"))
        val root = kotlinx.serialization.json.Json.parseToJsonElement(body)
        val payload = if (root is kotlinx.serialization.json.JsonArray) root.first() else root
        val encoded = (payload as kotlinx.serialization.json.JsonObject).getValue("private_enc") as kotlinx.serialization.json.JsonPrimitive
        val secrets = dev.miyado.shogisupplement.crypto.TransferSecretManager.getOrCreateSecrets(store)
        val key = dev.miyado.shogisupplement.crypto.TransferSecretKeys.deriveEncKey(secrets.encSecret)
        val restored = dev.miyado.shogisupplement.crypto.PrivateEncCodec.decrypt(
            key, kotlin.io.encoding.Base64.decode(encoded.content), game.contentHash.encodeToByteArray(),
        )
        assertEquals(original, restored.studyOriginalKif)
        assertEquals(edited, restored.studyKif)
        client.close()
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class FakeTransferSecretStore : TransferSecretStore {
        override suspend fun load(): ByteArray? = null
        override suspend fun save(secret: ByteArray) = Unit
        override suspend fun clear() = Unit
    }

    private fun repository(engine: MockEngine): SupabaseUploadRepository {
        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "anon-key",
        ) {
            httpEngine = engine
            install(Postgrest)
        }
        return SupabaseUploadRepository(client, FakeTransferSecretStore())
    }

    private fun problem(ply: Long = 41L) = BlunderRecord(
        id = 7L,
        gameId = 3L,
        ply = ply,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        sfenBefore = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
        category = "駒損",
        diffMaterial = -11L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "テスト問題",
        problemType = "手筋",
        priority = 2.5,
        secondUsi = "2g2f",
        secondCp = 123L,
    )

    private fun HttpRequestData.bodyText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    @Test
    fun `問題が空ならHTTPリクエストを送らず成功する`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
        }

        val result = repository(engine).syncDrillProblems("user-1", "hash-1", emptyList())

        assertEquals(UploadResult.Success, result)
        assertEquals(0, requestCount)
    }

    @Test
    fun `旧方式の問題upsertは複合キーで重複を無視し全ペイロードを送る`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { received ->
            request = received
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
        }

        val result = repository(engine).syncDrillProblems("user-1", "hash-1", listOf(problem()))

        assertEquals(UploadResult.Success, result)
        val sent = request ?: error("request was not sent")
        assertEquals(HttpMethod.Post, sent.method)
        assertTrue(sent.url.toString().contains("drill_problems"))
        assertEquals("user_id,content_hash,ply", sent.url.parameters["on_conflict"])
        assertTrue(sent.headers[HttpHeaders.Prefer].orEmpty().contains("resolution=ignore-duplicates"))
        val body = sent.bodyText()
        assertTrue(body.contains("\"user_id\":\"user-1\""))
        assertTrue(body.contains("\"content_hash\":\"hash-1\""))
        assertTrue(body.contains("\"ply\":41"))
        assertTrue(body.contains("\"side\":\"sente\""))
        assertTrue(body.contains("\"sfen_before\":"))
        assertTrue(body.contains("\"move_usi\":\"B*3d\""))
        assertTrue(body.contains("\"best_usi\":\"2f6f\""))
        assertTrue(body.contains("\"loss_wp\":0.225"))
        assertTrue(body.contains("\"category\":\"駒損\""))
        assertTrue(body.contains("\"verdict\":\"○ 出題対象\""))
        assertTrue(body.contains("\"note\":\"テスト問題\""))
        assertTrue(body.contains("\"problem_type\":\"手筋\""))
        assertTrue(body.contains("\"priority\":2.5"))
        assertTrue(body.contains("\"second_usi\":\"2g2f\""))
        assertTrue(body.contains("\"second_cp\":123"))
    }

    @Test
    fun `解答送信は問題upsert問題IDselect解答upsertの順に行う`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { received ->
            requests += received
            bodies += received.bodyText()
            when (requests.size) {
                1, 3 -> respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
                2 -> respond(
                    content = ByteReadChannel("[{\"id\":\"problem-uuid\"}]"),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                else -> error("unexpected request ${requests.size}")
            }
        }

        val result = repository(engine).uploadDrillAttempt(
            userId = "user-1",
            contentHash = "hash-1",
            problem = problem(),
            attempt = DrillAttemptUpload(
                syncId = "attempt-uuid",
                userMoveUsi = "B*3d",
                isCorrect = false,
                lossWp = 0.5,
                attemptedAt = 1_780_000_000L,
            ),
        )

        assertEquals(UploadResult.Success, result)
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Post), requests.map { it.method })
        assertTrue(requests[0].url.toString().contains("drill_problems"))
        assertTrue(requests[1].url.toString().contains("drill_problems"))
        assertTrue(requests[2].url.toString().contains("drill_attempts"))
        assertEquals("eq.user-1", requests[1].url.parameters["user_id"])
        assertEquals("eq.hash-1", requests[1].url.parameters["content_hash"])
        assertEquals("eq.41", requests[1].url.parameters["ply"])
        assertEquals("user_id,client_attempt_id", requests[2].url.parameters["on_conflict"])
        assertTrue(requests[2].headers[HttpHeaders.Prefer].orEmpty().contains("resolution=ignore-duplicates"))
        assertTrue(bodies[2].contains("\"user_id\":\"user-1\""))
        assertTrue(bodies[2].contains("\"problem_id\":\"problem-uuid\""))
        assertTrue(bodies[2].contains("\"client_attempt_id\":\"attempt-uuid\""))
        assertTrue(bodies[2].contains("\"user_move_usi\":\"B*3d\""))
        assertTrue(bodies[2].contains("\"is_correct\":false"))
        assertTrue(bodies[2].contains("\"loss_wp\":0.5"))
        assertTrue(bodies[2].contains("\"attempted_at\":\"${Instant.fromEpochSeconds(1_780_000_000L)}\""))
    }

    @Test
    fun `解答upsertがPostgreSQLのunique_violationコードを返せば既存行として成功扱いする`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            when (requestCount) {
                1 -> respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
                2 -> respond(
                    content = ByteReadChannel("[{\"id\":\"problem-uuid\"}]"),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                3 -> respond(
                    content = ByteReadChannel("{\"code\":\"23505\",\"message\":\"duplicate key\"}"),
                    status = HttpStatusCode.Conflict,
                    headers = jsonHeaders,
                )
                else -> error("unexpected request $requestCount")
            }
        }

        val result = repository(engine).uploadDrillAttempt(
            userId = "user-1",
            contentHash = "hash-1",
            problem = problem(),
            attempt = DrillAttemptUpload("attempt-uuid", "B*3d", false, null, 1_780_000_000L),
        )

        assertEquals(UploadResult.Success, result)
        assertEquals(3, requestCount)
    }

    @Test
    fun `解答upsertがcodeを持たない409を返せば重複と誤判定せずFailureになる`() = runTest {
        // 外部キー違反（23503）等もPostgRESTはHTTP 409を返すため、ステータスコードや
        // メッセージ中の"409"という文字列だけでは重複と判別できない。ここではcodeフィールドを
        // 持たないレスポンスで、それらが誤ってSuccess/Duplicateに丸められないことを確認する。
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            when (requestCount) {
                1 -> respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
                2 -> respond(
                    content = ByteReadChannel("[{\"id\":\"problem-uuid\"}]"),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                3 -> respond(
                    content = ByteReadChannel("{\"message\":\"insert or update on table \\\"drill_attempts\\\" violates foreign key constraint, request id 409\"}"),
                    status = HttpStatusCode.Conflict,
                    headers = jsonHeaders,
                )
                else -> error("unexpected request $requestCount")
            }
        }

        val result = repository(engine).uploadDrillAttempt(
            userId = "user-1",
            contentHash = "hash-1",
            problem = problem(),
            attempt = DrillAttemptUpload("attempt-uuid", "B*3d", false, null, 1_780_000_000L),
        )

        assertTrue(result is UploadResult.Failure, "expected Failure but was $result")
        assertEquals(3, requestCount)
    }
}
