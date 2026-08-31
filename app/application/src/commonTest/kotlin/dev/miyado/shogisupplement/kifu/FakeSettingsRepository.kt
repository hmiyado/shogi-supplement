package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository

/** 取込フローが読み書きする設定だけを覚えるフェイク。他の設定は素通しする。 */
class FakeSettingsRepository(
    private var rating: Int = 1750,
    private var service: String = "lishogi",
    private var ratingRaw: Int = 1750,
    private var ratingRule: String? = null,
    var hasSavedRatingSettings: Boolean = false,
    val serviceAccounts: MutableMap<String, String> = mutableMapOf(),
    var savedLastUserSide: String? = null,
    var savedSkipSideConfirm: Boolean = false,
    var accountDeclined: Boolean = false,
) : SettingsRepository {
    val serviceRanks: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    var savedServiceAccountName: String? = null
        private set

    override fun saveRating(rating: Int) { this.rating = rating }

    override fun saveRatingFull(rating: Int, service: String, ratingRaw: Int) {
        this.rating = rating
        this.service = service
        this.ratingRaw = ratingRaw
    }

    override fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccountName: String?,
    ) {
        service?.let { this.service = it }
        ratingRaw?.let { this.ratingRaw = it }
        this.ratingRule = ratingRule
        this.savedServiceAccountName = serviceAccountName
        hasSavedRatingSettings = true
    }

    override fun getRatingSettings(): RatingSettings =
        RatingSettings(rating, service, ratingRaw, ratingRule, savedServiceAccountName)

    override fun hasUserSavedRatingSettings(): Boolean = hasSavedRatingSettings

    override fun getRating(): Int = rating

    override fun getRatingFull(): Triple<Int, String, Int> = Triple(rating, service, ratingRaw)

    override fun getServiceAccountName(): String? = savedServiceAccountName

    override fun upsertServiceAccount(service: String, accountName: String) {
        serviceAccounts[service] = accountName
    }

    override fun getAllServiceAccounts(): Map<String, String> = serviceAccounts

    override fun getServiceAccountByService(service: String): String? = serviceAccounts[service]

    override fun deleteServiceAccount(service: String) { serviceAccounts.remove(service) }

    override fun hasAnyServiceAccount(): Boolean = serviceAccounts.isNotEmpty()

    override fun saveLastUserSide(userSide: String?) { savedLastUserSide = userSide }

    override fun getLastUserSide(): String? = savedLastUserSide

    override fun saveConsentAcceptedAt(epochSeconds: Long) = Unit

    override fun getConsentAcceptedAt(): Long? = null

    override fun saveAccountDeclined(declined: Boolean) { accountDeclined = declined }

    override fun isAccountDeclined(): Boolean = accountDeclined

    override fun saveAutoUpload(enabled: Boolean) = Unit

    override fun getAutoUpload(): Boolean = false

    override fun saveThemeMode(themeMode: String) = Unit

    override fun getThemeMode(): String = "system"

    override fun saveServiceRank(service: String, rule: String, rankRaw: Int) {
        serviceRanks.getOrPut(service) { mutableMapOf() }[rule] = rankRaw
    }

    override fun getAllServiceRanks(): Map<String, Map<String, Int>> = serviceRanks

    override fun deleteServiceRank(service: String, rule: String) {
        serviceRanks[service]?.remove(rule)
    }

    override fun saveEvalDisplay(mode: String) = Unit

    override fun getEvalDisplay(): String = "cp"

    override fun saveSkipSideConfirm(skip: Boolean) { savedSkipSideConfirm = skip }

    override fun getSkipSideConfirm(): Boolean = savedSkipSideConfirm

    override fun saveAppPolicyCache(json: String) = Unit

    override fun getAppPolicyCache(): String? = null
}
