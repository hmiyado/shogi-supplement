package dev.miyado.shogisupplement.kifu

/**
 * 先後推定の結果。
 *
 * @param side "sente" / "gote" / null（推定不能）
 * @param matchedByAccount アカウント名の一致で推定できた場合 true。
 *        false のときは last_user_side フォールバック（またはnull）であり、
 *        確認省略（skip_side_confirm）の対象にならない。
 */
data class SideSuggestion(val side: String?, val matchedByAccount: Boolean)

/**
 * KIFの対局者名と設定済みアカウント名から自分の側を推定する。
 *
 * アカウント名一致の有無を返すことで、確認省略（skip_side_confirm）の
 * 判定に使えるようにしている。
 */
object UserSideSuggester {

    /**
     * 先手・後手名をアカウント名集合と突き合わせて推定する。
     * 一致しなければ lastUserSide にフォールバック（matchedByAccount = false）。
     * 両者が同名でアカウント名に一致する場合は先手を優先する（従来挙動を踏襲）。
     */
    fun suggest(
        senteName: String?,
        goteName: String?,
        accountNames: Set<String>,
        lastUserSide: String?,
    ): SideSuggestion = when {
        senteName != null && senteName in accountNames ->
            SideSuggestion("sente", matchedByAccount = true)
        goteName != null && goteName in accountNames ->
            SideSuggestion("gote", matchedByAccount = true)
        else -> SideSuggestion(lastUserSide, matchedByAccount = false)
    }

    /**
     * 側選択ダイアログを省略してよいかどうか。
     * 省略できるのは「設定が ON」かつ「アカウント名一致で側が確定した」場合のみ。
     * フォールバック推定（last_user_side）は誤判定リスクがあるため省略しない。
     */
    fun shouldSkipConfirm(suggestion: SideSuggestion, skipEnabled: Boolean): Boolean =
        skipEnabled && suggestion.matchedByAccount && suggestion.side != null
}
