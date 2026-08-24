package dev.miyado.shogisupplement.policy

import dev.miyado.shogisupplement.db.SettingsRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 起動時と復帰時の強制アップデート判定を調停する。
 * Why not 取得失敗時に一律ブロック: 配信障害で全ユーザーが起動不能になるため、fail-openを優先する。
 * 成功値を保存し、失敗時はキャッシュ、未保存時は非ブロックで判定する。
 * @param currentBuild 現在のビルド番号を返す関数。
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
