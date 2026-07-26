package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 行があれば即403（認可順序: JWT検証の次・クォータ判定の前。不変条件）。
interface BanRepository {
    suspend fun isBanned(userId: String): Boolean
}

class SupabaseBanRepository(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
) : BanRepository {

    @Serializable
    private data class BanRow(@SerialName("user_id") val userId: String)

    override suspend fun isBanned(userId: String): Boolean {
        val response = httpClient.get(restUrl(supabaseUrl, "user_bans")) {
            parameter("user_id", "eq.$userId")
            parameter("select", "user_id")
            supabaseServiceRoleHeaders(serviceRoleKey)
        }
        check(response.status.isSuccess()) { "user_bans query failed: ${response.status}" }
        val rows: List<BanRow> = response.body()
        return rows.isNotEmpty()
    }
}
