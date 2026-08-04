package dev.miyado.shogisupplement.db

/** user_settings・サービスアカウント・段級（service_rank）のDB永続化リポジトリ（[SettingsRepository]のSQLDelight実装）。 */
class SqlDelightSettingsRepository(private val database: ShogiSupplementDatabase) : SettingsRepository {

    /** ユーザーレートを保存する（upsert）。 */
    override fun saveRating(rating: Int) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateRating(rating.toLong())
        }
    }

    /** ユーザーレート・サービス・raw値を保存する（upsert）。 */
    override fun saveRatingFull(rating: Int, service: String, ratingRaw: Int) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateRatingFull(rating.toLong(), service, ratingRaw.toLong())
        }
    }

    /**
     * サービス申告情報（サービス名・raw値・ルール・アカウント名）をまとめて保存する（upsert）。
     *
     * 相応判定には使わない（記録専用 + 先後自動選択用）。
     * rating（推定値）はここでは更新しない。
     *
     * @param service "lishogi" / "shogi_wars" / "kiou"（null = 未申告）
     * @param ratingRaw サービス上のraw値（ウォーズ・棋桜は段級位を整数エンコード、null = 未申告）
     * @param ratingRule ルール文字列（例: "10min" / "serious"、null = 未申告）
     * @param serviceAccountName このサービスでのアカウント名（先後自動選択に使用）
     */
    override fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccountName: String?,
    ) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateRatingSettings(
                // rating カラムは推定値保存用なのでここでは変更しない（既存値を維持するため現在値で上書き）
                database.shogiSupplementQueries.getRating().executeAsOneOrNull() ?: 1750L,
                service ?: "lishogi",
                ratingRaw?.toLong() ?: 0L,
                ratingRule,
                serviceAccountName,
            )
        }
    }

    /**
     * レート・サービス・raw値・ルール・アカウント名をまとめて返す。
     * 未設定なら RatingSettings(1750, "lishogi", 1750, null, null)。
     */
    override fun getRatingSettings(): RatingSettings {
        val row = database.shogiSupplementQueries.getRatingSettings().executeAsOneOrNull()
        return if (row != null) {
            RatingSettings(
                rating = row.rating.toInt(),
                service = row.rating_service,
                ratingRaw = row.rating_raw.toInt(),
                ratingRule = row.rating_rule,
                serviceAccountName = row.service_account_name,
            )
        } else {
            RatingSettings(1750, "lishogi", 1750, null, null)
        }
    }

    /** ユーザーが棋力設定を一度でも保存したかどうか（デフォルト値と区別するため）。 */
    override fun hasUserSavedRatingSettings(): Boolean =
        database.shogiSupplementQueries.getRatingSettings().executeAsOneOrNull() != null

    /** 保存されたレートを返す。未設定なら 1750。 */
    override fun getRating(): Int {
        return database.shogiSupplementQueries
            .getRating()
            .executeAsOneOrNull()
            ?.toInt() ?: 1750
    }

    /**
     * 保存されたレート・サービス・raw値を返す。
     * 未設定なら (1750, "lishogi", 1750)。
     */
    override fun getRatingFull(): Triple<Int, String, Int> {
        val row = database.shogiSupplementQueries
            .getRatingFull()
            .executeAsOneOrNull()
        return if (row != null) {
            Triple(row.rating.toInt(), row.rating_service, row.rating_raw.toInt())
        } else {
            Triple(1750, "lishogi", 1750)
        }
    }

    /** サービスアカウント名を返す（未設定なら null）。旧テーブル（user_settings）から取得。 */
    override fun getServiceAccountName(): String? {
        return database.shogiSupplementQueries
            .getServiceAccountName()
            .executeAsOneOrNull()
            ?.service_account_name
    }

    // ─── service_account（v8: サービスごとのアカウント名）─────────────────────

    /**
     * サービスのアカウント名を保存する（upsert）。
     * 先後の自動推定に使用。全サービスのいずれかと一致すればそのサービスの側を推定する。
     */
    override fun upsertServiceAccount(service: String, accountName: String) {
        database.shogiSupplementQueries.upsertServiceAccount(service, accountName)
    }

    /** 全サービスのアカウント名を返す。service → account_name のマップ。 */
    override fun getAllServiceAccounts(): Map<String, String> {
        return database.shogiSupplementQueries
            .getAllServiceAccounts()
            .executeAsList()
            .associate { it.service to it.account_name }
    }

    /** 指定サービスのアカウント名を返す（未設定なら null）。 */
    override fun getServiceAccountByService(service: String): String? {
        return database.shogiSupplementQueries
            .getServiceAccountByService(service)
            .executeAsOneOrNull()
    }

    /** 指定サービスのアカウント名を削除する。 */
    override fun deleteServiceAccount(service: String) {
        database.shogiSupplementQueries.deleteServiceAccountByService(service)
    }

    /** いずれかのサービスにアカウント名が設定されているかどうか。 */
    override fun hasAnyServiceAccount(): Boolean {
        return database.shogiSupplementQueries
            .getAllServiceAccounts()
            .executeAsList()
            .isNotEmpty()
    }

    /** 最後に選んだ user_side を保存する。 */
    override fun saveLastUserSide(userSide: String?) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateLastUserSide(userSide)
        }
    }

    /** 最後に選んだ user_side を返す。未設定なら null。 */
    override fun getLastUserSide(): String? {
        return database.shogiSupplementQueries
            .getLastUserSide()
            .executeAsOneOrNull()
            ?.last_user_side
    }

    /**
     * 利用規約・プライバシーポリシーへの同意日時を記録する（Unix epoch 秒）。
     * アカウント作成成功時に呼び出す。
     */
    override fun saveConsentAcceptedAt(epochSeconds: Long) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateConsentAcceptedAt(epochSeconds)
        }
    }

    /**
     * 同意日時を返す（Unix epoch 秒）。
     * 未記録なら null。
     */
    override fun getConsentAcceptedAt(): Long? {
        return database.shogiSupplementQueries
            .getConsentAcceptedAt()
            .executeAsOneOrNull()
            ?.consent_accepted_at
    }

    /**
     * 解析後自動アップロード設定を保存する。
     */
    override fun saveAutoUpload(enabled: Boolean) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateAutoUpload(if (enabled) 1L else 0L)
        }
    }

    /**
     * 解析後自動アップロード設定を返す。未設定なら false（デフォルト OFF）。
     */
    override fun getAutoUpload(): Boolean {
        return (database.shogiSupplementQueries
            .getAutoUpload()
            .executeAsOneOrNull() ?: 0L) != 0L
    }

    /**
     * テーマモードを保存する（'system' / 'light' / 'dark'）。
     */
    override fun saveThemeMode(themeMode: String) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateThemeMode(themeMode)
        }
    }

    /**
     * テーマモードを返す。未設定なら 'system'。
     */
    override fun getThemeMode(): String {
        return database.shogiSupplementQueries
            .getThemeMode()
            .executeAsOneOrNull() ?: "system"
    }

    // ─── ルール別棋力（service_rank） ─────────────────────────────────────────

    /** サービスのルール別棋力を保存する（申告のみ、相応判定には使用しない）。 */
    override fun saveServiceRank(service: String, rule: String, rankRaw: Int) {
        database.shogiSupplementQueries.upsertServiceRank(service, rule, rankRaw.toLong())
    }

    /** 全サービスのルール別棋力を返す。service → rule → rankRaw のネスト Map。 */
    override fun getAllServiceRanks(): Map<String, Map<String, Int>> {
        return database.shogiSupplementQueries
            .getServiceRanks()
            .executeAsList()
            .groupBy { it.service }
            .mapValues { (_, rows) ->
                rows.associate { it.rule to it.rank_raw.toInt() }
            }
    }

    /** サービスのルール別棋力を削除する。 */
    override fun deleteServiceRank(service: String, rule: String) {
        database.shogiSupplementQueries.deleteServiceRank(service, rule)
    }

    // ─── 形勢の表示単位（eval_display）─────────────────────────────────────────

    /**
     * 形勢の表示単位を保存する（'cp' = 評価値 / 'wp' = 勝率）。
     * デフォルト='cp'。
     */
    override fun saveEvalDisplay(mode: String) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateEvalDisplay(mode)
        }
    }

    /**
     * 形勢の表示単位を返す。未設定なら 'cp'（デフォルト）。
     */
    override fun getEvalDisplay(): String {
        return database.shogiSupplementQueries
            .getEvalDisplay()
            .executeAsOneOrNull() ?: "cp"
    }

    // ─── skip_side_confirm（先後確認の省略・v12）────────────────────────────────

    /**
     * 先後確認の省略設定を保存する。
     * true = アカウント名一致時に側選択ダイアログを出さず即解析を開始する。
     */
    override fun saveSkipSideConfirm(skip: Boolean) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateSkipSideConfirm(if (skip) 1L else 0L)
        }
    }

    /**
     * 先後確認の省略設定を返す。未設定なら false（確認する）。
     */
    override fun getSkipSideConfirm(): Boolean {
        return (database.shogiSupplementQueries
            .getSkipSideConfirm()
            .executeAsOneOrNull() ?: 0L) != 0L
    }

    // ─── app_policy_cache（強制アップデートポリシーの直近取得結果）──────────────

    /**
     * 強制アップデートポリシーの直近取得成功結果をJSONで保存する（upsert）。
     */
    override fun saveAppPolicyCache(json: String) {
        database.transaction {
            database.shogiSupplementQueries.insertOrIgnoreDefaultSettings()
            database.shogiSupplementQueries.updateAppPolicyCache(json)
        }
    }

    /**
     * キャッシュされたポリシーJSONを返す。未取得なら null。
     */
    override fun getAppPolicyCache(): String? {
        return database.shogiSupplementQueries
            .getAppPolicyCache()
            .executeAsOneOrNull()
            ?.app_policy_cache
    }
}
