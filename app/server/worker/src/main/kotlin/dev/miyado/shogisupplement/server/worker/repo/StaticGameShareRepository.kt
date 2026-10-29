package dev.miyado.shogisupplement.server.worker.repo

import dev.miyado.shogisupplement.api.gameshare.GameShareSnapshotJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.time.Instant

interface StaticGameShareRepository {
    suspend fun reserve(id: String, owner: String, hash: String): Boolean
    suspend fun upload(snapshot: GameShareSnapshotJson)
    suspend fun finish(id: String, owner: String): Boolean
    suspend fun cleanup(): Int
}

class SupabaseStaticGameShareRepository(
    private val client: HttpClient,
    private val base: String,
    private val key: String,
) : StaticGameShareRepository {
    @Serializable private data class Reserve(val p_id: String, val p_owner: String, val p_hash: String)
    @Serializable private data class Finish(val p_id: String, val p_owner: String)
    @Serializable private data class DeleteObjects(val prefixes: List<String>)
    @Serializable private data class Tombstone(
        @SerialName("object_id") val id: String,
        @SerialName("expires_at") val expiresAt: String,
    )
    @Serializable private data class NextAttempt(@SerialName("next_attempt_at") val time: String)

    override suspend fun reserve(id: String, owner: String, hash: String): Boolean {
        val response = client.post("$base/rest/v1/rpc/reserve_game_share") {
            supabaseServiceRoleHeaders(key); contentType(ContentType.Application.Json)
            setBody(Reserve(id, owner, hash))
        }
        check(response.status.isSuccess()) { "share reservation failed: ${response.status}" }
        return response.body()
    }

    override suspend fun upload(snapshot: GameShareSnapshotJson) {
        val response = client.post("$base/storage/v1/object/game-shares/${snapshot.id}.json") {
            supabaseServiceRoleHeaders(key)
            contentType(ContentType.Application.Json)
            header("x-upsert", "false")
            header(HttpHeaders.CacheControl, "max-age=60")
            setBody(Json.encodeToString(GameShareSnapshotJson.serializer(), snapshot))
        }
        check(response.status.isSuccess()) { "share upload failed: ${response.status}" }
    }

    override suspend fun finish(id: String, owner: String): Boolean {
        val response = client.post("$base/rest/v1/rpc/finish_game_share") {
            supabaseServiceRoleHeaders(key); contentType(ContentType.Application.Json)
            setBody(Finish(id, owner))
        }
        check(response.status.isSuccess()) { "share completion failed: ${response.status}" }
        return response.body()
    }

    override suspend fun cleanup(): Int {
        val collect = client.post("$base/rest/v1/rpc/collect_unfinished_game_shares") {
            supabaseServiceRoleHeaders(key); contentType(ContentType.Application.Json); setBody("{}")
        }
        check(collect.status.isSuccess()) { "share collection failed" }
        val response = client.get("$base/rest/v1/game_share_deletions") {
            supabaseServiceRoleHeaders(key)
            parameter("select", "object_id,expires_at"); parameter("next_attempt_at", "lte.${Instant.now()}")
            parameter("order", "next_attempt_at.asc"); parameter("limit", "100")
        }
        check(response.status.isSuccess()) { "share deletion queue failed" }
        val tombstones: List<Tombstone> = response.body()
        var deleted = 0
        for (row in tombstones) {
            val removed = try {
                val removal = client.delete("$base/storage/v1/object/game-shares") {
                    supabaseServiceRoleHeaders(key); contentType(ContentType.Application.Json)
                    setBody(DeleteObjects(listOf("${row.id}.json")))
                }
                removal.status.isSuccess() || removal.status == HttpStatusCode.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            val expiresAt = Instant.parse(row.expiresAt)
            val updated = if (removed && !expiresAt.isAfter(Instant.now())) {
                client.delete("$base/rest/v1/game_share_deletions") {
                    supabaseServiceRoleHeaders(key)
                    parameter("object_id", "eq.${row.id}")
                }
            } else {
                client.patch("$base/rest/v1/game_share_deletions") {
                    supabaseServiceRoleHeaders(key); contentType(ContentType.Application.Json)
                    parameter("object_id", "eq.${row.id}")
                    val next = if (removed) expiresAt else Instant.now().plusSeconds(300)
                    setBody(NextAttempt(next.toString()))
                }
            }
            check(updated.status.isSuccess()) { "share deletion acknowledgement failed" }
            if (removed) deleted++
        }
        return deleted
    }
}
