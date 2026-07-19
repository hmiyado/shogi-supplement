package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SettingsRepository の単体テスト。
 * インメモリSQLiteで user_settings・service_account・service_rank の保存復元を検証する。
 */
class SettingsRepositoryTest {

    private fun newRepository(): SettingsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return SettingsRepository(ShogiSupplementDatabase(driver))
    }

    @Test
    fun `レートはupsertで保存・更新でき未設定なら1750`() {
        val repo = newRepository()
        assertEquals(1750, repo.getRating())

        repo.saveRating(1600)
        assertEquals(1600, repo.getRating())

        repo.saveRating(1900)
        assertEquals(1900, repo.getRating())
    }

    @Test
    fun `consent_accepted_atが保存復元できる`() {
        val repo = newRepository()
        // 初期状態は null
        assertNull(repo.getConsentAcceptedAt())

        // 保存
        val epochSeconds = 1_780_000_000L
        repo.saveConsentAcceptedAt(epochSeconds)
        assertEquals(epochSeconds, repo.getConsentAcceptedAt())

        // 上書き（アカウント再作成など）
        val newEpoch = 1_790_000_000L
        repo.saveConsentAcceptedAt(newEpoch)
        assertEquals(newEpoch, repo.getConsentAcceptedAt())
    }

    @Test
    fun `consentAcceptedAt保存後もratingが保持される`() {
        val repo = newRepository()
        repo.saveRating(1850)
        assertEquals(1850, repo.getRating())

        // consent 保存で rating が消えないことを確認
        repo.saveConsentAcceptedAt(1_780_000_000L)
        assertEquals(1850, repo.getRating())
        assertNotNull(repo.getConsentAcceptedAt())
    }

    @Test
    fun `rating保存後もconsentAcceptedAtが保持される`() {
        val repo = newRepository()
        repo.saveConsentAcceptedAt(1_780_000_000L)
        assertNotNull(repo.getConsentAcceptedAt())

        // rating 上書きで consent が消えないことを確認
        repo.saveRating(1600)
        assertEquals(1600, repo.getRating())
        assertNotNull(repo.getConsentAcceptedAt())
    }

    @Test
    fun `autoUploadのデフォルトはfalse`() {
        val repo = newRepository()
        assertFalse(repo.getAutoUpload())
    }

    @Test
    fun `autoUploadが保存復元できる`() {
        val repo = newRepository()
        repo.saveAutoUpload(true)
        assertTrue(repo.getAutoUpload())

        repo.saveAutoUpload(false)
        assertFalse(repo.getAutoUpload())
    }

    // ─── service_rank ────────────────────────────────────────────────────────

    @Test
    fun `service_rankの保存と取得が正しく動作する`() {
        val repo = newRepository()
        repo.saveServiceRank("shogi_wars", "10min", 30)
        repo.saveServiceRank("shogi_wars", "3min", 28)
        repo.saveServiceRank("kiou", "serious", 27)

        val ranks = repo.getAllServiceRanks()
        assertEquals(30, ranks["shogi_wars"]?.get("10min"))
        assertEquals(28, ranks["shogi_wars"]?.get("3min"))
        assertEquals(27, ranks["kiou"]?.get("serious"))
    }

    @Test
    fun `service_rankのupsertは既存行を上書きする`() {
        val repo = newRepository()
        repo.saveServiceRank("shogi_wars", "10min", 30)
        repo.saveServiceRank("shogi_wars", "10min", 31)

        val ranks = repo.getAllServiceRanks()
        assertEquals(31, ranks["shogi_wars"]?.get("10min"))
    }

    @Test
    fun `service_rankの削除が正しく動作する`() {
        val repo = newRepository()
        repo.saveServiceRank("shogi_wars", "10min", 30)
        repo.saveServiceRank("shogi_wars", "3min", 28)

        repo.deleteServiceRank("shogi_wars", "10min")

        val ranks = repo.getAllServiceRanks()
        assertNull(ranks["shogi_wars"]?.get("10min"))
        assertEquals(28, ranks["shogi_wars"]?.get("3min"))
    }

    // ─── service_account ────────────────────────────────────────────────────────

    @Test
    fun `service_accountの保存と取得が正しく動作する`() {
        val repo = newRepository()
        repo.upsertServiceAccount("lishogi", "miyado")
        repo.upsertServiceAccount("shogi_wars", "miyado_wars")

        val accounts = repo.getAllServiceAccounts()
        assertEquals("miyado", accounts["lishogi"])
        assertEquals("miyado_wars", accounts["shogi_wars"])
    }

    @Test
    fun `service_accountのupsertは既存行を上書きする`() {
        val repo = newRepository()
        repo.upsertServiceAccount("lishogi", "old_name")
        repo.upsertServiceAccount("lishogi", "new_name")

        val accounts = repo.getAllServiceAccounts()
        assertEquals("new_name", accounts["lishogi"])
        assertEquals(1, accounts.size)
    }

    @Test
    fun `service_accountの削除が正しく動作する`() {
        val repo = newRepository()
        repo.upsertServiceAccount("lishogi", "miyado")
        repo.upsertServiceAccount("shogi_wars", "miyado_wars")

        repo.deleteServiceAccount("lishogi")

        val accounts = repo.getAllServiceAccounts()
        assertNull(accounts["lishogi"])
        assertEquals("miyado_wars", accounts["shogi_wars"])
    }

    @Test
    fun `hasAnyServiceAccountは未設定でfalseを返す`() {
        val repo = newRepository()
        assertFalse(repo.hasAnyServiceAccount())
    }

    @Test
    fun `hasAnyServiceAccountは設定済みでtrueを返す`() {
        val repo = newRepository()
        repo.upsertServiceAccount("lishogi", "miyado")
        assertTrue(repo.hasAnyServiceAccount())
    }

    @Test
    fun `getServiceAccountByServiceは設定済みサービスの名前を返す`() {
        val repo = newRepository()
        repo.upsertServiceAccount("lishogi", "miyado")

        assertEquals("miyado", repo.getServiceAccountByService("lishogi"))
        assertNull(repo.getServiceAccountByService("shogi_wars"))
    }

    // ─── theme_mode ──────────────────────────────────────────────────────────

    @Test
    fun `themeModeのデフォルトはsystem`() {
        val repo = newRepository()
        assertEquals("system", repo.getThemeMode())
    }

    @Test
    fun `themeModeが保存復元できる`() {
        val repo = newRepository()
        repo.saveThemeMode("dark")
        assertEquals("dark", repo.getThemeMode())

        repo.saveThemeMode("light")
        assertEquals("light", repo.getThemeMode())

        repo.saveThemeMode("system")
        assertEquals("system", repo.getThemeMode())
    }

    @Test
    fun `themeMode保存後も既存設定が保持される`() {
        val repo = newRepository()
        repo.saveRating(1850)
        repo.saveThemeMode("dark")

        assertEquals(1850, repo.getRating())
        assertEquals("dark", repo.getThemeMode())
    }

    // ─── eval_display ────────────────────────────────────────────────────────

    @Test
    fun `evalDisplayのデフォルトはcp`() {
        val repo = newRepository()
        assertEquals("cp", repo.getEvalDisplay())
    }

    @Test
    fun `evalDisplayが保存復元できる`() {
        val repo = newRepository()
        repo.saveEvalDisplay("wp")
        assertEquals("wp", repo.getEvalDisplay())

        repo.saveEvalDisplay("cp")
        assertEquals("cp", repo.getEvalDisplay())
    }

    @Test
    fun `evalDisplay保存後も他の設定が保持される`() {
        val repo = newRepository()
        repo.saveRating(1750)
        repo.saveThemeMode("dark")
        repo.saveEvalDisplay("wp")

        assertEquals(1750, repo.getRating())
        assertEquals("dark", repo.getThemeMode())
        assertEquals("wp", repo.getEvalDisplay())
    }

    // ─── skip_side_confirm ─────────────────────────────────────────────────

    @Test
    fun `skipSideConfirmのデフォルトはfalse`() {
        val repo = newRepository()
        assertFalse(repo.getSkipSideConfirm())
    }

    @Test
    fun `skipSideConfirmが保存復元できる`() {
        val repo = newRepository()
        repo.saveSkipSideConfirm(true)
        assertTrue(repo.getSkipSideConfirm())

        repo.saveSkipSideConfirm(false)
        assertFalse(repo.getSkipSideConfirm())
    }

    @Test
    fun `skipSideConfirm保存後も他の設定が保持される`() {
        val repo = newRepository()
        repo.saveRating(1750)
        repo.saveEvalDisplay("wp")
        repo.saveSkipSideConfirm(true)

        assertEquals(1750, repo.getRating())
        assertEquals("wp", repo.getEvalDisplay())
        assertTrue(repo.getSkipSideConfirm())
    }
}
