package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecretManager
import dev.miyado.shogisupplement.crypto.TransferSecretRegistrar
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase postgrest-kt を使った [TransferSecretRegistrar] 実装（user_transfer_secrets）。
 *
 * 端末シークレットS（未生成なら遅延生成。[TransferSecretManager.getOrCreateSecret] 参照）から
 * K_authを導出し、そのSHA-256ハッシュだけをサーバーへ送る（K_auth自体・Sは送らない）。
 *
 * Why not upsert: user_transfer_secretsのRLSはUPDATEを許可していないため、
 * 既存行があるとupsertは失敗する。insertで固定し、2回目以降の呼び出しは
 * unique_violation（23505・primary key = user_id）を「既に登録済み」として吸収する。
 * これにより [registerIfNeeded] を毎回無条件に呼んでも安全。
 */
class SupabaseTransferSecretRegistrar(
    private val supabase: SupabaseClient,
    private val transferSecretStore: TransferSecretStore,
) : TransferSecretRegistrar {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun registerIfNeeded(userId: String): Result<Unit> = runCatching {
        val secret = TransferSecretManager.getOrCreateSecret(transferSecretStore)
        val authKey = TransferSecretKeys.deriveAuthKey(secret)
        val hash = TransferSecretKeys.authKeyHash(authKey)
        val payload = UserTransferSecretPayload(
            userId = userId,
            keyAuthHash = Base64.encode(hash),
        )
        supabase.from("user_transfer_secrets").insert(payload)
        Unit
    }.recoverCatching { e ->
        val msg = e.message ?: ""
        // 23505 = PostgreSQL unique_violation, 409 = HTTP Conflict（既に登録済み）。
        if (msg.contains("23505") || msg.contains("409") || msg.contains("duplicate")) {
            Unit
        } else {
            throw e
        }
    }

    @Serializable
    private data class UserTransferSecretPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("key_auth_hash") val keyAuthHash: String,
    )
}
