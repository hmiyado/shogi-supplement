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
 * GoTrue Admin APIで「合成email付与 → generate_link(magiclink) → verify(token_hash)」の
 * 3手順を実行し、旧user_idのセッションを発行する。
 *
 * Why not user_idでセッションを直接発行するAPI: GoTrueにその公式エンドポイントが無く、
 * このプロジェクトは非対称JWT署名鍵で運用しているためワーカー自身によるJWT自己署名も
 * 不可能（秘密鍵を持たない）。唯一の実現手段であるmagiclinkフローはemail必須のため、
 * 対象アカウント（匿名認証のみ）に合成emailを恒久的に付与する（[SYNTHETIC_EMAIL_DOMAIN] は
 * 配送不能なRFC 2606予約TLD）。これによりis_anonymousクレームがfalseへ変わる副作用が
 * 生じる。
 *
 * Why not verifyOtpをクライアント側で実行: hashed_tokenはmagiclinkを完了できる
 * （＝そのユーザーとしてログインできる）強度を持つ実質的な認証情報で、k_authと同様に
 * 露出面を最小化すべき。generate_link→verifyをワーカー内で完結させれば、hashed_tokenが
 * ネットワークに一切出ない。クライアントへはこの関数が返す最終セッションのみを渡す。
 *
 * Why not 呼び出しの度にGET /admin/users/{id}で既存emailを確認してから分岐する: PUTでの
 * email+email_confirm:true設定はそれ自体が冪等（同じ値を再設定するだけ）なので、
 * 分岐を増やすより毎回無条件PUTするほうが単純で同じ結果になる。
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
