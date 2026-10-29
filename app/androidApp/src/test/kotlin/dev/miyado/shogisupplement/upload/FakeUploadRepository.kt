package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord

/**
 * テスト用 UploadRepository 実装。
 * uploadGame の返却値をあらかじめ指定できる。
 */
class FakeUploadRepository(
    private var result: UploadResult = UploadResult.Success,
    private var deleteResult: Boolean = true,
    private var drillProblemsResult: UploadResult = UploadResult.Success,
    private var drillAttemptResult: UploadResult = UploadResult.Success,
) : UploadRepository {
    var remoteState: UploadRepository.AnalysisRemoteState? = UploadRepository.AnalysisRemoteState(null, false)
    var analysisOutcome: UploadRepository.AnalysisUploadOutcome? = null
    var remoteStateCalls = 0
    var onRemoteState: (suspend () -> Unit)? = null
    var onAnalysisResponse: (suspend () -> Unit)? = null
    val analysisTargets = mutableListOf<dev.miyado.shogisupplement.db.GameRepository.AnalysisSyncTarget>()
    val analysisSnapshots = mutableListOf<dev.miyado.shogisupplement.db.GameRepository.AnalysisUploadSnapshot>()
    val attemptGenerations = mutableListOf<String>()
    val deletionTargets = mutableListOf<dev.miyado.shogisupplement.db.GameRepository.AnalysisDeleteTarget>()
    override suspend fun deleteAnalysisGeneration(
        userId: String, contentHash: String, target: dev.miyado.shogisupplement.db.GameRepository.AnalysisDeleteTarget,
    ): Boolean {
        deletionTargets += target
        deleteCalls += userId to contentHash
        return deleteResult
    }

    override suspend fun uploadGenerationAttempt(
        userId: String, contentHash: String, generation: String, problem: BlunderRecord, attempt: DrillAttemptUpload,
    ): UploadResult {
        attemptGenerations += generation
        return uploadDrillAttempt(userId, contentHash, problem, attempt)
    }

    override suspend fun getAnalysisRemoteState(contentHash: String): UploadRepository.AnalysisRemoteState? {
        remoteStateCalls++
        onRemoteState?.invoke()
        return remoteState
    }

    override suspend fun uploadAnalysis(
        userId: String,
        snapshot: dev.miyado.shogisupplement.db.GameRepository.AnalysisUploadSnapshot,
        target: dev.miyado.shogisupplement.db.GameRepository.AnalysisSyncTarget,
    ): UploadRepository.AnalysisUploadOutcome {
        calls += Triple(userId, snapshot.game, snapshot.reports)
        analysisTargets += target
        analysisSnapshots += snapshot
        events += "analysis:${snapshot.game.contentHash}"
        onGameUpload?.invoke()
        onAnalysisResponse?.invoke()
        return analysisOutcome ?: when (val configured = result) {
            is UploadResult.Failure -> UploadRepository.AnalysisUploadOutcome.Failure(configured.message)
            else -> if (drillProblemsResult is UploadResult.Failure) {
                UploadRepository.AnalysisUploadOutcome.Failure("snapshot rejected")
            } else UploadRepository.AnalysisUploadOutcome.Applied(configured is UploadResult.Success)
        }
    }

    /** 呼び出し履歴（テスト検証用）。 */
    val calls = mutableListOf<Triple<String, GameRecord, List<BlunderRecord>>>()
    val deleteCalls = mutableListOf<Pair<String, String>>()
    val drillProblemCalls = mutableListOf<Triple<String, String, List<BlunderRecord>>>()
    val drillAttemptCalls = mutableListOf<DrillAttemptCall>()
    val events = mutableListOf<String>()
    var onGameUpload: (() -> Unit)? = null
    var onStudyUpload: (() -> Unit)? = null
    var studyResult: UploadResult = UploadResult.Success
    val studyCalls = mutableListOf<dev.miyado.shogisupplement.db.GameRepository.StudyUploadSnapshot>()

    override suspend fun uploadStudy(userId: String, snapshot: dev.miyado.shogisupplement.db.GameRepository.StudyUploadSnapshot): UploadResult {
        studyCalls += snapshot
        onStudyUpload?.invoke()
        return studyResult
    }

    override suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult {
        calls.add(Triple(userId, game, reports))
        events += "game:${game.contentHash}"
        onGameUpload?.invoke()
        return result
    }

    override suspend fun deleteGame(userId: String, contentHash: String): Boolean {
        deleteCalls.add(userId to contentHash)
        return deleteResult
    }

    override suspend fun syncDrillProblems(
        userId: String,
        contentHash: String,
        problems: List<BlunderRecord>,
        replaceExisting: Boolean,
    ): UploadResult {
        drillProblemCalls += Triple(userId, contentHash, problems)
        events += "problem:$contentHash"
        return drillProblemsResult
    }

    override suspend fun uploadDrillAttempt(
        userId: String,
        contentHash: String,
        problem: BlunderRecord,
        attempt: DrillAttemptUpload,
    ): UploadResult {
        drillAttemptCalls += DrillAttemptCall(userId, contentHash, problem, attempt)
        events += "attempt:${attempt.syncId}"
        return drillAttemptResult
    }

    /** 次の呼び出しに返す結果を変更する。 */
    fun setResult(r: UploadResult) {
        result = r
    }

    /** 次の削除呼び出しに返す結果を変更する。 */
    fun setDeleteResult(value: Boolean) {
        deleteResult = value
    }

    fun setDrillProblemsResult(r: UploadResult) {
        drillProblemsResult = r
    }

    fun setDrillAttemptResult(r: UploadResult) {
        drillAttemptResult = r
    }
}

data class DrillAttemptCall(
    val userId: String,
    val contentHash: String,
    val problem: BlunderRecord,
    val attempt: DrillAttemptUpload,
)
