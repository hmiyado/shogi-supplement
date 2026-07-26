package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ベータの既定クォータ（行が無いユーザーへの既定値）。
const val DEFAULT_DAILY_QUOTA = 30

// 行が無ければ [DEFAULT_DAILY_QUOTA]。
interface QuotaLimitRepository {
    suspend fun dailyLimit(userId: String): Int
}

class SupabaseQuotaLimitRepository(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
) : QuotaLimitRepository {

    @Serializable
    private data class QuotaRow(@SerialName("daily_limit") val dailyLimit: Int)

    override suspend fun dailyLimit(userId: String): Int {
        val response = httpClient.get(restUrl(supabaseUrl, "quota_limits")) {
            parameter("user_id", "eq.$userId")
            parameter("select", "daily_limit")
            supabaseServiceRoleHeaders(serviceRoleKey)
        }
        check(response.status.isSuccess()) { "quota_limits query failed: ${response.status}" }
        val rows: List<QuotaRow> = response.body()
        return rows.firstOrNull()?.dailyLimit ?: DEFAULT_DAILY_QUOTA
    }
}
