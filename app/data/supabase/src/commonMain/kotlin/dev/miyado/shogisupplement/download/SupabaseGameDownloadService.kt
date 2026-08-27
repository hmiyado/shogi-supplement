package dev.miyado.shogisupplement.download

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.crypto.PrivateEncCodec
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecrets
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.kifu.KifuReconstructor
import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.kifu.PublicKifuFields
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.currentEpochSeconds
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 復元された端末シークレットSからK_encを導出し、`uploaded_games.private_enc`を復号する。
 * エンジン選定はプラットフォーム依存のため、解析・保存はコールバックへ委ねる。
 */
class SupabaseGameDownloadService(
    private val supabase: SupabaseClient,
    private val transferSecretStore: TransferSecretStore,
    private val authRepository: AuthRepository,
    private val gameRepository: GameRepository,
) : GameDownloadService {

    private val remoteSource = UploadedGamesRemoteSource(supabase)

    override suspend fun countRemoteGames(): Result<Int> = runCatching {
        val result = supabase.from(UPLOADED_GAMES_TABLE).select(columns = Columns.list("id")) {
            head = true
            count(Count.EXACT)
        }
        (result.countOrNull() ?: 0L).toInt()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun downloadAndImport(
        onProgress: (done: Int, total: Int) -> Unit,
        importGame: suspend (ReconstructedGame) -> GameImportOutcome,
    ): GameDownloadOutcome {
        if (authRepository.currentUser.value == null) return GameDownloadOutcome.NotAuthenticated
        // getOrCreateではなくload: 未生成のSでK_encを新規生成してしまうと、行ごとに
        // 「復号鍵が違うので全滅する」という分かりにくい失敗（failedの積み上がり）になる。
        // NoSecretという別種の結果として区別できるよう、生成せず即座に打ち切る。
        val stored = transferSecretStore.load() ?: return GameDownloadOutcome.NoSecret
        val secrets = TransferSecrets.fromStored(stored) ?: return GameDownloadOutcome.NoSecret
        val kEnc = TransferSecretKeys.deriveEncKey(secrets.encSecret)

        val rows = try {
            remoteSource.fetchAllRows()
        } catch (e: Exception) {
            return GameDownloadOutcome.NetworkError(e.message ?: "communication failed")
        }

        val total = rows.size
        var done = 0
        var succeeded = 0
        var failed = 0
        onProgress(done, total)
        for (row in rows) {
            val existingId = gameRepository.getByHash(row.contentHash)
            if (existingId != null) {
                // 既にこの端末へ取込済み（前回の途中中断からの再開、または復元前の手動取込との
                // 重複）。再アップロード対象に回さないよう、ここでも uploaded_at を確定させる。
                markUploaded(existingId)
                succeeded++
            } else {
                val outcome = runCatching { importRow(row, kEnc, importGame) }.getOrNull()
                if (outcome != null && outcome.success) {
                    outcome.gameId?.let { markUploaded(it) }
                    succeeded++
                } else {
                    // 復号失敗・KIF再構成失敗・保存失敗のいずれも1局の失敗として扱い、
                    // 残りの棋譜の取込を止めない（部分失敗の継続）。
                    failed++
                }
            }
            done++
            onProgress(done, total)
        }
        return GameDownloadOutcome.Completed(total = total, succeeded = succeeded, failed = failed)
    }

    private fun markUploaded(gameId: Long) {
        gameRepository.updateUploadedAt(gameId, currentEpochSeconds())
    }

    /**
     * Why not 秘匿項目を復号できない行を失敗にする: 鍵が食い違うと棋譜そのものを失う。
     * 対局者名等を伏せてでも棋譜を戻す。
     */
    private suspend fun importRow(
        row: UploadedGameRow,
        kEnc: ByteArray,
        importGame: suspend (ReconstructedGame) -> GameImportOutcome,
    ): GameImportOutcome {
        val privateFields = row.privateEnc?.let { encoded ->
            runCatching {
                val blob = Base64.decode(encoded)
                PrivateEncCodec.decrypt(kEnc, blob, row.contentHash.encodeToByteArray())
            }.getOrNull()
        }
        val public = PublicKifuFields(
            movesUsi = row.movesUsi,
            moveTimesSeconds = row.moveTimes.orEmpty(),
            headers = row.headers.orEmpty(),
            result = row.result,
            source = KifuSource.entries.firstOrNull { it.wireValue == row.sourcePlace } ?: KifuSource.OTHER,
        )
        val kifText = KifuReconstructor.reconstruct(public, privateFields, userSide = row.side)
        return importGame(
            ReconstructedGame(
                kifText = kifText,
                fileName = AppStrings.restoredGameFileName(public.headers["開始日時"]),
                contentHash = row.contentHash,
                userSide = row.side,
                ratingService = row.ratingService,
                ratingRaw = row.ratingRaw?.toLong(),
                ratingRule = row.ratingRule,
                sourcePlaceOverride = row.sourcePlace,
            ),
        )
    }

}
