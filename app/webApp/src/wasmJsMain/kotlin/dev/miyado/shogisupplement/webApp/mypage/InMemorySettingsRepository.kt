package dev.miyado.shogisupplement.webApp.mypage

import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository

/**
 * [RemoteTransferRestoreService]が要求する`SettingsRepository`の依存を満たすためだけに存在する。
 * ログイン成功時に`saveAccountDeclined`/`saveAutoUpload`/`saveConsentAcceptedAt`が呼ばれるが、
 * Web版に棋譜のアップロード・解析機能自体が無く、どこからも読み返されないため永続化しない。
 */
class InMemorySettingsRepository : SettingsRepository {
    private var rating = 1750
    private var ratingService = "lishogi"
    private var ratingRaw = 1750
    private var ratingRule: String? = null
    private var serviceAccountName: String? = null
    private var hasSavedRatingSettings = false
    private val serviceAccounts = mutableMapOf<String, String>()
    private var lastUserSide: String? = null
    private var consentAcceptedAt: Long? = null
    private var accountDeclined = false
    private var autoUpload = false
    private var themeMode = "system"
    private val serviceRanks = mutableMapOf<String, MutableMap<String, Int>>()
    private var evalDisplay = "cp"
    private var skipSideConfirm = false
    private var appPolicyCache: String? = null

    override fun saveRating(rating: Int) {
        this.rating = rating
    }

    override fun saveRatingFull(rating: Int, service: String, ratingRaw: Int) {
        this.rating = rating
        this.ratingService = service
        this.ratingRaw = ratingRaw
    }

    override fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccountName: String?,
    ) {
        hasSavedRatingSettings = true
        service?.let { ratingService = it }
        ratingRaw?.let { this.ratingRaw = it }
        this.ratingRule = ratingRule
        this.serviceAccountName = serviceAccountName
    }

    override fun getRatingSettings(): RatingSettings =
        RatingSettings(rating, ratingService, ratingRaw, ratingRule, serviceAccountName)

    override fun hasUserSavedRatingSettings(): Boolean = hasSavedRatingSettings

    override fun getRating(): Int = rating

    override fun getRatingFull(): Triple<Int, String, Int> = Triple(rating, ratingService, ratingRaw)

    override fun getServiceAccountName(): String? = serviceAccountName

    override fun upsertServiceAccount(service: String, accountName: String) {
        serviceAccounts[service] = accountName
    }

    override fun getAllServiceAccounts(): Map<String, String> = serviceAccounts.toMap()

    override fun getServiceAccountByService(service: String): String? = serviceAccounts[service]

    override fun deleteServiceAccount(service: String) {
        serviceAccounts.remove(service)
    }

    override fun hasAnyServiceAccount(): Boolean = serviceAccounts.isNotEmpty()

    override fun saveLastUserSide(userSide: String?) {
        lastUserSide = userSide
    }

    override fun getLastUserSide(): String? = lastUserSide

    override fun saveConsentAcceptedAt(epochSeconds: Long) {
        consentAcceptedAt = epochSeconds
    }

    override fun getConsentAcceptedAt(): Long? = consentAcceptedAt

    override fun saveAccountDeclined(declined: Boolean) {
        accountDeclined = declined
    }

    override fun isAccountDeclined(): Boolean = accountDeclined

    override fun saveAutoUpload(enabled: Boolean) {
        autoUpload = enabled
    }

    override fun getAutoUpload(): Boolean = autoUpload

    override fun saveThemeMode(themeMode: String) {
        this.themeMode = themeMode
    }

    override fun getThemeMode(): String = themeMode

    override fun saveServiceRank(service: String, rule: String, rankRaw: Int) {
        serviceRanks.getOrPut(service) { mutableMapOf() }[rule] = rankRaw
    }

    override fun getAllServiceRanks(): Map<String, Map<String, Int>> = serviceRanks.toMap()

    override fun deleteServiceRank(service: String, rule: String) {
        serviceRanks[service]?.remove(rule)
    }

    override fun saveEvalDisplay(mode: String) {
        evalDisplay = mode
    }

    override fun getEvalDisplay(): String = evalDisplay

    override fun saveSkipSideConfirm(skip: Boolean) {
        skipSideConfirm = skip
    }

    override fun getSkipSideConfirm(): Boolean = skipSideConfirm

    override fun saveAppPolicyCache(json: String) {
        appPolicyCache = json
    }

    override fun getAppPolicyCache(): String? = appPolicyCache
}
