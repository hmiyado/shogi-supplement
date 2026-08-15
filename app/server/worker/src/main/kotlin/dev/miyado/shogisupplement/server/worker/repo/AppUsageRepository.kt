package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 利用中のアプリ版の記録。どの版がどれだけ使われているかを知る唯一の経路
 * （Supabaseへの直接リクエストにはワーカー宛てのヘッダが乗らない）。
 */
interface AppUsageRepository {
    suspend fun record(userId: String, platform: String, build: Int)
}

class SupabaseAppUsageRepository(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
) : AppUsageRepository {

    @Serializable
    private data class UsageRow(
        @SerialName("user_id") val userId: String,
        val platform: String,
        val build: Int,
        @SerialName("updated_at") val updatedAt: String,
    )

    /**
     * Why not 失敗を伝播させる: 記録は解析そのものの成否と関係がなく、
     * ここで落とすと解析が使えなくなる。取りこぼしは分布の精度に影響するだけ。
     */
    override suspend fun record(userId: String, platform: String, build: Int) {
        runCatching {
            httpClient.post(restUrl(supabaseUrl, "app_usage")) {
                supabaseServiceRoleHeaders(serviceRoleKey)
                // 競合の解決対象は (user_id, platform) の複合主キー。
                header("Prefer", "resolution=merge-duplicates")
                contentType(ContentType.Application.Json)
                setBody(
                    listOf(
                        UsageRow(
                            userId = userId,
                            platform = platform,
                            build = build,
                            updatedAt = java.time.Instant.now().toString(),
                        ),
                    ),
                )
            }
        }
    }
}
