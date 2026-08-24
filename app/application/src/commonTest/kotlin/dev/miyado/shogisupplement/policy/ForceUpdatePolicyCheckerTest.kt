package dev.miyado.shogisupplement.policy

import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ForceUpdatePolicyChecker] の取得・キャッシュ・fail-open調停の単体テスト。
 *
 * :analysisはsupabase-kt/SQLDelightに依存しないため、[AppPolicyRepository]・
 * [SettingsRepository] とも本テスト専用の最小Fakeで代替する
 * （shared/jvmTest/ConsentOrchestratorTest.kt と同じ「テストローカルFake」方針）。
 */
class ForceUpdatePolicyCheckerTest {

    private val blockingRow = AppPolicyRow(platform = "android", minBuild = 100, storeUrl = "https://play.example/app", message = null)
    private val nonBlockingRow = AppPolicyRow(platform = "android", minBuild = 1, storeUrl = "https://play.example/app", message = null)

    private fun checker(
        repository: AppPolicyRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        currentBuild: Int = 50,
    ) = ForceUpdatePolicyChecker(
        policyRepository = repository,
        settingsRepository = settings,
        platform = "android",
        currentBuild = { currentBuild },
    ) to settings

    @Test
    fun `取得成功時はその結果で判定する`() = runTest {
        val (checker, _) = checker(FakeAppPolicyRepository(Result.success(listOf(blockingRow))), currentBuild = 50)
        assertTrue(checker.check().blocked)
    }

    @Test
    fun `build がminBuild以上なら通常起動`() = runTest {
        val (checker, _) = checker(FakeAppPolicyRepository(Result.success(listOf(nonBlockingRow))), currentBuild = 50)
        assertFalse(checker.check().blocked)
    }

    @Test
    fun `取得成功時は次回失敗に備えてキャッシュへ保存する`() = runTest {
        val (checker, settings) = checker(FakeAppPolicyRepository(Result.success(listOf(blockingRow))))
        checker.check()
        assertEquals(true, settings.getAppPolicyCache()?.isNotBlank())
    }

    @Test
    fun `取得失敗 かつ キャッシュも無ければfail-open（非ブロック）`() = runTest {
        val (checker, _) = checker(FakeAppPolicyRepository(Result.failure(RuntimeException("network"))))
        val decision = checker.check()
        assertFalse(decision.blocked)
        assertNull(decision.storeUrl)
        assertNull(decision.message)
    }

    @Test
    fun `取得失敗でもキャッシュがあればキャッシュを採用する`() = runTest {
        val settings = FakeSettingsRepository()
        // 前回起動時に取得成功していた想定でキャッシュを先に積んでおく。
        val (warmupChecker, _) = checker(FakeAppPolicyRepository(Result.success(listOf(blockingRow))), settings)
        warmupChecker.check()

        val (checker, _) = checker(FakeAppPolicyRepository(Result.failure(RuntimeException("network"))), settings)
        assertTrue(checker.check().blocked)
    }

    @Test
    fun `初回起動でオフラインならキャッシュも無くfail-open`() = runTest {
        val settings = FakeSettingsRepository()
        assertNull(settings.getAppPolicyCache())
        val (checker, _) = checker(FakeAppPolicyRepository(Result.failure(RuntimeException("network"))), settings)
        assertFalse(checker.check().blocked)
    }

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class FakeAppPolicyRepository(private val result: Result<List<AppPolicyRow>>) : AppPolicyRepository {
        override suspend fun fetchPolicies(): Result<List<AppPolicyRow>> = result
    }

    /** SettingsRepositoryの全メンバーを持つが、本テストではキャッシュ2メソッドのみを使う。 */
    private class FakeSettingsRepository : SettingsRepository {
        private var appPolicyCache: String? = null

        override fun saveRating(rating: Int) {}
        override fun saveRatingFull(rating: Int, service: String, ratingRaw: Int) {}
        override fun saveRatingSettings(service: String?, ratingRaw: Int?, ratingRule: String?, serviceAccountName: String?) {}
        override fun getRatingSettings(): RatingSettings = RatingSettings(1750, "lishogi", 1750, null, null)
        override fun hasUserSavedRatingSettings(): Boolean = false
        override fun getRating(): Int = 1750
        override fun getRatingFull(): Triple<Int, String, Int> = Triple(1750, "lishogi", 1750)
        override fun getServiceAccountName(): String? = null
        override fun upsertServiceAccount(service: String, accountName: String) {}
        override fun getAllServiceAccounts(): Map<String, String> = emptyMap()
        override fun getServiceAccountByService(service: String): String? = null
        override fun deleteServiceAccount(service: String) {}
        override fun hasAnyServiceAccount(): Boolean = false
        override fun saveLastUserSide(userSide: String?) {}
        override fun getLastUserSide(): String? = null
        override fun saveConsentAcceptedAt(epochSeconds: Long) {}
        override fun getConsentAcceptedAt(): Long? = null
        override fun saveAccountDeclined(declined: Boolean) {}
        override fun isAccountDeclined(): Boolean = false
        override fun saveAutoUpload(enabled: Boolean) {}
        override fun getAutoUpload(): Boolean = false
        override fun saveThemeMode(themeMode: String) {}
        override fun getThemeMode(): String = "system"
        override fun saveServiceRank(service: String, rule: String, rankRaw: Int) {}
        override fun getAllServiceRanks(): Map<String, Map<String, Int>> = emptyMap()
        override fun deleteServiceRank(service: String, rule: String) {}
        override fun saveEvalDisplay(mode: String) {}
        override fun getEvalDisplay(): String = "cp"
        override fun saveSkipSideConfirm(skip: Boolean) {}
        override fun getSkipSideConfirm(): Boolean = false
        override fun saveAppPolicyCache(json: String) { appPolicyCache = json }
        override fun getAppPolicyCache(): String? = appPolicyCache
    }
}
