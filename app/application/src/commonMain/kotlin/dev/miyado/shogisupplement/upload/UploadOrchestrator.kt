package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.db.DrillAttemptRecord
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class UploadAllResult(
    val gameSuccess: Int,
    val gameFailed: Int,
    /** 問題の同期に失敗した棋譜の数。解答とは別の軸で、未送信解答には現れない。 */
    val drillProblemSyncFailed: Int,
    /** 送信できずに残っている解答の数。送信に失敗した解答もここに含まれる。 */
    val drillPendingRemaining: Int,
    val studySuccess: Int = 0,
    val studyFailed: Int = 0,
)

/**
 * 問題登録と解答送信の別はユーザーへ出さず、1つの件数に合算する（docs/wording.md）。
 * Why not 解答送信の失敗数も足す: 失敗した解答は未送信のまま残り
 * [drillPendingRemaining] に既に入っているため、同じ1件を2件として見せてしまう。
 */
fun UploadAllResult.resultMessage(): String =
    AppStrings.accountUploadResult(gameSuccess + studySuccess, gameFailed + studyFailed, drillPendingRemaining + drillProblemSyncFailed)

/** アップロードのオーケストレーター。constructor injectionでテスト可能（fakeを注入できる）。 */
class UploadOrchestrator(
    private val authRepository: AuthRepository,
    private val uploadRepository: UploadRepository,
    private val dbRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
) : DrillAttemptSync {

    /** 自動送信と手動送信が同時に同じ棋譜を置換し、解答履歴をcascade削除する競合を防ぐ。 */
    private val uploadMutex = Mutex()

    override suspend fun syncPendingAttempts() = maybeAutoUploadDrillAttempts()

    /**
     * 指定ゲームをアップロードする。未ログイン/既アップロード（Duplicate扱い）で
     * 実行しなかった場合は null。
     */
    suspend fun uploadGame(gameId: Long): UploadResult? {
        return uploadMutex.withLock {
            val user = authRepository.currentUser.value ?: return@withLock null  // 未ログイン
            val snapshot = dbRepository.getAnalysisUploadSnapshot(gameId) ?: return@withLock null
            val revision = snapshot.revision
            val game = snapshot.game
            if (game.uploadedAt != null) return@withLock UploadResult.Duplicate  // 既アップロード
            val generation = snapshot.generation ?: return@withLock UploadResult.Failure("解析世代がありません")
            val target = dbRepository.getAnalysisSyncTarget(gameId, user.id, generation) ?: run {
                val remote = dbRepository.getAnalysisRemoteBase(gameId, user.id)?.let {
                    UploadRepository.AnalysisRemoteState(it.generation, false)
                } ?: uploadRepository.getAnalysisRemoteState(game.contentHash)
                    ?: return@withLock UploadResult.Failure("送信先の解析世代を確認できませんでした")
                if (authRepository.currentUser.value?.id != user.id) return@withLock UploadResult.Failure("アカウントが変更されました")
                // 削除済みの棋譜の再作成は、通常の自動再送からは許可しない。
                if (remote.deleted) return@withLock UploadResult.Failure("送信先の棋譜は削除されています")
                dbRepository.freezeAnalysisSyncTarget(gameId, user.id, generation, remote.generation,
                    (game.studyKif ?: game.kifText)?.let { GameRepository.FrozenStudy(it, snapshot.studyRevision) })
                    ?: return@withLock UploadResult.Failure("送信前に解析が更新されました")
            }
            if (authRepository.currentUser.value?.id != user.id) return@withLock UploadResult.Failure("アカウントが変更されました")
            val sending = target.study?.let {
                snapshot.copy(game = game.copy(studyKif = it.kif), studyRevision = it.revision)
            } ?: snapshot
            val outcome = uploadRepository.uploadAnalysis(user.id, sending, target)
            if (authRepository.currentUser.value?.id != user.id) return@withLock UploadResult.Failure("アカウントが変更されました")
            when (val result = outcome) {
                is UploadRepository.AnalysisUploadOutcome.Applied -> {
                    if (result.privateWritten) {
                        target.study?.let {
                            dbRepository.acknowledgeStudyUpload(gameId, it.revision, it.kif)
                        }
                    }
                    dbRepository.acknowledgeAnalysisGeneration(gameId, user.id, generation, revision, currentEpochSeconds())
                    UploadResult.Success
                }
                is UploadRepository.AnalysisUploadOutcome.Conflict -> UploadResult.Failure("別の端末で解析が更新されています")
                is UploadRepository.AnalysisUploadOutcome.Superseded -> UploadResult.Failure("送信した解析は既に置き換えられています")
                is UploadRepository.AnalysisUploadOutcome.Failure -> UploadResult.Failure(result.message)
            }
        }
    }

    /** 検討だけの送信は問題の置換や回答の送信を呼ばない。 */
    suspend fun uploadPendingStudies(): Pair<Int, Int> = uploadMutex.withLock {
        val pending = dbRepository.getPendingStudyUploads()
        val user = authRepository.currentUser.value ?: return@withLock 0 to pending.size
        var succeeded = 0
        var failed = 0
        for (snapshot in pending) {
            val result = try {
                uploadRepository.uploadStudy(user.id, snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                UploadResult.Failure("検討文書を送信できませんでした")
            }
            if (result is UploadResult.Success) {
                dbRepository.acknowledgeStudyUpload(snapshot.gameId, snapshot.revision, snapshot.studyKif)
                succeeded++
            } else {
                failed++
            }
        }
        succeeded to failed
    }

    /**
     * サーバーに保存済みの棋譜を削除する。未ログインなら false。
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun deleteUploadedGame(contentHash: String): Boolean = uploadMutex.withLock {
        val user = authRepository.currentUser.value ?: return@withLock false
        val gameId = dbRepository.getByHash(contentHash) ?: return@withLock false
        val snapshot = dbRepository.getAnalysisUploadSnapshot(gameId) ?: return@withLock false
        val generation = snapshot.generation ?: return@withLock false
        val target = dbRepository.getAnalysisDeleteTarget(gameId, user.id, generation) ?: run {
            val remote = uploadRepository.getAnalysisRemoteState(contentHash) ?: return@withLock false
            if (authRepository.currentUser.value?.id != user.id) return@withLock false
            // 他の要求で既に削除済みなら、サーバー側を再変更する必要はない。
            if (remote.deleted) return@withLock true
            dbRepository.freezeAnalysisDeleteTarget(gameId, user.id, generation,
                GameRepository.AnalysisDeleteTarget(remote.generation, Uuid.random().toString())) ?: return@withLock false
        }
        if (authRepository.currentUser.value?.id != user.id) return@withLock false
        val deleted = uploadRepository.deleteAnalysisGeneration(user.id, contentHash, target)
        deleted && authRepository.currentUser.value?.id == user.id
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
                drillProblemSyncFailed = 0,
                drillPendingRemaining = drillRepository.getDrillAttemptsNotUploaded(Int.MAX_VALUE).size,
            )

        var gameSuccess = 0
        var gameFailed = 0
        var drillProblemSyncFailed = 0
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
                if (dbRepository.getGameById(game.id)?.uploadedAt == null) {
                    drillProblemSyncFailed++
                }
            } else {
                gameFailed++
            }
        }

        drillRepository.getDrillAttemptsNotUploaded(Int.MAX_VALUE).forEach { attempt ->
            try {
                syncOneDrillAttempt(user.id, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 1件の失敗で残りを止めない。送れなかった解答は未送信のまま残り、
                // 下のdrillPendingRemainingで数える。
            }
        }

        val (studySuccess, studyFailed) = uploadPendingStudies()
        return UploadAllResult(
            gameSuccess = gameSuccess,
            gameFailed = gameFailed,
            drillProblemSyncFailed = drillProblemSyncFailed,
            drillPendingRemaining = drillRepository.getDrillAttemptsNotUploaded(Int.MAX_VALUE).size,
            studySuccess = studySuccess,
            studyFailed = studyFailed,
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
        val snapshot = dbRepository.getAnalysisUploadSnapshot(problem.gameId)
            ?: return UploadResult.Failure("次の一手の棋譜が見つからない")
        val game = snapshot.game
        val generation = snapshot.generation ?: return UploadResult.Failure("解析世代がありません")
        if (snapshot.problems.none { it.id == problem.id }) {
            return UploadResult.Failure("回答元の解析は更新されています")
        }
        val result = uploadRepository.uploadGenerationAttempt(
            userId = userId,
            contentHash = game.contentHash,
            generation = generation,
            problem = problem,
            attempt = DrillAttemptUpload(
                syncId = syncId,
                userMoveUsi = attempt.userMoveUsi,
                isCorrect = attempt.isCorrect,
                lossWp = attempt.lossWp,
                attemptedAt = attempt.attemptedAt,
            ),
        )
        if (authRepository.currentUser.value?.id != userId) return UploadResult.Failure("アカウントが変更されました")
        if (result is UploadResult.Success || result is UploadResult.Duplicate) {
            drillRepository.updateDrillAttemptUploadedAt(attempt.id, currentEpochSeconds())
        }
        return result
    }
}
