package dev.miyado.shogisupplement.policy

import dev.miyado.shogisupplement.db.SettingsRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 起動時／フォアグラウンド復帰時の強制アップデート判定の調停役。
 *
 * 優先順位（fail-open最優先。Why not 取得失敗時に一律ブロック: 配信側の障害で
 * 全ユーザーが起動不能になる事故のほうが、ビルド制限を一時的に見逃す被害より大きい）:
 *   1. 取得成功 → 判定に使い、次回の取得失敗に備えてキャッシュへ保存
 *   2. 取得失敗 → 直近のキャッシュがあればそれで判定
 *   3. 取得失敗 かつ キャッシュも無い（初回起動でオフライン等）→ 非ブロックで即返す
 *
 * [AppPolicyRepository]・[SettingsRepository] はどちらもインターフェースのみに依存するため、
 * supabase-kt・SQLDelight実体を持ち込まずにテスト可能（Fakeで代替できる）。
 *
 * @param currentBuild 自分の側のビルド番号を返す関数。expect/actual実体
 *   （:sharedの[dev.miyado.shogisupplement.policy.currentBuildNumber]相当）を
 *   呼び出し側から注入する（このクラス自体はプラットフォーム非依存に保つため）。
 */
class ForceUpdatePolicyChecker(
    private val policyRepository: AppPolicyRepository,
    private val settingsRepository: SettingsRepository,
    private val platform: String,
    private val currentBuild: () -> Int,
) {
    suspend fun check(): ForceUpdateJudge.Decision {
        val rows = fetchAndCache() ?: loadCache()
        // 取得失敗 かつ キャッシュも無い = fail-open（非ブロックで即返す。判定材料が無い以上、
        // 可用性を優先する。SAFE側に倒すのでstoreUrl/messageも出さない）。
        ?: return ForceUpdateJudge.Decision(blocked = false, storeUrl = null, message = null)

        return ForceUpdateJudge.evaluate(platform, currentBuild(), rows)
    }

    private suspend fun fetchAndCache(): List<AppPolicyRow>? {
        val rows = policyRepository.fetchPolicies().getOrNull() ?: return null
        settingsRepository.saveAppPolicyCache(Json.encodeToString(rows))
        return rows
    }

    private fun loadCache(): List<AppPolicyRow>? {
        val json = settingsRepository.getAppPolicyCache() ?: return null
        // キャッシュJSONの破損（旧バージョンのスキーマ差異等）はfail-open側へ倒す。
        return runCatching { Json.decodeFromString<List<AppPolicyRow>>(json) }.getOrNull()
    }
}
