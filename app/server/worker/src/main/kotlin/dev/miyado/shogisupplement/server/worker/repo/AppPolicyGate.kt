package dev.miyado.shogisupplement.server.worker.repo

import dev.miyado.shogisupplement.policy.AppPolicyRow
import dev.miyado.shogisupplement.policy.ForceUpdateJudge
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Duration
import java.time.Instant

// Why not 取得失敗時に一律ブロック: 配信側の障害で全ユーザーが解析不能になる事故のほうが、
// ビルド制限を一時的に見逃す被害より大きい（クライアント側の判定と同じ優先順位）。
interface AppPolicyGate {
    /** true = build < min_build（426を返すべき）。 */
    suspend fun isBlocked(platform: String, build: Int): Boolean

    companion object {
        // Why not 必須パラメータのみにする: 既定実装を用意しないと既存の呼び出し側を
        // すべて更新しないとコンパイルが通らなくなるため、常に非ブロックの実装を既定値にする。
        val AlwaysAllow: AppPolicyGate = object : AppPolicyGate {
            override suspend fun isBlocked(platform: String, build: Int) = false
        }
    }
}

/**
 * `app_policy` をservice_roleでPostgRESTから取得し、[ForceUpdateJudge]（:analysis。
 * クライアント側判定と同一の純粋関数）で判定するゲート。
 *
 * 取得結果はプロセス内メモリに[cacheTtlMs]だけ保持し、毎リクエストのDB往復を避ける
 * （app_policyは数行かつ管理画面からの変更頻度も低いため、TTLの粗さは許容できる）。
 * 期限切れ後の取得が失敗した場合は直近のキャッシュへフォールバックし、キャッシュも
 * 無ければfail-open（非ブロック）で返す。
 */
class SupabaseAppPolicyGate(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtlMs: Long = 60_000,
) : AppPolicyGate {

    @Serializable
    private data class AppPolicyDto(
        val platform: String,
        @SerialName("min_build") val minBuild: Int? = null,
        @SerialName("store_url") val storeUrl: String? = null,
        val message: String? = null,
    )

    private data class CacheEntry(val rows: List<AppPolicyRow>, val fetchedAt: Instant)

    // Why not Mutex: 書き込みは不変オブジェクトの参照差し替えのみ（JVM上で単一操作としてvisible）。
    // TTL境界での多重フェッチが稀に起きても、結果はどれも同じ行を返すだけで実害が無いため、
    // 直列化のコストをかけてまで単一飛行化する必要はない。
    @Volatile
    private var cache: CacheEntry? = null

    override suspend fun isBlocked(platform: String, build: Int): Boolean {
        val rows = rows() ?: return false
        return ForceUpdateJudge.evaluate(platform, build, rows).blocked
    }

    private suspend fun rows(): List<AppPolicyRow>? {
        val cached = cache
        if (cached != null && Duration.between(cached.fetchedAt, clock.instant()).toMillis() < cacheTtlMs) {
            return cached.rows
        }
        val fetched = runCatching { fetch() }.getOrNull()
        if (fetched == null) {
            // 取得失敗: 期限切れでも直近のキャッシュがあればそれを使う（クライアント側と同じ優先順位）。
            return cached?.rows
        }
        cache = CacheEntry(fetched, clock.instant())
        return fetched
    }

    private suspend fun fetch(): List<AppPolicyRow> {
        val response = httpClient.get(restUrl(supabaseUrl, "app_policy")) {
            parameter("select", "platform,min_build,store_url,message")
            supabaseServiceRoleHeaders(serviceRoleKey)
        }
        check(response.status.isSuccess()) { "app_policy query failed: ${response.status}" }
        val rows: List<AppPolicyDto> = response.body()
        return rows.map { AppPolicyRow(it.platform, it.minBuild, it.storeUrl, it.message) }
    }
}
