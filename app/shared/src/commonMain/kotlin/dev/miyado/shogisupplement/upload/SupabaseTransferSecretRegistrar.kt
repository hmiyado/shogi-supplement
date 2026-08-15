package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecretManager
import dev.miyado.shogisupplement.crypto.TransferSecretRegistrar
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * サーバーへ送るのは認証鍵のSHA-256ハッシュだけで、シークレット自体は送らない。
 *
 * Why not upsert: 登録は毎回無条件に呼ばれる。insertで固定し、2回目以降は
 * unique_violation（23505）を「登録済み」として吸収する。
 */
class SupabaseTransferSecretRegistrar(
    private val supabase: SupabaseClient,
    private val transferSecretStore: TransferSecretStore,
) : TransferSecretRegistrar {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun rotate(): Result<Unit> = runCatching {
        val previous = TransferSecretManager.getOrCreateSecrets(transferSecretStore)
        val rotated = TransferSecretManager.rotateAuthSecret(transferSecretStore)
        val hash = TransferSecretKeys.authKeyHash(TransferSecretKeys.deriveAuthKey(rotated.authSecret))
        try {
            supabase.from("user_transfer_secrets")
                .update(mapOf("key_auth_hash" to Base64.encode(hash))) {
                    filter { eq("user_id", currentUserId()) }
                }
        } catch (e: Throwable) {
            transferSecretStore.save(previous.toStored())
            throw e
        }
        Unit
    }

    private suspend fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("not authenticated")

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun registerIfNeeded(userId: String): Result<Unit> = runCatching {
        val secrets = TransferSecretManager.getOrCreateSecrets(transferSecretStore)
        val authKey = TransferSecretKeys.deriveAuthKey(secrets.authSecret)
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
