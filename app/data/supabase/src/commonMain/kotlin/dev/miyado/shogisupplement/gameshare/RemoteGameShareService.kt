package dev.miyado.shogisupplement.gameshare

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.gameshare.CreateGameShareRequest
import dev.miyado.shogisupplement.api.gameshare.GameShareBranchJson
import dev.miyado.shogisupplement.api.gameshare.GameShareSnapshotJson
import dev.miyado.shogisupplement.api.gameshare.GameShareSnapshotValidator
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.policy.currentBuildNumber
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

class RemoteGameShareService(
    private val baseUrl: String,
    private val authRepository: AuthRepository,
    private val platform: String,
    private val httpClient: HttpClient,
    private val appCheckTokenProvider: (suspend () -> String?)? = null,
    override val sharePageUrl: String? = null,
) : GameShareService {
    override val currentOwner get() = authRepository.currentUser
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(draft: GameShareDraft): GameShareResult<GameShareSummary> {
        val request = draft.toRequest()
        if (GameShareSnapshotValidator.validate(request) == null) return GameShareResult.Invalid
        return networkCall {
            val auth = requestHeaders() ?: return@networkCall GameShareResult.Unauthorized
            val response = httpClient.post("$baseUrl/v1/game-shares") {
                auth(this)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CreateGameShareRequest.serializer(), request))
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val snapshot = decode(response.bodyAsText(), GameShareSnapshotJson.serializer())
                        ?: return@networkCall GameShareResult.Failure("malformed response")
                    GameShareResult.Success(
                        GameShareSummary(
                            id = snapshot.id,
                        ),
                    )
                }
                HttpStatusCode.BadRequest -> GameShareResult.Invalid
                HttpStatusCode.Conflict -> GameShareResult.SourceMissing
                HttpStatusCode.Unauthorized -> GameShareResult.Unauthorized
                HttpStatusCode.UpgradeRequired -> GameShareResult.UpgradeRequired
                HttpStatusCode.TooManyRequests -> GameShareResult.RateLimited
                else -> GameShareResult.Failure("unexpected status ${response.status}")
            }
        }
    }

    private suspend fun <T> networkCall(block: suspend () -> GameShareResult<T>): GameShareResult<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GameShareResult.Failure(e.message ?: "connection failed")
        }

    private fun <T> decode(text: String, deserializer: DeserializationStrategy<T>): T? =
        runCatching { json.decodeFromString(deserializer, text) }.getOrNull()

    private suspend fun requestHeaders(): ((io.ktor.client.request.HttpRequestBuilder) -> Unit)? {
        val accessToken = authRepository.accessToken() ?: return null
        val appCheck = appCheckTokenProvider?.invoke()
        return { builder ->
            builder.header(HttpHeaders.Authorization, "Bearer $accessToken")
            if (appCheck != null) builder.header(ApiHeaders.APP_CHECK, appCheck)
            builder.header(ApiHeaders.APP_PLATFORM, platform)
            builder.header(ApiHeaders.APP_BUILD, currentBuildNumber().toString())
        }
    }

    private fun GameShareDraft.toRequest() = CreateGameShareRequest(
        gameKey = gameKey,
        initialSfen = initialSfen,
        mainline = mainline,
        selectedPly = selectedPly,
        branch = branch?.let { GameShareBranchJson(originPly = it.originPly, moves = it.moves) },
        userSide = userSide,
        flip = flip,
        winner = winner,
        endReason = endReason,
    )

}
