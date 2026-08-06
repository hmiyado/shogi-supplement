package dev.miyado.shogisupplement.policy

/**
 * `app_policy`（infra/supabase/migrations/20260807120000_add_dev_policy_rows.sql）の
 * platform列キーを決定する。Debugビルドは検証用のdev行（android-dev/ios-dev）を読み、
 * 実ユーザーがいる本番行（android/ios）を動かさずに強制アップデート判定を検証できるようにする。
 *
 * fail-safe方向: [isDebugBuild] の判定に自信が持てない呼び出し元はfalseを渡すこと
 * （本番行を読む側に倒す。dev行を誤ってReleaseが読むのが最悪ケースのため）。
 */
fun resolvePolicyPlatform(basePlatform: String, isDebugBuild: Boolean): String =
    if (isDebugBuild) "$basePlatform-dev" else basePlatform
