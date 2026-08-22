package dev.miyado.shogisupplement.server.worker.auth

import dev.miyado.shogisupplement.server.worker.repo.supabaseJson
import dev.miyado.shogisupplement.server.worker.repo.supabaseServiceRoleHeaders
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class TransferSession(val accessToken: String, val refreshToken: String)

/**
 * 旧user_idのSupabaseセッション（access/refresh token）を発行する。
 *
 * 実装: [GoTrueTransferSessionIssuer]。
 */
interface TransferSessionIssuer {
    suspend fun issueSession(userId: String): TransferSession
}

/**
 * GoTrue Admin APIで合成email、magiclink、verifyを順に実行してセッションを発行する。
 * Why not user_idで直接発行しない: 公式APIも署名鍵もなく、magiclinkが唯一の手段のため。
 * Why notクライアントでverifyしない: hashed_tokenをネットワークへ出さないため。
 * Why not既存emailを毎回取得しない: email設定は冪等なので無条件PUTで足りるため。
 */
class GoTrueTransferSessionIssuer(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
) : TransferSessionIssuer {

    override suspend fun issueSession(userId: String): TransferSession {
        val email = syntheticEmail(userId)
        assignSyntheticEmail(userId, email)
        val hashedToken = generateMagicLinkHashedToken(email)
        return verifyMagicLink(hashedToken)
    }

    private fun syntheticEmail(userId: String): String = "transfer-$userId@$SYNTHETIC_EMAIL_DOMAIN"

    private suspend fun assignSyntheticEmail(userId: String, email: String) {
        val response = httpClient.put(authUrl("admin/users/$userId")) {
            supabaseServiceRoleHeaders(serviceRoleKey)
            contentType(ContentType.Application.Json)
            setBody(supabaseJson.encodeToString(UpdateUserEmailRequest.serializer(), UpdateUserEmailRequest(email = email)))
        }
        check(response.status.isSuccess()) { "admin update user failed: ${response.status}" }
    }

    private suspend fun generateMagicLinkHashedToken(email: String): String {
        val response = httpClient.post(authUrl("admin/generate_link")) {
            supabaseServiceRoleHeaders(serviceRoleKey)
            contentType(ContentType.Application.Json)
            setBody(supabaseJson.encodeToString(GenerateLinkRequest.serializer(), GenerateLinkRequest(email = email)))
        }
        check(response.status.isSuccess()) { "admin generate_link failed: ${response.status}" }
        val body: GenerateLinkResponse = response.body()
        return body.hashedToken
    }

    private suspend fun verifyMagicLink(hashedToken: String): TransferSession {
        val response = httpClient.post(authUrl("verify")) {
            supabaseServiceRoleHeaders(serviceRoleKey)
            contentType(ContentType.Application.Json)
            setBody(supabaseJson.encodeToString(VerifyRequest.serializer(), VerifyRequest(tokenHash = hashedToken)))
        }
        check(response.status.isSuccess()) { "verify magiclink failed: ${response.status}" }
        val body: VerifyResponse = response.body()
        return TransferSession(accessToken = body.accessToken, refreshToken = body.refreshToken)
    }

    private fun authUrl(path: String): String = "${supabaseUrl.trimEnd('/')}/auth/v1/$path"

    @Serializable
    private data class UpdateUserEmailRequest(
        val email: String,
        @SerialName("email_confirm") val emailConfirm: Boolean = true,
    )

    @Serializable
    private data class GenerateLinkRequest(
        val type: String = "magiclink",
        val email: String,
    )

    @Serializable
    private data class GenerateLinkResponse(
        @SerialName("hashed_token") val hashedToken: String,
    )

    @Serializable
    private data class VerifyRequest(
        val type: String = "magiclink",
        @SerialName("token_hash") val tokenHash: String,
    )

    @Serializable
    private data class VerifyResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    companion object {
        // RFC 2606予約TLD（.invalid）: 絶対に配送されないドメインであることが仕様上保証される。
        const val SYNTHETIC_EMAIL_DOMAIN = "transfer.shogi-supplement.invalid"
    }
}
