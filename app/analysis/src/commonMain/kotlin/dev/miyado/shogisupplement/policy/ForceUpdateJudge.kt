package dev.miyado.shogisupplement.policy

/**
 * 強制アップデート判定の純粋関数。ネットワーク・DB・時刻等の副作用を一切持たない
 * （[ForceUpdatePolicyChecker] が取得・キャッシュ・fail-openの調停を担い、この関数を呼ぶ）。
 */
object ForceUpdateJudge {

    /**
     * @param blocked true = build < minBuild（全画面ブロック対象）
     * @param storeUrl ストアを開くボタンの遷移先。空文字/未設定なら null
     *   （呼び出し側はnullのときボタン自体を出さない）
     * @param message プラットフォーム行とcommon行のmessageを合成した表示文言。
     *   両方とも空/未設定ならnull
     */
    data class Decision(
        val blocked: Boolean,
        val storeUrl: String?,
        val message: String?,
    )

    /**
     * @param platform "android" / "ios"（"common"は対象プラットフォーム行ではなく、
     *   messageを合成する側の入力としてのみ使う）
     * @param currentBuild 自分の側のビルド番号（Android=versionCode、iOS=CFBundleVersion）
     * @param rows `app_policy` の全行（取得成功分、またはキャッシュ）
     */
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

    /**
     * プラットフォーム行のmessageを先、common行のmessageを後に改行で連結する。
     * Why not どちらかだけ採用: 管理画面はプラットフォーム個別の告知（例: 既知の不具合）と
     * 全体共通の告知（例: メンテナンス予定）を同時に運用しうるため、両方を欠落なく出す。
     */
    private fun combineMessage(platformMessage: String?, commonMessage: String?): String? {
        val parts = listOfNotNull(
            platformMessage?.trim()?.takeIf { it.isNotEmpty() },
            commonMessage?.trim()?.takeIf { it.isNotEmpty() },
        )
        return parts.joinToString("\n").ifEmpty { null }
    }
}
