package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.db.RatingDeclaration
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.saveRatingSettingsBundle
import dev.miyado.shogisupplement.rating.declaredRankForGame
import dev.miyado.shogisupplement.text.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** KIFの取込元。エラー文言の出し分けと、ファイル名の決め方が分かれる。 */
enum class KifOrigin { FILE, CLIPBOARD, MANUAL }

/**
 * 検証を通ったKIFと、そこから読み取れた値。
 *
 * @property sourcePlace 出典サービス（[KifuSource.wireValue]）。
 * @property timeControlRaw 「持ち時間」ヘッダの原文。
 * @property byoyomiRaw 「秒読み」ヘッダの原文。
 */
data class ValidatedKif(
    val kifText: String,
    val fileName: String,
    val senteName: String?,
    val goteName: String?,
    val origin: KifOrigin,
    val sourcePlace: String?,
    val timeControlRaw: String?,
    val byoyomiRaw: String?,
)

/** 保存に必要な確定値。 */
data class KifImportRequest(
    val kifText: String,
    val fileName: String,
    val userSide: String?,
    val ratingService: String?,
    val ratingRaw: Long?,
    val ratingRule: String?,
    val ratingDeclaredAt: Long?,
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
        proceedAfterValidated(
            ValidatedKif(
                kifText = text,
                fileName = fileName,
                senteName = senteName,
                goteName = goteName,
                origin = origin,
                sourcePlace = KifuDecomposer.classifySource(text, headers["場所"], headers["棋戦"]).wireValue,
                timeControlRaw = headers["持ち時間"],
                byoyomiRaw = headers["秒読み"],
            ),
        )
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
        val currentStep = _step.value as? Step.SideConfirm ?: return
        if (currentStep.suggestion.matchedByAccount) settingsRepository.saveSkipSideConfirm(skipNext)
        if (userSide != null) settingsRepository.saveLastUserSide(userSide)
        // 未申告なら記録しない。行の既定値（lishogi 1750）を申告値として棋譜に焼き付けないため。
        val currentSettings = settingsRepository.getRatingSettings()
            .takeIf { settingsRepository.hasUserSavedRatingSettings() }
        val gameStartedAt = runCatching { KifParser().parse(currentStep.kif.kifText).headers["開始日時"] }
            .getOrNull()
            ?.let(::parseKifStartAtJst)
        val history = gameStartedAt
            ?.let { settingsRepository.getRatingDeclarationsAtOrBefore(it) }
            ?.firstOrNull { declaration -> declarationMatchesGame(declaration, currentStep.kif) }
        // 対局日時がある棋譜では、履歴に無い現在の申告を過去へ遡って付与しない。
        val declared = when {
            history != null -> history
            gameStartedAt != null -> null
            else -> currentSettings?.let {
                RatingDeclaration(
                    service = it.service,
                    ratingRaw = it.ratingRaw,
                    ratingRule = it.ratingRule,
                    declaredAt = settingsRepository.getRatingDeclaredAt() ?: 0L,
                )
            }
        }
        // 日時のない棋譜では、段級位制のサービスだけ現在のルールから値を引き直す。
        val rank = declaredRankForGame(
            service = currentSettings?.service,
            serviceRanks = settingsRepository.getAllServiceRanks(),
            sourcePlace = currentStep.kif.sourcePlace,
            timeControlRaw = currentStep.kif.timeControlRaw,
            byoyomiRaw = currentStep.kif.byoyomiRaw,
        ).takeIf { gameStartedAt == null }
        val saving = Step.Saving(currentStep.kif)
        _step.value = saving
        val request = KifImportRequest(
            kifText = currentStep.kif.kifText,
            fileName = currentStep.kif.fileName,
            userSide = userSide,
            ratingService = declared?.service,
            // 段級位制のサービスでは単一値の申告が無く0が入っているため、値として送らない。
            ratingRaw = rank?.rankRaw?.toLong() ?: declared?.ratingRaw?.takeIf { it > 0 }?.toLong(),
            ratingRule = rank?.ruleId ?: declared?.ratingRule,
            ratingDeclaredAt = declared?.declaredAt?.takeIf { it > 0 },
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

    private fun declarationMatchesGame(declaration: RatingDeclaration, kif: ValidatedKif): Boolean {
        val service = declaration.service ?: return false
        val ratingRaw = declaration.ratingRaw ?: return false
        val expectedSource = when (service) {
            "shogi_wars" -> "wars"
            "lishogi" -> "lishogi"
            "shogi_quest" -> "shogi_quest"
            "kiou" -> "kiou"
            else -> null
        }
        if (expectedSource != null && kif.sourcePlace != expectedSource) return false
        val ratingRule = declaration.ratingRule ?: return true
        val rank = declaredRankForGame(
            service = service,
            serviceRanks = mapOf(service to mapOf(ratingRule to ratingRaw)),
            sourcePlace = kif.sourcePlace,
            timeControlRaw = kif.timeControlRaw,
            byoyomiRaw = kif.byoyomiRaw,
        )
        return rank?.rankRaw == ratingRaw
    }
}
