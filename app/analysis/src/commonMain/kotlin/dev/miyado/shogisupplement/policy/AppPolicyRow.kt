package dev.miyado.shogisupplement.policy

import kotlinx.serialization.Serializable

/**
 * `app_policy` テーブル（infra/supabase/migrations/20260803120000_create_app_policy.sql）の1行。
 *
 * platform = "android" / "ios" / "common"。"common" 行は message のみを持ち、
 * minBuild/storeUrl は null（テーブル定義の check 制約と対応）。
 *
 * [kotlinx.serialization.Serializable] を付けているのは Supabase 応答のデコードではなく
 * （そちらは:sharedの専用DTOが担う。supabase-ktの型を公開APIに出さない方針のため）、
 * [dev.miyado.shogisupplement.db.SettingsRepository.saveAppPolicyCache] の
 * 端末キャッシュ用JSONシリアライズに使うため。
 */
@Serializable
data class AppPolicyRow(
    val platform: String,
    val minBuild: Int?,
    val storeUrl: String?,
    val message: String?,
)
