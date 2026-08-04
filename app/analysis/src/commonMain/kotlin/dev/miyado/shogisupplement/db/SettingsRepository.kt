package dev.miyado.shogisupplement.db

/** user_settings・サービスアカウント・段級（service_rank）の永続化リポジトリのインターフェース。 */
interface SettingsRepository {

    /** ユーザーレートを保存する（upsert）。 */
    fun saveRating(rating: Int)

    /** ユーザーレート・サービス・raw値を保存する（upsert）。 */
    fun saveRatingFull(rating: Int, service: String, ratingRaw: Int)

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
    fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccountName: String?,
    )

    /**
     * レート・サービス・raw値・ルール・アカウント名をまとめて返す。
     * 未設定なら RatingSettings(1750, "lishogi", 1750, null, null)。
     */
    fun getRatingSettings(): RatingSettings

    /** ユーザーが棋力設定を一度でも保存したかどうか（デフォルト値と区別するため）。 */
    fun hasUserSavedRatingSettings(): Boolean

    /** 保存されたレートを返す。未設定なら 1750。 */
    fun getRating(): Int

    /**
     * 保存されたレート・サービス・raw値を返す。
     * 未設定なら (1750, "lishogi", 1750)。
     */
    fun getRatingFull(): Triple<Int, String, Int>

    /** サービスアカウント名を返す（未設定なら null）。旧テーブル（user_settings）から取得。 */
    fun getServiceAccountName(): String?

    /**
     * サービスのアカウント名を保存する（upsert）。
     * 先後の自動推定に使用。全サービスのいずれかと一致すればそのサービスの側を推定する。
     */
    fun upsertServiceAccount(service: String, accountName: String)

    /** 全サービスのアカウント名を返す。service → account_name のマップ。 */
    fun getAllServiceAccounts(): Map<String, String>

    /** 指定サービスのアカウント名を返す（未設定なら null）。 */
    fun getServiceAccountByService(service: String): String?

    /** 指定サービスのアカウント名を削除する。 */
    fun deleteServiceAccount(service: String)

    /** いずれかのサービスにアカウント名が設定されているかどうか。 */
    fun hasAnyServiceAccount(): Boolean

    /** 最後に選んだ user_side を保存する。 */
    fun saveLastUserSide(userSide: String?)

    /** 最後に選んだ user_side を返す。未設定なら null。 */
    fun getLastUserSide(): String?

    /**
     * 利用規約・プライバシーポリシーへの同意日時を記録する（Unix epoch 秒）。
     * アカウント作成成功時に呼び出す。
     */
    fun saveConsentAcceptedAt(epochSeconds: Long)

    /**
     * 同意日時を返す（Unix epoch 秒）。
     * 未記録なら null。
     */
    fun getConsentAcceptedAt(): Long?

    /** 解析後自動アップロード設定を保存する。 */
    fun saveAutoUpload(enabled: Boolean)

    /** 解析後自動アップロード設定を返す。未設定なら false（デフォルト OFF）。 */
    fun getAutoUpload(): Boolean

    /** テーマモードを保存する（'system' / 'light' / 'dark'）。 */
    fun saveThemeMode(themeMode: String)

    /** テーマモードを返す。未設定なら 'system'。 */
    fun getThemeMode(): String

    /** サービスのルール別棋力を保存する（申告のみ、相応判定には使用しない）。 */
    fun saveServiceRank(service: String, rule: String, rankRaw: Int)

    /** 全サービスのルール別棋力を返す。service → rule → rankRaw のネスト Map。 */
    fun getAllServiceRanks(): Map<String, Map<String, Int>>

    /** サービスのルール別棋力を削除する。 */
    fun deleteServiceRank(service: String, rule: String)

    /**
     * 形勢の表示単位を保存する（'cp' = 評価値 / 'wp' = 勝率）。
     * デフォルト='cp'。
     */
    fun saveEvalDisplay(mode: String)

    /** 形勢の表示単位を返す。未設定なら 'cp'（デフォルト）。 */
    fun getEvalDisplay(): String

    /**
     * 先後確認の省略設定を保存する。
     * true = アカウント名一致時に側選択ダイアログを出さず即解析を開始する。
     */
    fun saveSkipSideConfirm(skip: Boolean)

    /** 先後確認の省略設定を返す。未設定なら false（確認する）。 */
    fun getSkipSideConfirm(): Boolean

    /**
     * 強制アップデートポリシー（[dev.miyado.shogisupplement.policy.AppPolicyRow] のリスト）の
     * 直近取得成功結果をJSON文字列で保存する（upsert）。
     * ポリシー取得に失敗した起動時のフォールバック（fail-open前の最後の手段）に使う。
     */
    fun saveAppPolicyCache(json: String)

    /** キャッシュされたポリシーJSONを返す。未取得なら null。 */
    fun getAppPolicyCache(): String?
}
