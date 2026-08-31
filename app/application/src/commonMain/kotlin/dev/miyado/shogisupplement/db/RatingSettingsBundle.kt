package dev.miyado.shogisupplement.db

/**
 * 現在選択中サービスのアカウント名は user_settings.service_account_name にも書く。
 * この列を読む経路が残っているため、service_account テーブルだけでは足りない。
 */
fun SettingsRepository.saveRatingSettingsBundle(
    service: String?,
    ratingRaw: Int?,
    ratingRule: String?,
    serviceAccounts: Map<String, String>,
    serviceRanks: Map<String, Map<String, Int>> = emptyMap(),
) {
    saveRatingSettings(service, ratingRaw, ratingRule, service?.let { serviceAccounts[it] })
    for ((svc, name) in serviceAccounts) {
        upsertServiceAccount(svc, name)
    }
    for ((svc, rules) in serviceRanks) {
        for ((rule, rankRaw) in rules) {
            saveServiceRank(svc, rule, rankRaw)
        }
    }
}
