package dev.miyado.shogisupplement.kifu

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** KIFを受け取ってから保存を依頼するまでの分岐（アカウント確認・棋力設定・先後確認）を保証する。 */
@OptIn(ExperimentalCoroutinesApi::class)
class KifImportControllerTest {

    private val kif = """
        手合割：平手
        先手：miyado
        後手：相手
        手数----指手---------消費時間--
        1 ７六歩(77)
        2 ３四歩(33)
        3 投了
    """.trimIndent()

    private class Recorder {
        val requests = mutableListOf<KifImportRequest>()
        val last: KifImportRequest get() = requests.last()
    }

    private fun build(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        analysisWouldCreateAccount: Boolean = false,
        recorder: Recorder = Recorder(),
    ) = KifImportController(
        settingsRepository = settings,
        // 保存の依頼をその場で実行させ、状態の落ち着き先を同期的に確かめる。
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        analysisWouldCreateAccount = { analysisWouldCreateAccount },
        dateTimeLabel = { "2026-09-01 12:00" },
        onImport = { recorder.requests.add(it) },
    ) to recorder

    @Test
    fun `アカウント名未設定なら先に棋力設定へ進む`() {
        val (controller, _) = build()

        controller.beginFromFile("game.kif", kif)

        val step = assertIs<KifImportController.Step.RatingSetup>(controller.step.value)
        assertEquals("miyado", step.kif.senteName)
    }

    @Test
    fun `棋力設定を確定するとアカウント名を保存して先後確認へ進む`() {
        val settings = FakeSettingsRepository()
        val (controller, _) = build(settings)
        controller.beginFromFile("game.kif", kif)

        controller.completeRatingSetup("lishogi", 1700, "standard", mapOf("lishogi" to "miyado"), emptyMap())

        assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
        assertEquals("miyado", settings.serviceAccounts["lishogi"])
    }

    @Test
    fun `アカウント名が設定済みなら棋力設定を挟まず先後確認へ進む`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "miyado"))
        val (controller, _) = build(settings)

        controller.beginFromFile("game.kif", kif)

        val step = assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
        assertEquals("sente", step.suggestion.side)
        assertTrue(step.suggestion.matchedByAccount)
    }

    @Test
    fun `アカウント名一致かつ省略設定ONなら確認なしで保存へ進む`() {
        val settings = FakeSettingsRepository(
            serviceAccounts = mutableMapOf("lishogi" to "miyado"),
            savedSkipSideConfirm = true,
        )
        val (controller, recorder) = build(settings)

        controller.beginFromFile("game.kif", kif)

        assertEquals(KifImportController.Step.Idle, controller.step.value)
        assertEquals(1, recorder.requests.size)
        assertEquals("sente", recorder.last.userSide)
    }

    @Test
    fun `推定が前回選択どまりなら省略設定がONでも確認を出す`() {
        val settings = FakeSettingsRepository(
            serviceAccounts = mutableMapOf("lishogi" to "別人"),
            savedSkipSideConfirm = true,
            savedLastUserSide = "gote",
        )
        val (controller, recorder) = build(settings)

        controller.beginFromFile("game.kif", kif)

        val step = assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
        assertEquals("gote", step.suggestion.side)
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun `先後を確定すると選択を保存して取込を依頼する`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "別人"))
        val (controller, recorder) = build(settings)
        controller.beginFromFile("game.kif", kif)

        controller.confirmSide("gote", skipNext = true)

        assertEquals("gote", settings.savedLastUserSide)
        assertEquals(KifImportController.Step.Idle, controller.step.value)
        assertEquals("game.kif", recorder.last.fileName)
    }

    @Test
    fun `省略設定はアカウント名一致で推定できたときだけ保存する`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "別人"))
        val (controller, _) = build(settings)
        controller.beginFromFile("game.kif", kif)

        controller.confirmSide("gote", skipNext = true)

        assertEquals(false, settings.savedSkipSideConfirm, "推定が外れる経路で省略を覚えないはず")
    }

    @Test
    fun `申告棋力を棋譜へ記録する`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "miyado"))
        settings.saveRatingSettings("shogi_wars", 1600, "standard", "miyado")
        val (controller, recorder) = build(settings)

        controller.beginFromFile("game.kif", kif)
        controller.confirmSide("sente", skipNext = false)

        assertEquals("shogi_wars", recorder.last.ratingService)
        assertEquals(1600L, recorder.last.ratingRaw)
        assertEquals("standard", recorder.last.ratingRule)
    }

    @Test
    fun `KIFとしては読めるがパースできない棋譜も保存まで進める`() {
        // 駒落ちのように KifParser が拒否する棋譜。ここで止めると、保存時にしか出せない
        // 固有の理由が汎用の文言に置き換わってしまう。
        val handicap = kif.replace("手合割：平手", "手合割：香落ち")
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "miyado"))
        val (controller, _) = build(settings)

        controller.beginFromFile("game.kif", handicap)

        assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
    }

    @Test
    fun `未ログインで解析へ進む場合はアカウント作成の確認を先に出す`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "miyado"))
        val (controller, recorder) = build(settings, analysisWouldCreateAccount = true)

        controller.beginFromFile("game.kif", kif)

        assertIs<KifImportController.Step.AccountCreationConfirm>(controller.step.value)
        assertTrue(recorder.requests.isEmpty())

        controller.confirmAccountCreation()
        assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
    }

    @Test
    fun `作らないと決めたら以後は確認を出さない`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "miyado"))
        val (controller, _) = build(settings, analysisWouldCreateAccount = true)
        controller.beginFromFile("game.kif", kif)

        controller.declineAccount()

        assertEquals(true, settings.accountDeclined)
        assertIs<KifImportController.Step.SideConfirm>(controller.step.value)

        controller.dismiss()
        controller.beginFromFile("game.kif", kif)
        assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
    }

    @Test
    fun `手入力はアカウント名未設定でも棋力設定を挟まない`() {
        val (controller, _) = build()

        controller.beginManual(kif, "manual.kif")

        assertIs<KifImportController.Step.SideConfirm>(controller.step.value)
    }

    @Test
    fun `クリップボード取込は日時つきのファイル名を付ける`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "miyado"))
        val (controller, recorder) = build(settings)

        controller.beginFromClipboard(kif)
        controller.confirmSide("sente", skipNext = false)

        assertTrue(recorder.last.fileName.contains("2026-09-01 12:00"))
    }

    @Test
    fun `空や不正なテキストは取込元ごとの文言で失敗にする`() {
        val (controller, _) = build()

        controller.beginFromClipboard(null)
        var failed = assertIs<KifImportController.Step.Failed>(controller.step.value)
        assertEquals(KifOrigin.CLIPBOARD, failed.origin)

        controller.beginFromFile("game.kif", "これは棋譜ではない")
        failed = assertIs<KifImportController.Step.Failed>(controller.step.value)
        assertEquals(KifOrigin.FILE, failed.origin)
    }

    @Test
    fun `確定前のキャンセルは取込を依頼しない`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "別人"))
        val (controller, recorder) = build(settings)
        controller.beginFromFile("game.kif", kif)

        controller.dismiss()

        assertEquals(KifImportController.Step.Idle, controller.step.value)
        assertTrue(recorder.requests.isEmpty())
        controller.confirmSide("sente", skipNext = false)
        assertTrue(recorder.requests.isEmpty(), "キャンセル後の確定は無視するはず")
    }

    @Test
    fun `保存中は先後の確定をもう一度受け付けない`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "別人"))
        val recorder = Recorder()
        val controller = KifImportController(
            settingsRepository = settings,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            dateTimeLabel = { "2026-09-01 12:00" },
            // 保存が終わらない間に二度目の確定が来る状況を作る。
            onImport = { recorder.requests.add(it); CompletableDeferred<Unit>().await() },
        )
        controller.beginFromFile("game.kif", kif)

        controller.confirmSide("sente", skipNext = false)
        controller.confirmSide("sente", skipNext = false)

        assertIs<KifImportController.Step.Saving>(controller.step.value)
        assertEquals(1, recorder.requests.size)
    }

    @Test
    fun `保存が失敗しても取込フローは畳む`() {
        val settings = FakeSettingsRepository(serviceAccounts = mutableMapOf("lishogi" to "別人"))
        val controller = KifImportController(
            settingsRepository = settings,
            scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
            dateTimeLabel = { "2026-09-01 12:00" },
            onImport = { error("保存に失敗") },
        )
        controller.beginFromFile("game.kif", kif)

        controller.confirmSide("sente", skipNext = false)

        assertEquals(KifImportController.Step.Idle, controller.step.value)
    }
}
