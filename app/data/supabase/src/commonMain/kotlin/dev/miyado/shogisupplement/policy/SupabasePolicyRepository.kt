package dev.miyado.shogisupplement.policy

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase postgrest-kt を使った [AppPolicyRepository] 実装。
 *
 * `app_policy` はanonでSELECTのみ可（infra/supabase/migrations/20260803120000_create_app_policy.sql
 * のRLSポリシー参照）。認証状態に関わらず起動直後から呼べる必要がある
 * （匿名サインイン前でも強制アップデート判定は動く必要がある）。
 */
class SupabasePolicyRepository(private val supabase: SupabaseClient) : AppPolicyRepository {

    override suspend fun fetchPolicies(): Result<List<AppPolicyRow>> = runCatching {
        supabase.from("app_policy")
            .select()
            .decodeList<AppPolicyDto>()
            .map { it.toAppPolicyRow() }
    }

    @Serializable
    private data class AppPolicyDto(
        val platform: String,
        @SerialName("min_build") val minBuild: Int? = null,
        @SerialName("store_url") val storeUrl: String? = null,
        val message: String? = null,
    ) {
        fun toAppPolicyRow() = AppPolicyRow(
            platform = platform,
            minBuild = minBuild,
            storeUrl = storeUrl,
            message = message,
        )
    }
}
