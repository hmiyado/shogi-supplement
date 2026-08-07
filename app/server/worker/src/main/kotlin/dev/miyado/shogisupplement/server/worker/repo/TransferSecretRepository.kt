package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `user_transfer_secrets.key_auth_hash`（SHA-256(K_auth)のBase64）からuser_idを引く。 */
interface TransferSecretRepository {
    /** 一致する行が無ければnull（呼び出し側は情報を漏らさない汎用エラーとして扱う）。 */
    suspend fun findUserId(keyAuthHashBase64: String): String?
}

class SupabaseTransferSecretRepository(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
) : TransferSecretRepository {

    @Serializable
    private data class TransferSecretRow(@SerialName("user_id") val userId: String)

    override suspend fun findUserId(keyAuthHashBase64: String): String? {
        val response = httpClient.get(restUrl(supabaseUrl, "user_transfer_secrets")) {
            parameter("key_auth_hash", "eq.$keyAuthHashBase64")
            parameter("select", "user_id")
            supabaseServiceRoleHeaders(serviceRoleKey)
        }
        check(response.status.isSuccess()) { "user_transfer_secrets query failed: ${response.status}" }
        val rows: List<TransferSecretRow> = response.body()
        return rows.firstOrNull()?.userId
    }
}
