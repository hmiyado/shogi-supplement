package dev.miyado.shogisupplement.download

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.crypto.PrivateEncCodec
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecrets
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.kifu.KifuReconstructor
import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.kifu.PublicKifuFields
import dev.miyado.shogisupplement.kifu.kifuWinner
import dev.miyado.shogisupplement.text.AppStrings
import io.github.jan.supabase.SupabaseClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * [dev.miyado.shogisupplement.download.GameDownloadService] と異なりローカルDBへ保存せず、
 * 一覧表示に要る範囲だけをメモリ上の [GameRecord] へ詰めて返す（KIF全文の再構成はしない）。
 */
class SupabaseGameSummaryService(
    supabase: SupabaseClient,
    private val transferSecretStore: TransferSecretStore,
    private val authRepository: AuthRepository,
) : GameSummaryService {

    private val remoteSource = UploadedGamesRemoteSource(supabase)

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun listGames(): GameSummaryOutcome {
        if (authRepository.currentUser.value == null) return GameSummaryOutcome.NotAuthenticated
        val stored = transferSecretStore.load() ?: return GameSummaryOutcome.NoSecret
        val secrets = TransferSecrets.fromStored(stored) ?: return GameSummaryOutcome.NoSecret
        val kEnc = TransferSecretKeys.deriveEncKey(secrets.encSecret)

        val rows = try {
            remoteSource.fetchAllRows()
        } catch (e: Exception) {
            return GameSummaryOutcome.NetworkError(e.message ?: "communication failed")
        }

        val games = rows.mapIndexed { index, row -> row.toGameRecord(id = -(index + 1).toLong(), kEnc) }
            .sortedByDescending { it.analyzedAt }
        return GameSummaryOutcome.Loaded(games)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getDetail(contentHash: String): GameDetailOutcome {
        if (authRepository.currentUser.value == null) return GameDetailOutcome.NotAuthenticated
        val stored = transferSecretStore.load() ?: return GameDetailOutcome.NoSecret
        val secrets = TransferSecrets.fromStored(stored) ?: return GameDetailOutcome.NoSecret
        val kEnc = TransferSecretKeys.deriveEncKey(secrets.encSecret)

        val row = try {
            remoteSource.fetchRowByContentHash(contentHash) ?: return GameDetailOutcome.NotFound
        } catch (e: Exception) {
            return GameDetailOutcome.NetworkError(e.message ?: "communication failed")
        }

        val game = row.toGameRecord(id = 1L, kEnc, includeKifText = true)
        val reports = row.analysisJson.orEmpty().map { it.toBlunderRecord(row.movesUsi) }
        return GameDetailOutcome.Loaded(GameDetail(game, reports))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun UploadedGameRow.toGameRecord(
        id: Long,
        kEnc: ByteArray,
        includeKifText: Boolean = false,
    ): GameRecord {
        val privateFields = privateEnc?.let { encoded ->
            runCatching {
                val blob = Base64.decode(encoded)
                PrivateEncCodec.decrypt(kEnc, blob, contentHash.encodeToByteArray())
            }.getOrNull()
        }
        val effectiveMoveCount = moveCount ?: movesUsi.size
        val kifText = if (includeKifText) {
            val public = PublicKifuFields(
                movesUsi = movesUsi,
                moveTimesSeconds = moveTimes.orEmpty(),
                headers = headers.orEmpty(),
                result = result,
                source = KifuSource.entries.firstOrNull { it.wireValue == sourcePlace } ?: KifuSource.OTHER,
            )
            KifuReconstructor.reconstruct(public, privateFields, userSide = side)
        } else {
            null
        }
        return GameRecord(
            id = id,
            fileName = AppStrings.restoredGameFileName(headers?.get("開始日時")),
            contentHash = contentHash,
            moveCount = effectiveMoveCount.toLong(),
            senteName = privateFields?.senteName,
            goteName = privateFields?.goteName,
            analyzedAt = parseEpochSeconds(startedAt ?: createdAt),
            rating = (estimatedRating ?: 0).toLong(),
            coefVersion = coefVersion.orEmpty(),
            kifText = kifText,
            movesUsi = movesUsi,
            userSide = side,
            ratingService = ratingService,
            ratingRaw = ratingRaw?.toLong(),
            ratingRule = ratingRule,
            sourcePlace = sourcePlace,
            gameWinner = kifuWinner(result, effectiveMoveCount),
            endReason = result,
        )
    }

    /**
     * サーバーには評価値・読み筋を保存していないため、それらのフィールドはデフォルト値のまま
     * （[GameDetail]のKDoc参照）。[ply]は1始まりで`movesUsi[ply - 1]`がその手自体を指すため、
     * [sfenBefore]（直前局面）は`ply - 1`手までしか適用せず再計算する。
     */
    private fun BlunderReportJson.toBlunderRecord(movesUsi: List<String>): BlunderRecord {
        val board = ShogiBoard()
        val limit = (ply.toInt() - 1).coerceIn(0, movesUsi.size)
        for (i in 0 until limit) {
            if (runCatching { board.push(ShogiMove.fromUsi(movesUsi[i])) }.isFailure) break
        }
        return blunderRecordWithSfen(board.toSfen())
    }

    private fun BlunderReportJson.blunderRecordWithSfen(sfenBefore: String) = BlunderRecord(
        id = ply,
        gameId = 1L,
        ply = ply,
        side = side,
        moveUsi = moveUsi,
        bestUsi = bestUsi,
        lossWp = lossWp,
        sfenBefore = sfenBefore,
        category = category,
        diffMaterial = 0,
        punishChecks = 0,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = verdict,
        note = note,
        problemType = problemType,
        priority = priority,
    )

    private fun parseEpochSeconds(iso: String?): Long =
        iso?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() } ?: 0L
}
