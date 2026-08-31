package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.saveRatingSettingsBundle
import dev.miyado.shogisupplement.text.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** KIFの取込元。エラー文言の出し分けと、ファイル名の決め方が分かれる。 */
enum class KifOrigin { FILE, CLIPBOARD, MANUAL }

/** 検証を通ったKIFと、そこから読み取れた対局者名。 */
data class ValidatedKif(
    val kifText: String,
    val fileName: String,
    val senteName: String?,
    val goteName: String?,
    val origin: KifOrigin,
)

/** 保存に必要な確定値。 */
data class KifImportRequest(
    val kifText: String,
    val fileName: String,
    val userSide: String?,
    val ratingService: String?,
    val ratingRaw: Long?,
    val ratingRule: String?,
)

/**
 * KIFを受け取ってから保存を依頼するまでの手順。
 * 保存と解析の起動はプラットフォームへ委ねる（[onImport]）。
 *
 * @param analysisWouldCreateAccount 未ログインのまま解析へ進むと匿名アカウントが新規に作られる状態か。
 * @param dateTimeLabel クリップボード取込のファイル名に入れる日時（"yyyy-MM-dd HH:mm"）。
 */
class KifImportController(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val analysisWouldCreateAccount: () -> Boolean = { false },
    private val dateTimeLabel: () -> String = { "" },
    private val onImport: suspend (KifImportRequest) -> Unit,
) {

    sealed interface Step {
        data object Idle : Step

        /** 解析時に匿名アカウントが作られることの事前確認。 */
        data class AccountCreationConfirm(val kif: ValidatedKif) : Step

        /** アカウント名が全サービス未設定。先に棋力設定を出す。 */
        data class RatingSetup(val kif: ValidatedKif) : Step

        data class SideConfirm(val kif: ValidatedKif, val suggestion: SideSuggestion) : Step

        /** 保存中。次の画面が確定するまで確認をもう一度受け付けない。 */
        data class Saving(val kif: ValidatedKif) : Step

        data class Failed(val message: String, val origin: KifOrigin) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Idle)
    val step: StateFlow<Step> = _step.asStateFlow()

    fun beginFromFile(fileName: String, text: String?) =
        begin(text, fileName, KifOrigin.FILE, AppStrings.KIF_FILE_EMPTY, AppStrings.KIF_FILE_INVALID)

    fun beginFromClipboard(text: String?) = begin(
        text,
        AppStrings.clipboardFileName(dateTimeLabel()),
        KifOrigin.CLIPBOARD,
        AppStrings.KIF_CLIPBOARD_EMPTY,
        AppStrings.KIF_CLIPBOARD_INVALID,
    )

    fun beginManual(kifText: String, fileName: String) =
        begin(kifText, fileName, KifOrigin.MANUAL, AppStrings.KIF_FILE_EMPTY, AppStrings.KIF_FILE_INVALID)

    private fun begin(
        text: String?,
        fileName: String,
        origin: KifOrigin,
        emptyMessage: String,
        invalidMessage: String,
    ) {
        if (text.isNullOrBlank()) {
            _step.value = Step.Failed(emptyMessage, origin)
            return
        }
        if (!ClipboardKifValidator.isValidKif(text)) {
            _step.value = Step.Failed(invalidMessage, origin)
            return
        }
        // KIFとしては読めるがパースが通らない棋譜（駒落ちなど）は、ここで止めずに保存まで進める。
        // 固有の理由は保存時にしか出せず、ここで潰すと汎用の文言に置き換わってしまう。
        val headers = runCatching { KifParser().parse(text).headers }.getOrElse { emptyMap() }
        val (senteName, goteName) = KifuDecomposer.resolvePlayerNames(text, headers)
        proceedAfterValidated(ValidatedKif(text, fileName, senteName, goteName, origin))
    }

    private fun proceedAfterValidated(kif: ValidatedKif) {
        if (analysisWouldCreateAccount() && !settingsRepository.isAccountDeclined()) {
            _step.value = Step.AccountCreationConfirm(kif)
            return
        }
        proceedAfterAccountNotice(kif)
    }

    /** [Step.AccountCreationConfirm] の「続ける」。 */
    fun confirmAccountCreation() {
        val current = _step.value as? Step.AccountCreationConfirm ?: return
        proceedAfterAccountNotice(current.kif)
    }

    /** [Step.AccountCreationConfirm] の「作らずに解析する」。以後は確認自体を出さない。 */
    fun declineAccount() {
        val current = _step.value as? Step.AccountCreationConfirm ?: return
        settingsRepository.saveAccountDeclined(true)
        proceedAfterAccountNotice(current.kif)
    }

    private fun proceedAfterAccountNotice(kif: ValidatedKif) {
        // 手入力は対局者名を書く場所が無く、アカウント名一致による推定が成り立たない。
        if (kif.origin != KifOrigin.MANUAL && !settingsRepository.hasAnyServiceAccount()) {
            _step.value = Step.RatingSetup(kif)
            return
        }
        proceedToSideConfirm(kif)
    }

    /**
     * [Step.RatingSetup] の確定。
     * Why not アカウント名の有無を再判定する: 任意入力のアカウント名を空のまま保存すると
     * 棋力設定が無限に再表示される。
     */
    fun completeRatingSetup(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccounts: Map<String, String>,
        serviceRanks: Map<String, Map<String, Int>>,
    ) {
        val current = _step.value as? Step.RatingSetup ?: return
        settingsRepository.saveRatingSettingsBundle(service, ratingRaw, ratingRule, serviceAccounts, serviceRanks)
        proceedToSideConfirm(current.kif)
    }

    private fun proceedToSideConfirm(kif: ValidatedKif) {
        val suggestion = UserSideSuggester.suggest(
            senteName = kif.senteName,
            goteName = kif.goteName,
            accountNames = settingsRepository.getAllServiceAccounts().values.toSet(),
            lastUserSide = settingsRepository.getLastUserSide(),
        )
        _step.value = Step.SideConfirm(kif, suggestion)
        val side = suggestion.side
        if (side != null && UserSideSuggester.shouldSkipConfirm(suggestion, settingsRepository.getSkipSideConfirm())) {
            confirmSide(side, skipNext = true)
        }
    }

    /** [Step.SideConfirm] の確定。[skipNext] はアカウント名一致で推定できたときだけ保存する。 */
    fun confirmSide(userSide: String?, skipNext: Boolean) {
        val current = _step.value as? Step.SideConfirm ?: return
        if (current.suggestion.matchedByAccount) settingsRepository.saveSkipSideConfirm(skipNext)
        if (userSide != null) settingsRepository.saveLastUserSide(userSide)
        val declared = settingsRepository.getRatingSettings()
        val saving = Step.Saving(current.kif)
        _step.value = saving
        val request = KifImportRequest(
            kifText = current.kif.kifText,
            fileName = current.kif.fileName,
            userSide = userSide,
            ratingService = declared.service,
            ratingRaw = declared.ratingRaw.toLong(),
            ratingRule = declared.ratingRule,
        )
        scope.launch {
            try {
                onImport(request)
            } finally {
                // 保存が失敗しても取込フローは必ず畳む。別の取込が既に始まっていれば触らない。
                if (_step.value === saving) _step.value = Step.Idle
            }
        }
    }

    fun dismiss() {
        _step.value = Step.Idle
    }
}
