package dev.miyado.shogisupplement.policy

/**
 * 強制アップデート判定の純粋関数。ネットワーク・DB・時刻等の副作用を一切持たない
 * （[ForceUpdatePolicyChecker] が取得・キャッシュ・fail-openの調停を担い、この関数を呼ぶ）。
 */
object ForceUpdateJudge {

    /** 判定結果。 @param blocked buildが最小値未満か。 @param storeUrl ストアURL。 @param message 合成済み告知文。 */
    data class Decision(
        val blocked: Boolean,
        val storeUrl: String?,
        val message: String?,
    )

    /** 強制更新を判定する。 @param platform androidまたはios。commonは共通告知の入力として扱う。 @param currentBuild 現在のビルド番号。 @param rows app_policyの行。 */
    fun evaluate(platform: String, currentBuild: Int, rows: List<AppPolicyRow>): Decision {
        val platformRow = rows.firstOrNull { it.platform == platform }
        val commonRow = rows.firstOrNull { it.platform == "common" }

        // platform行が無い、またはmin_buildが未設定（初期値=誰もブロックしない）なら常に非ブロック。
        val minBuild = platformRow?.minBuild
        val blocked = minBuild != null && currentBuild < minBuild

        return Decision(
            blocked = blocked,
            storeUrl = platformRow?.storeUrl?.takeIf { it.isNotBlank() },
            message = combineMessage(platformRow?.message, commonRow?.message),
        )
    }

    /** プラットフォーム行とcommon行のmessageを改行で連結する。Why not 一方だけにしない: 個別告知と共通告知は同時に成立するため。 */
    private fun combineMessage(platformMessage: String?, commonMessage: String?): String? {
        val parts = listOfNotNull(
            platformMessage?.trim()?.takeIf { it.isNotEmpty() },
            commonMessage?.trim()?.takeIf { it.isNotEmpty() },
        )
        return parts.joinToString("\n").ifEmpty { null }
    }
}
