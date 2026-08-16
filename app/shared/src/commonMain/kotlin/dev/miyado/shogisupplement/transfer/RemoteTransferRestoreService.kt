package dev.miyado.shogisupplement.transfer

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.transfer.TransferRequest
import dev.miyado.shogisupplement.api.transfer.TransferSessionJson
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.crypto.TransferCode
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.util.currentEpochSeconds
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecrets
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.policy.currentBuildNumber
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json

/**
 * [TransferRestoreService] のSupabase Worker実装。
 *
 * 成功時のみ副作用を確定させる: [AuthRepository.importSession] が成功して初めて
 * [TransferSecretStore.save] でSを上書きする。逆順（先にSave）だと、importSessionが
 * 失敗した場合にSだけが新アカウントを指し、ログインセッションは旧アカウントのまま
 * ……という端末内で矛盾した状態になり得るため。
 */
@OptIn(ExperimentalEncodingApi::class)
class RemoteTransferRestoreService(
    private val baseUrl: String,
    private val authRepository: AuthRepository,
    private val transferSecretStore: TransferSecretStore,
    private val settingsRepository: SettingsRepository,
    private val platform: String,
    private val httpClient: HttpClient = HttpClient(),
    private val appCheckTokenProvider: (suspend () -> String?)? = null,
) : TransferRestoreService {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun restore(code: String): TransferRestoreResult {
        val secrets = TransferCode.decodeSecrets(code) ?: return TransferRestoreResult.InvalidCode
        val kAuth = TransferSecretKeys.deriveAuthKey(secrets.authSecret)
        val appCheckToken = appCheckTokenProvider?.invoke()

        val response = try {
            httpClient.post("$baseUrl/v1/transfer") {
                if (appCheckToken != null) {
                    header(ApiHeaders.APP_CHECK, appCheckToken)
                }
                header(ApiHeaders.APP_PLATFORM, platform)
                header(ApiHeaders.APP_BUILD, currentBuildNumber().toString())
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(TransferRequest.serializer(), TransferRequest(kAuth = Base64.encode(kAuth))))
            }
        } catch (e: Exception) {
            return TransferRestoreResult.NetworkError(e.message ?: "connection failed")
        }

        return when (response.status) {
            HttpStatusCode.OK -> applySession(response, secrets)
            HttpStatusCode.NotFound -> TransferRestoreResult.NotFound
            HttpStatusCode.TooManyRequests -> TransferRestoreResult.RateLimited
            HttpStatusCode.UpgradeRequired -> TransferRestoreResult.UpgradeRequired
            else -> TransferRestoreResult.NetworkError("unexpected status ${response.status}")
        }
    }

    private suspend fun applySession(response: HttpResponse, secrets: TransferSecrets): TransferRestoreResult {
        val session = try {
            json.decodeFromString(TransferSessionJson.serializer(), response.bodyAsText())
        } catch (e: Exception) {
            return TransferRestoreResult.NetworkError(e.message ?: "malformed response")
        }
        val imported = authRepository.importSession(session.accessToken, session.refreshToken)
        return imported.fold(
            onSuccess = {
                transferSecretStore.save(secrets.toStored())
                // 復元でアカウントを手にした端末は、作らないと決めた状態から戻す。
                // 戻さないとサーバーに解析結果があっても端末内で解析し直すことになる。
                settingsRepository.saveAccountDeclined(false)
                settingsRepository.saveAutoUpload(true)
                if (settingsRepository.getConsentAcceptedAt() == null) {
                    settingsRepository.saveConsentAcceptedAt(currentEpochSeconds())
                }
                TransferRestoreResult.Success
            },
            onFailure = { e -> TransferRestoreResult.SessionImportFailed(e.message ?: "session import failed") },
        )
    }
}
