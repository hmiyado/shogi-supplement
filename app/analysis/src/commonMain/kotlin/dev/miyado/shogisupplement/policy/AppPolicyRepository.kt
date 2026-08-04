package dev.miyado.shogisupplement.policy

/**
 * 強制アップデートポリシー（`app_policy` テーブル）取得のインターフェース。
 * 実装: SupabasePolicyRepository（:shared）、テスト用Fake（各テスト）。
 *
 * AuthRepository/SettingsRepository と同じ方針で、supabase-kt の型は公開APIに出さない。
 */
interface AppPolicyRepository {
    /**
     * `app_policy` の全行をanonでSELECTする。
     * @return 成功時 Result.success(rows)、失敗時（ネットワーク断・パース失敗等）Result.failure
     */
    suspend fun fetchPolicies(): Result<List<AppPolicyRow>>
}
