package dev.miyado.shogisupplement.gameshare

import dev.miyado.shogisupplement.api.gameshare.GameShareSnapshotJson
import dev.miyado.shogisupplement.api.gameshare.GameShareSnapshotValidator
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class StaticGameShareReader(
    private val supabaseUrl: String,
    private val httpClient: HttpClient,
) : GameShareReader {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(id: String): GameShareResult<GameShareSnapshot> {
        if (!GameShareSnapshotValidator.isValidShareId(id)) return GameShareResult.Invalid
        if (supabaseUrl.isBlank()) return GameShareResult.Failure("storage not configured")
        return try {
            val response = httpClient.get("${supabaseUrl.trimEnd('/')}/storage/v1/object/public/game-shares/$id.json")
            when (response.status) {
                HttpStatusCode.OK -> {
                    val snapshot = json.decodeFromString(GameShareSnapshotJson.serializer(), response.bodyAsText())
                    if (snapshot.id != id) return GameShareResult.Invalid
                    val validated = GameShareSnapshotValidator.validate(
                        dev.miyado.shogisupplement.api.gameshare.CreateGameShareRequest(
                            version = snapshot.version, gameKey = "public", initialSfen = snapshot.initialSfen,
                            mainline = snapshot.mainline, selectedPly = snapshot.selectedPly, branch = snapshot.branch,
                            userSide = snapshot.userSide, flip = snapshot.flip, winner = snapshot.winner,
                            endReason = snapshot.endReason,
                        ),
                    ) ?: return GameShareResult.Invalid
                    GameShareResult.Success(GameShareSnapshot(
                        id = id, initialSfen = validated.initialSfen, mainline = validated.mainline,
                        selectedPly = validated.selectedPly,
                        branch = validated.branch?.let { GameShareBranch(it.originPly, it.moves) },
                        userSide = validated.userSide, flip = validated.flip,
                        winner = validated.winner, endReason = validated.endReason,
                    ))
                }
                HttpStatusCode.NotFound, HttpStatusCode.BadRequest -> GameShareResult.NotFound
                else -> GameShareResult.Failure("unexpected storage status ${response.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GameShareResult.Failure(e.message ?: "storage read failed")
        }
    }
}
