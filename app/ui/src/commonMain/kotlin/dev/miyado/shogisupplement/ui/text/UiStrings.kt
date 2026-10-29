package dev.miyado.shogisupplement.ui.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*
import dev.miyado.shogisupplement.strength.formatStrengthDecimal
import kotlin.math.roundToInt

object UiStrings {
    @Composable
    fun homeTodaysDrillPly(ply: Long): String = stringResource(Res.string.text_home_todays_drill_ply, ply)

    @Composable
    fun drillRecordAccuracy(correct: Int, total: Int): String =
        stringResource(Res.string.text_drill_record_accuracy, (correct * 100.0 / total).roundToInt(), correct, total)

    @Composable
    fun drillRecordStreakOrdinal(ordinal: Int): String = stringResource(Res.string.text_drill_record_streak_ordinal, ordinal)

    @Composable
    fun drillRecordStreakRange(start: String, end: String): String = stringResource(Res.string.text_drill_record_streak_range, start, end)

    @Composable
    fun gameMoveCount(count: Long): String = stringResource(Res.string.text_game_move_count, count)

    @Composable
    fun playersLine(senteName: String?, goteName: String?): String =
        stringResource(Res.string.text_players_line, senteName ?: PLAYER_UNKNOWN, goteName ?: PLAYER_UNKNOWN)

    @Composable
    fun studyEvalPerspective(isGote: Boolean): String = if (isGote) stringResource(Res.string.text_study_eval_perspective) else stringResource(Res.string.text_study_eval_perspective_2)

    val serviceOptions: List<Pair<String, String>>
        @Composable get() = listOf(
        "lishogi" to stringResource(Res.string.service_lishogi),
        "shogi_wars" to stringResource(Res.string.text_service_options),
        "kiou" to stringResource(Res.string.text_service_options_2),
        "shogi_quest" to stringResource(Res.string.text_service_options_3),
    )

    val warsRules: List<Pair<String, String>>
        @Composable get() = listOf(
        "10min" to stringResource(Res.string.text_wars_rules),
        "3min" to stringResource(Res.string.text_wars_rules_2),
        "10sec" to stringResource(Res.string.text_wars_rules_3),
    )

    val kiouRules: List<Pair<String, String>>
        @Composable get() = listOf(
        "serious" to stringResource(Res.string.text_kiou_rules),
        "casual" to stringResource(Res.string.text_kiou_rules_2),
        "fischer" to stringResource(Res.string.text_kiou_rules_3),
        "short" to stringResource(Res.string.text_kiou_rules_4),
    )

    val IMPORT_ACCOUNT_NOTICE_BODY: String
        @Composable get() =
        stringResource(Res.string.text_import_account_notice_body)

    @Composable
    fun sideSente(senteName: String?): String = if (senteName != null) stringResource(Res.string.text_side_sente, senteName) else stringResource(Res.string.text_side_sente_2)

    @Composable
    fun sideGote(goteName: String?): String = if (goteName != null) stringResource(Res.string.text_side_gote, goteName) else stringResource(Res.string.text_side_gote_2)

    @Composable
    fun analyzingProgress(currentMove: Int, totalMoves: Int): String = stringResource(Res.string.text_analyzing_progress, currentMove, totalMoves)

    @Composable
    fun viewerPlyLabel(ply: Int): String = stringResource(Res.string.text_viewer_ply_label, ply)

    @Composable
    fun noBlundersLoss(endReasonLabel: String): String =
        stringResource(Res.string.text_no_blunders_loss, endReasonLabel) +
        stringResource(Res.string.text_no_blunders_loss_2)

    @Composable
    fun blunderCardPly(ply: Long): String = stringResource(Res.string.text_blunder_card_ply, ply)

    @Composable
    fun drillAttemptCount(count: Int): String = stringResource(Res.string.text_drill_attempt_count, count)

    @Composable
    fun drillTotalCount(count: Int): String = stringResource(Res.string.text_drill_total_count, count)

    @Composable
    fun drillLossPct(pct: String): String = stringResource(Res.string.text_drill_loss_pct, pct)

    @Composable
    fun drillActualMove(moveDisplay: String): String = stringResource(Res.string.text_drill_actual_move, moveDisplay)

    val GAME_LIST_FILTER_OTHER: String
        @Composable get() = stringResource(Res.string.text_game_list_filter_other)

    val GAME_LIST_FILTER_PERIOD_7D: String
        @Composable get() = stringResource(Res.string.text_game_list_filter_period_7d)

    val GAME_LIST_FILTER_PERIOD_30D: String
        @Composable get() = stringResource(Res.string.text_game_list_filter_period_30d)

    val GAME_LIST_FILTER_PERIOD_ALL: String
        @Composable get() = stringResource(Res.string.text_game_list_filter_period_all)

    @Composable
    fun gameListFilteredCount(shown: Int, total: Int): String = stringResource(Res.string.text_game_list_filtered_count, shown, total)

    @Composable
    fun gameListTotalCount(total: Int): String = stringResource(Res.string.text_game_list_total_count, total)

    @Composable
    fun gameListSavedFilterPeriod(days: Int?): String = when (days) {
        null -> GAME_LIST_FILTER_PERIOD_ALL
        7 -> GAME_LIST_FILTER_PERIOD_7D
        else -> GAME_LIST_FILTER_PERIOD_30D
    }

    @Composable
    fun gameListSummaryWinRate(pct: Int, wins: Int, decided: Int): String =
        stringResource(Res.string.text_game_list_summary_win_rate, pct, wins, decided)

    @Composable
    fun gameListSummaryBlunderRate(pct: Int, blunders: Int, moves: Int): String =
        stringResource(Res.string.text_game_list_summary_blunder_rate, pct, blunders, moves)

    @Composable
    fun gameListSummaryStrength(display: String): String = stringResource(Res.string.text_game_list_summary_strength, display)

    @Composable
    fun gameListSelectedCount(count: Int): String = stringResource(Res.string.text_game_list_selected_count, count)

    @Composable
    fun evalSuffix(label: String): String = stringResource(Res.string.text_eval_suffix, label)

    @Composable
    fun errorMessage(message: String): String = stringResource(Res.string.text_error_message, message)

    val PLAYER_UNKNOWN: String
        @Composable get() = stringResource(Res.string.text_player_unknown)

    val ACCOUNT_NOT_PROVIDING_DESCRIPTION: String
        @Composable get() =
        stringResource(Res.string.text_account_not_providing_description)

    val ACCOUNT_DEVICE_TRANSFER_NOTE: String
        @Composable get() =
        stringResource(Res.string.text_account_device_transfer_note)

    @Composable
    fun accountUploadedCount(count: Int): String = stringResource(Res.string.text_account_uploaded_count, count)

    val ACCOUNT_DELETE_DIALOG_TEXT: String
        @Composable get() =
        stringResource(Res.string.text_account_delete_dialog_text)

    val GAME_DELETE_DIALOG_TITLE: String
        @Composable get() = stringResource(Res.string.text_game_delete_dialog_title)

    @Composable
    fun gameDeleteDialogTitle(count: Int): String =
        if (count <= 1) GAME_DELETE_DIALOG_TITLE else stringResource(Res.string.text_game_delete_dialog_title_count, count)

    val GAME_DELETE_DIALOG_TEXT_DEVICE_ONLY: String
        @Composable get() =
        stringResource(Res.string.text_game_delete_dialog_text_device_only)

    val GAME_DELETE_DIALOG_TEXT_WITH_SERVER: String
        @Composable get() =
        stringResource(Res.string.text_game_delete_dialog_text_with_server)

    val GAME_DELETE_SERVER_ERROR: String
        @Composable get() =
        stringResource(Res.string.text_game_delete_server_error)

    val GAME_DELETE_LOCAL_ERROR: String
        @Composable get() =
        stringResource(Res.string.text_game_delete_local_error)

    val ACCOUNT_AUTO_UPLOAD_DESC: String
        @Composable get() =
        stringResource(Res.string.text_account_auto_upload_desc)

    @Composable
    fun accountManualUploadButton(count: Int): String = stringResource(Res.string.text_account_manual_upload_button, count)

    @Composable
    fun sourcePlaceLabel(sourcePlace: String?): String? = when (sourcePlace) {
        "wars" -> stringResource(Res.string.text_source_place_label)
        "lishogi" -> stringResource(Res.string.service_lishogi)
        "kiou" -> stringResource(Res.string.text_source_place_label_2)
        "shogi_quest" -> stringResource(Res.string.text_source_place_label_3)
        else -> null
    }

    @Composable
    fun sourceFilterLabel(sourcePlace: String): String =
        sourcePlaceLabel(sourcePlace) ?: GAME_LIST_FILTER_OTHER

    val THEME_SYSTEM: String
        @Composable get() = stringResource(Res.string.text_theme_system)

    val THEME_LIGHT: String
        @Composable get() = stringResource(Res.string.text_theme_light)

    val THEME_DARK: String
        @Composable get() = stringResource(Res.string.text_theme_dark)

    @Composable
    fun themeLabel(themeMode: String): String = when (themeMode) {
        "light" -> THEME_LIGHT
        "dark" -> THEME_DARK
        else -> THEME_SYSTEM
    }

    val EVAL_DISPLAY_CP: String
        @Composable get() = stringResource(Res.string.text_eval_display_cp)

    val EVAL_DISPLAY_WP: String
        @Composable get() = stringResource(Res.string.text_eval_display_wp)

    @Composable
    fun evalDisplayLabel(mode: String): String = if (mode == "cp") EVAL_DISPLAY_CP else EVAL_DISPLAY_WP

    @Composable
    fun blunderLossCp(cpLoss: Int): String = stringResource(Res.string.text_blunder_loss_cp, cpLoss)

    @Composable
    fun cpSignedLabel(cp: Int): String = when {
        cp > 0 -> stringResource(Res.string.text_cp_signed_label, cp)
        cp < 0 -> stringResource(Res.string.text_cp_signed_label_2, -cp)
        else -> stringResource(Res.string.text_cp_signed_label_3)
    }

    val DEBUG_WIPE_DIALOG_TEXT: String
        @Composable get() =
        stringResource(Res.string.text_debug_wipe_dialog_text) +
        stringResource(Res.string.text_debug_wipe_dialog_text_2)

    @Composable
    fun debugWasmSiteEffective(url: String, source: String): String = stringResource(Res.string.text_debug_wasm_site_effective, url, source)

    @Composable
    fun studyTurnHint(senteToMove: Boolean): String =
        if (senteToMove) stringResource(Res.string.text_study_turn_hint) else stringResource(Res.string.text_study_turn_hint_2)

    @Composable
    fun studyPlyLabel(ply: Int): String = stringResource(Res.string.text_study_ply_label, ply)

    @Composable
    fun studyOriginLine(label: String): String = stringResource(Res.string.text_study_origin_line, label)

    @Composable
    fun studyChipEvalSuffix(text: String): String = stringResource(Res.string.text_study_chip_eval_suffix, text)

    val LICENSE_APP_BODY: String
        @Composable get() =
        stringResource(Res.string.text_license_app_body) +
        stringResource(Res.string.text_license_app_body_2)

    val LICENSE_ENGINE_BODY: String
        @Composable get() =
        stringResource(Res.string.text_license_engine_body) +
        stringResource(Res.string.text_license_engine_body_2)

    val LICENSE_FONT_INTRO: String
        @Composable get() =
        stringResource(Res.string.text_license_font_intro) +
        stringResource(Res.string.text_license_font_intro_2)

    val LICENSE_FONT_BODY: String
        @Composable get() =
        stringResource(Res.string.text_license_font_body) +
        stringResource(Res.string.text_license_font_body_2) +
        stringResource(Res.string.text_license_font_body_3)

    val CONSENT_INTRO: String
        @Composable get() =
        stringResource(Res.string.text_consent_intro)

    val CONSENT_WITH_ACCOUNT_POINTS: List<String>
        @Composable get() = org.jetbrains.compose.resources.stringArrayResource(Res.array.consent_with_account_points)

    val CONSENT_WITHOUT_ACCOUNT_POINTS: List<String>
        @Composable get() = org.jetbrains.compose.resources.stringArrayResource(Res.array.consent_without_account_points)

    val TRANSFER_CODE_DESCRIPTION: String
        @Composable get() =
        stringResource(Res.string.text_transfer_code_description) +
        stringResource(Res.string.text_transfer_code_description_2)

    val TRANSFER_CODE_REGENERATE_DIALOG_TEXT: String
        @Composable get() =
        stringResource(Res.string.text_transfer_code_regenerate_dialog_text) +
        stringResource(Res.string.text_transfer_code_regenerate_dialog_text_2)

    val TRANSFER_CODE_INPUT_CONFIRM_TEXT: String
        @Composable get() =
        stringResource(Res.string.text_transfer_code_input_confirm_text)

    @Composable
    fun blunderRateValue(pct: Int, m: Int, n: Int): String = stringResource(Res.string.text_blunder_rate_value, pct, m, n)

    @Composable
    fun matchRateValue(pct: Int, l: Int, n: Int): String = stringResource(Res.string.text_match_rate_value, pct, l, n)

    @Composable
    fun kentoAnalyzing(done: Int, total: Int): String = stringResource(Res.string.text_kento_analyzing, done, total)

    val FORCE_UPDATE_NOTE: String
        @Composable get() =
        stringResource(Res.string.text_force_update_note)

    @Composable
    fun forceUpdateVersionValue(versionName: String, buildNumber: Int): String = stringResource(Res.string.text_force_update_version_value, versionName, buildNumber)

    @Composable
    fun gameRestoreCount(count: Int): String = stringResource(Res.string.text_game_restore_count, count)

    @Composable
    fun gameRestoreProgress(done: Int, total: Int): String = stringResource(Res.string.text_game_restore_progress, done, total)

    @Composable
    fun gameRestoreCompletedAll(succeeded: Int): String = stringResource(Res.string.text_game_restore_completed_all, succeeded)

    @Composable
    fun gameRestoreCompletedPartial(succeeded: Int, failed: Int): String =
        stringResource(Res.string.text_game_restore_completed_partial, succeeded, failed)

    @Composable
    fun strengthDetailRange(low: Double, high: Double): String =
        stringResource(Res.string.text_strength_detail_range, formatStrengthDecimal(low), formatStrengthDecimal(high))

    @Composable
    fun strengthDetailTrendLabel(count: Int): String = stringResource(Res.string.text_strength_detail_trend_label, count)

    @Composable
    fun strengthDetailSelectedMeta(blunderRateValue: String, matchRateValue: String): String =
        stringResource(Res.string.text_strength_detail_selected_meta, blunderRateValue, matchRateValue)
}
