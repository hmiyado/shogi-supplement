package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

/** 失敗した解答も未送信件数に含まれるため、解答送信の失敗数を重ねて足さない。 */
suspend fun UploadAllResult.resultMessage(): String {
    val succeeded = gameSuccess + studySuccess
    val failed = gameFailed + studyFailed
    val pending = drillPendingRemaining + drillProblemSyncFailed
    val base = if (failed > 0) getString(Res.string.upload_result_failed, succeeded, failed)
        else getString(Res.string.upload_result_success, succeeded)
    return if (pending > 0) getString(Res.string.upload_result_drill_pending, base, pending) else base
}
