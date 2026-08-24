package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.db.DrillAttemptRecord
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.coroutines.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class UploadAllResult(
    val gameSuccess: Int,
    val gameFailed: Int,
    val drillFailed: Int,
    val drillPendingRemaining: Int,
)

/**
 * 一括アップロードの結果メッセージ。送れなかった成績は、失敗した分と未送信のまま
 * 残った分を足して1つの件数で見せる（docs/wording.md）。
 */
fun UploadAllResult.resultMessage(): String =
    AppStrings.accountUploadResult(gameSuccess, gameFailed, drillPendingRemaining + drillFailed)

/** アップロードのオーケストレーター。constructor injectionでテスト可能（fakeを注入できる）。 */
class UploadOrchestrator(
    private val authRepository: AuthRepository,
    private val uploadRepository: UploadRepository,
    private val dbRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
) : DrillAttemptSync {

    override suspend fun syncPendingAttempts() = maybeAutoUploadDrillAttempts()

    /**
     * 指定ゲームをアップロードする。未ログイン/既アップロード（Duplicate扱い）で
     * 実行しなかった場合は null。
     */
    suspend fun uploadGame(gameId: Long): UploadResult? {
        val user = authRepository.currentUser.value ?: return null  // 未ログイン
        val game = dbRepository.getGameById(gameId) ?: return null
        if (game.uploadedAt != null) return UploadResult.Duplicate  // 既アップロード
        val reports = dbRepository.getReports(gameId)
        val result = uploadRepository.uploadGame(user.id, game, reports)
        if (result is UploadResult.Success || result is UploadResult.Duplicate) {
            dbRepository.updateUploadedAt(gameId, currentEpochSeconds())
            syncDrillProblemsSilently(user.id, game.contentHash, gameId)
        }
        return result
    }

    /**
     * 棋譜1局分の次の一手候補を問題として同期する。失敗しても棋譜アップロード自体の
     * 結果には影響させない（未登録の問題は手動再同期・解答送信時の1件同期で拾われる）。
     */
    private suspend fun syncDrillProblemsSilently(userId: String, contentHash: String, gameId: Long) {
        try {
            val problems = drillRepository.getDrillCandidatesByGame(gameId)
            uploadRepository.syncDrillProblems(userId, contentHash, problems)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * サーバーに保存済みの棋譜を削除する。未ログインなら false。
     */
    suspend fun deleteUploadedGame(contentHash: String): Boolean {
        val user = authRepository.currentUser.value ?: return false
        return uploadRepository.deleteGame(user.id, contentHash)
    }

    /**
     * 未送信棋譜、アップロード済み棋譜の問題、未送信の次の一手の成績を順に送信する。
     * 問題同期と解答送信の失敗は、ユーザー向けには次の一手の成績の失敗として合算する。
     */
    suspend fun uploadAll(): UploadAllResult {
        val user = authRepository.currentUser.value
            ?: return UploadAllResult(
                gameSuccess = 0,
                gameFailed = 0,
                drillFailed = 0,
                drillPendingRemaining = drillRepository.getDrillAttemptsNotUploaded(Int.MAX_VALUE).size,
            )

        var gameSuccess = 0
        var gameFailed = 0
        // uploadGame自体が成功時に問題同期を済ませるため、ここでアップロードした棋譜は
        // 下の再同期ループの対象から除く（二重の問題upsertを避ける）。
        val justUploadedGameIds = mutableSetOf<Long>()
        dbRepository.getNotUploadedGames().forEach { game ->
            val result = (try {
                uploadGame(game.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                UploadResult.Failure("棋譜のアップロードに失敗")
            }) ?: UploadResult.Failure("未ログイン")
            if (result is UploadResult.Success || result is UploadResult.Duplicate) {
                gameSuccess++
                justUploadedGameIds += game.id
            } else {
                gameFailed++
            }
        }

        var drillFailed = 0
        dbRepository.getAllGames()
            .filter { it.uploadedAt != null && it.id !in justUploadedGameIds }
            .forEach { game ->
                val result = try {
                    val problems = drillRepository.getDrillCandidatesByGame(game.id)
                    uploadRepository.syncDrillProblems(user.id, game.contentHash, problems)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    UploadResult.Failure("次の一手の問題同期に失敗")
                }
                if (result is UploadResult.Failure) drillFailed++
            }

        drillRepository.getDrillAttemptsNotUploaded(Int.MAX_VALUE).forEach { attempt ->
            val result = try {
                syncOneDrillAttempt(user.id, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                UploadResult.Failure("次の一手の成績送信に失敗")
            }
            if (result is UploadResult.Failure) drillFailed++
        }

        return UploadAllResult(
            gameSuccess = gameSuccess,
            gameFailed = gameFailed,
            drillFailed = drillFailed,
            drillPendingRemaining = drillRepository.getDrillAttemptsNotUploaded(Int.MAX_VALUE).size,
        )
    }

    /**
     * 自動アップロード設定 ON かつログイン中の場合に解析後アップロードを実行する。
     * 失敗してもアプリ動作に影響させない（例外を呑む）。
     */
    suspend fun maybeAutoUpload(gameId: Long) {
        if (!settingsRepository.getAutoUpload()) return   // 自動アップロードOFF
        if (authRepository.currentUser.value == null) return  // 未ログイン
        try {
            uploadGame(gameId)
        } catch (_: Exception) {
            // 自動アップロードの失敗はサイレント
        }
    }

    /** 自動アップロード設定 ON かつログイン中の場合に、未送信の成績を古い順で送信する。 */
    suspend fun maybeAutoUploadDrillAttempts(limit: Int = 20) {
        try {
            if (!settingsRepository.getAutoUpload()) return
            val user = authRepository.currentUser.value ?: return
            drillRepository.getDrillAttemptsNotUploaded(limit).forEach { attempt ->
                try {
                    syncOneDrillAttempt(user.id, attempt)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 1件の失敗で後続の未送信行を止めない。
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 自動アップロードの失敗はサイレントにして、未送信行を次回回収する。
        }
    }

    /** 問題と棋譜を解決して、1件の解答を冪等キー付きで送信する。 */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun syncOneDrillAttempt(userId: String, attempt: DrillAttemptRecord): UploadResult {
        val syncId = attempt.syncId ?: Uuid.random().toString().also {
            drillRepository.updateDrillAttemptSyncId(attempt.id, it)
        }
        val problem = drillRepository.getBlunderById(attempt.blunderReportId)
            ?: return UploadResult.Failure("次の一手の問題が見つからない")
        val game = dbRepository.getGameById(problem.gameId)
            ?: return UploadResult.Failure("次の一手の棋譜が見つからない")
        val result = uploadRepository.uploadDrillAttempt(
            userId = userId,
            contentHash = game.contentHash,
            problem = problem,
            attempt = DrillAttemptUpload(
                syncId = syncId,
                userMoveUsi = attempt.userMoveUsi,
                isCorrect = attempt.isCorrect,
                lossWp = attempt.lossWp,
                attemptedAt = attempt.attemptedAt,
            ),
        )
        if (result is UploadResult.Success || result is UploadResult.Duplicate) {
            drillRepository.updateDrillAttemptUploadedAt(attempt.id, currentEpochSeconds())
        }
        return result
    }
}
