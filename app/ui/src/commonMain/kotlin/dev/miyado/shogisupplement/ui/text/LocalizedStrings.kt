package dev.miyado.shogisupplement.ui.text

import org.jetbrains.compose.resources.getString
import dev.miyado.shogisupplement.ui.generated.resources.*
import dev.miyado.shogisupplement.strength.formatStrengthDecimal
import kotlin.math.roundToInt

object LocalizedStrings {
    suspend fun strengthDetail(gameCount: Int): String = getString(Res.string.message_strength_detail_1, gameCount)

    suspend fun STUDY_SAVE_FAILED(): String = getString(Res.string.study_save_failed)

    suspend fun serviceOptions(): List<Pair<String, String>> = listOf(
        "lishogi" to getString(Res.string.service_lishogi),
        "shogi_wars" to getString(Res.string.text_source_place_label),
        "kiou" to getString(Res.string.text_source_place_label_2),
        "shogi_quest" to getString(Res.string.text_source_place_label_3),
    )

    suspend fun warsRules(): List<Pair<String, String>> = listOf(
        "10min" to getString(Res.string.text_wars_rules),
        "3min" to getString(Res.string.text_wars_rules_2),
        "10sec" to getString(Res.string.text_wars_rules_3),
    )

    suspend fun kiouRules(): List<Pair<String, String>> = listOf(
        "serious" to getString(Res.string.text_kiou_rules),
        "casual" to getString(Res.string.text_kiou_rules_2),
        "fischer" to getString(Res.string.text_kiou_rules_3),
        "short" to getString(Res.string.text_kiou_rules_4),
    )

    suspend fun NOTIF_ANALYZING_TITLE(): String = getString(Res.string.notif_analyzing_title)

    suspend fun notifProgress(done: Int, total: Int, progressPct: Int): String = getString(Res.string.message_notif_progress_1, done, total, progressPct)

    suspend fun NOTIF_PREPARING(): String = getString(Res.string.notif_preparing)

    suspend fun UNKNOWN_ERROR(): String = getString(Res.string.unknown_error)

    suspend fun gameNotFound(gameId: Long): String = getString(Res.string.message_game_not_found_1, gameId)

    suspend fun AUTH_ERROR_ANON_SIGN_IN_GENERIC(): String = getString(Res.string.auth_error_anon_sign_in_generic)

    suspend fun STRENGTH_DECLARED_PREFIX(): String = getString(Res.string.strength_declared_prefix)

    suspend fun serviceShortName(serviceId: String): String = when (serviceId) {
        "shogi_wars" -> getString(Res.string.message_service_short_name_1)
        "kiou" -> getString(Res.string.text_source_place_label_2)
        "shogi_quest" -> getString(Res.string.message_service_short_name_3)
        else -> serviceId
    }

    suspend fun ruleLabel(serviceId: String, ruleId: String): String {
        val list = when (serviceId) {
            "shogi_wars" -> warsRules()
            "kiou" -> kiouRules()
            else -> emptyList()
        }
        return list.firstOrNull { it.first == ruleId }?.second ?: ruleId
    }

    suspend fun serviceLabel(serviceId: String): String =
        serviceOptions().firstOrNull { it.first == serviceId }?.second ?: serviceId

    suspend fun strengthDeclaredLine(entries: String): String = getString(Res.string.message_strength_declared_line_1, STRENGTH_DECLARED_PREFIX(), entries)

    suspend fun ANALYSIS_SERVER_NOT_CONFIGURED(): String = getString(Res.string.analysis_server_not_configured)

    suspend fun MOVE_LIST_DROPDOWN_HINT(): String = getString(Res.string.move_list_dropdown_hint)


    suspend fun TRANSFER_CODE_INPUT_ERROR_INVALID(): String = getString(Res.string.transfer_code_input_error_invalid)

    suspend fun TRANSFER_CODE_INPUT_ERROR_NOT_FOUND(): String = getString(Res.string.transfer_code_input_error_not_found)

    suspend fun TRANSFER_CODE_INPUT_ERROR_RATE_LIMITED(): String = getString(Res.string.transfer_code_input_error_rate_limited)

    suspend fun TRANSFER_CODE_INPUT_ERROR_UPGRADE_REQUIRED(): String = getString(Res.string.transfer_code_input_error_upgrade_required)

    suspend fun TRANSFER_CODE_INPUT_ERROR_GENERIC(): String = getString(Res.string.mypage_error_network)

    suspend fun blunderRateValue(pct: Int, m: Int, n: Int): String = getString(Res.string.text_blunder_rate_value, pct, m, n)

    suspend fun matchRateValue(pct: Int, l: Int, n: Int): String = getString(Res.string.text_match_rate_value, pct, l, n)

    suspend fun KENTO_PASTED_GAME_TITLE(): String = getString(Res.string.kento_pasted_game_title)

    suspend fun KENTO_ERROR_GENERIC(): String = getString(Res.string.kento_error_generic)

    suspend fun KENTO_ERROR_EMPTY_INPUT(): String = getString(Res.string.kento_error_empty_input)

    suspend fun KENTO_ERROR_KIF_PARSE(): String = getString(Res.string.kento_error_kif_parse)

    suspend fun KENTO_ERROR_NO_MOVES(): String = getString(Res.string.kento_error_no_moves)

    suspend fun GAME_RESTORE_ERROR_NOT_AUTHENTICATED(): String = getString(Res.string.game_restore_error_not_authenticated)

    suspend fun GAME_RESTORE_ERROR_NO_SECRET(): String = getString(Res.string.game_restore_error_no_secret)

    suspend fun GAME_RESTORE_ERROR_NETWORK(): String = getString(Res.string.game_restore_error_network)

    suspend fun strengthDetailBestRankValue(serviceLabel: String, rankLabel: String): String = getString(Res.string.message_strength_detail_best_rank_value_1, serviceLabel, rankLabel)

    suspend fun MYPAGE_ERROR_NO_SECRET(): String = getString(Res.string.mypage_error_no_secret)

    suspend fun MYPAGE_ERROR_NOT_FOUND(): String = getString(Res.string.mypage_error_not_found)

    suspend fun MYPAGE_ERROR_NETWORK(): String = getString(Res.string.mypage_error_network)

    suspend fun MYPAGE_ERROR_LOGOUT_FAILED(): String = getString(Res.string.mypage_error_logout_failed)
}
