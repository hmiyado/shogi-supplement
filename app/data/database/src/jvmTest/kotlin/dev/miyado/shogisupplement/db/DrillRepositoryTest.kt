package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DrillRepository の単体テスト。
 * インメモリSQLiteで出題候補選定・解答履歴の保存復元を検証する。
 */
class DrillRepositoryTest {

    // date(attempted_at, 'unixepoch', 'localtime', ...) はJVMのTimeZone.setDefault()ではなく
    // プロセスのOSタイムゾーンに従う（SQLiteネイティブライブラリのlocaltime_r依存）。
    // 日付境界のテストのタイムゾーン固定はbuild.gradle.ktsのjvmTestタスク（TZ環境変数）で行う。

    private fun newGameRepository(database: ShogiSupplementDatabase): GameRepository = SqlDelightGameRepository(database)
    private fun newDrillRepository(database: ShogiSupplementDatabase): DrillRepository = SqlDelightDrillRepository(database)

    private fun newDatabase(): ShogiSupplementDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return ShogiSupplementDatabase(driver)
    }

    private fun sampleReport() = BlunderReport(
        ply = 41,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        classification = ClassificationResult(
            category = "駒損（タクティクス）",
            diffMaterial = -11,
            punishChecks = 0,
            tookMovedPiece = false,
            missedMateIn = null,
        ),
        judgement = Judgement(
            kind = VerdictKind.TARGET,
            verdict = "○ 出題対象",
            note = "自帯6.3件/1000手 (上帯5.2件)。帯として典型的なミス",
            problem = "手筋 (両取り・素抜き) の問題",
            priority = 2.9978349024480666,
        ),
    )

    /** ◎1件・○1件・△1件を保存し、blunder_report の id リストを返すヘルパ。 */
    private fun saveDrillFixture(gameRepo: GameRepository): List<BlunderRecord> {
        val reports = listOf(
            sampleReport().copy(
                ply = 1,
                judgement = sampleReport().judgement.copy(
                    kind = VerdictKind.PRIORITY, verdict = "◎ 優先出題", priority = 5.0,
                ),
            ),
            sampleReport().copy(ply = 2), // ○ 出題対象 (priority≈3.0)
            sampleReport().copy(
                ply = 3,
                judgement = sampleReport().judgement.copy(
                    kind = VerdictKind.SKIP, verdict = "△ 見送り", priority = 0.5,
                ),
            ),
        )
        val gameId = gameRepo.saveAnalysis(
            fileName = "g.kif",
            contentHash = "h",
            moves = listOf("7g7f", "3c3d", "2g2f"),
            headers = emptyMap(),
            reports = reports,
            rating = 1750,
            coefVersion = "hao_v1",
        )
        return gameRepo.getReports(gameId)
    }

    @Test
    fun `ドリル出題候補は◎と○のみで◎が先頭になる`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        saveDrillFixture(gameRepo)

        val candidates = drillRepo.getDrillCandidates()
        assertEquals(2, candidates.size, "△見送りは出題しない")
        assertEquals("◎ 優先出題", candidates[0].verdict)
        assertEquals("○ 出題対象", candidates[1].verdict)
    }

    @Test
    fun `棋譜単位のドリル候補は指定した棋譜だけを返し悪手単体も取得できる`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val firstGameReports = saveDrillFixture(gameRepo)
        val secondGameId = gameRepo.saveAnalysis(
            fileName = "other.kif",
            contentHash = "other-hash",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = listOf(sampleReport().copy(ply = 99)),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        val candidates = drillRepo.getDrillCandidatesByGame(secondGameId)
        assertEquals(1, candidates.size)
        assertEquals(secondGameId, candidates.single().gameId)
        assertEquals(candidates.single(), drillRepo.getBlunderById(candidates.single().id))
        assertEquals(null, drillRepo.getBlunderById(-1L))
        assertEquals(
            firstGameReports.count { it.verdict.startsWith("◎") || it.verdict.startsWith("○") },
            drillRepo.getDrillCandidatesByGame(firstGameReports.first().gameId).size,
        )
    }

    @Test
    fun `ドリル解答が保存復元できる`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        val id1 = drillRepo.saveDrillAttempt(
            blunderReportId = target.id,
            userMoveUsi = "2f6f",
            isCorrect = true,
            lossWp = 0.0,
            attemptedAt = 1_780_000_100L,
            readPv = "3c3d 2f2e",
        )
        val id2 = drillRepo.saveDrillAttempt(
            blunderReportId = target.id,
            userMoveUsi = "B*3d",
            isCorrect = false,
            lossWp = null, // エンジン判定不能ケース
            attemptedAt = 1_780_000_200L,
        )
        assertTrue(id1 > 0 && id2 > id1)

        val attempts = drillRepo.getDrillAttempts(target.id)
        assertEquals(2, attempts.size)
        // 新しい順
        assertEquals("B*3d", attempts[0].userMoveUsi)
        assertEquals(false, attempts[0].isCorrect)
        assertNull(attempts[0].lossWp)
        assertEquals(1_780_000_200L, attempts[0].attemptedAt)
        assertNull(attempts[0].readPv, "読み筋を入力しなかった解答はnull")
        assertEquals("2f6f", attempts[1].userMoveUsi)
        assertEquals(true, attempts[1].isCorrect)
        assertEquals(0.0, attempts[1].lossWp!!, 1e-12)
        assertEquals("3c3d 2f2e", attempts[1].readPv)

        // 他のblunder_reportの履歴は混ざらない
        assertTrue(drillRepo.getDrillAttempts(target.id + 999).isEmpty())
    }

    @Test
    fun `未送信解答は古い順に取得でき同期情報を更新できる`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()
        // getDrillAttemptsNotUploadedは棋譜アップロード済みの解答のみを対象にする
        gameRepo.updateUploadedAt(target.gameId, 1L)

        val oldest = drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = 100L)
        val middle = drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = 200L)
        val newest = drillRepo.saveDrillAttempt(target.id, "2g2f", true, null, attemptedAt = 300L)
        drillRepo.updateDrillAttemptSyncId(oldest, "sync-oldest")
        drillRepo.updateDrillAttemptUploadedAt(middle, 400L)

        val pending = drillRepo.getDrillAttemptsNotUploaded(limit = 10)
        assertEquals(listOf(oldest, newest), pending.map { it.id })
        assertEquals(listOf(100L, 300L), pending.map { it.attemptedAt })
        assertEquals("sync-oldest", pending.first().syncId)
        assertEquals(null, pending.first().uploadedAt)
    }

    @Test
    fun `棋譜が未アップロードの解答はgetDrillAttemptsNotUploadedの対象から外れる`() {
        // 棋譜がSupabase未アップロードだと解答送信は外部キー違反で必ず失敗するため、
        // その解答が古い順の先頭に居座って後続の送信可能な解答を止めないようにする。
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()
        // gameRepo.updateUploadedAtを呼ばない＝棋譜は未アップロードのまま

        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = 100L)

        assertTrue(drillRepo.getDrillAttemptsNotUploaded(limit = 10).isEmpty())
    }

    private fun epochSecondsAt(tokyoDateTime: String): Long =
        ZonedDateTime.parse("$tokyoDateTime+09:00[Asia/Tokyo]").toEpochSecond()

    @Test
    fun `累計解答数は全件をカウントする`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        assertEquals(0, drillRepo.getDrillAttemptCountTotal())
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = 100L)
        drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = 200L)
        assertEquals(2, drillRepo.getDrillAttemptCountTotal())
    }

    @Test
    fun `同じ日に複数回解答しても取組日数は1日として数える`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()
        val asOf = epochSecondsAt("2025-06-20T00:00:00")

        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-15T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = epochSecondsAt("2025-06-15T21:00:00"))
        drillRepo.saveDrillAttempt(target.id, "2g2f", true, null, attemptedAt = epochSecondsAt("2025-06-16T08:00:00"))

        assertEquals(2, drillRepo.getDrillAttemptActiveDayCount(windowDays = 30, asOfEpochSeconds = asOf))
    }

    @Test
    fun `日付境界には深夜0時から4時間の猶予があり前日の続きとして数える`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()
        val asOf = epochSecondsAt("2025-06-20T00:00:00")

        // 6/15 23:50 に解答 → そのまま6/15扱い
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-15T23:50:00"))
        // 6/16 00:10（日付が変わって10分後）に解答 → 猶予内なので6/15の続きとして扱われ、
        // 取組「日数」は増えない（一発リセットにしない設計の核）
        drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = epochSecondsAt("2025-06-16T00:10:00"))

        assertEquals(1, drillRepo.getDrillAttemptActiveDayCount(windowDays = 30, asOfEpochSeconds = asOf))

        // 6/16 04:10（猶予を過ぎた後）に解答 → ここでようやく新しい1日として数える
        drillRepo.saveDrillAttempt(target.id, "2g2f", true, null, attemptedAt = epochSecondsAt("2025-06-16T04:10:00"))
        assertEquals(2, drillRepo.getDrillAttemptActiveDayCount(windowDays = 30, asOfEpochSeconds = asOf))
    }

    @Test
    fun `取組日数はwindowDays暦日より前を対象にしない`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-05-01T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = epochSecondsAt("2025-06-15T10:00:00"))

        // 基準2025-06-20・窓30日 → 境界は2025-05-22。5/1は範囲外、6/15は範囲内。
        val asOf = epochSecondsAt("2025-06-20T00:00:00")
        assertEquals(1, drillRepo.getDrillAttemptActiveDayCount(windowDays = 30, asOfEpochSeconds = asOf))
    }

    @Test
    fun `取組日数の窓は暦日ちょうどで区切る（時刻の端数で31日分数えない）`() {
        // 720時間（30日）の単純な引き算だと、基準時刻の時・分ぶん余分な暦日が
        // 混ざり得る（例: 基準が正午なら前日正午からの範囲は31個の暦日にまたがる）。
        // 暦日そのものをSQLiteの日付演算に委ねているため混ざらないことを確認する。
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()
        val asOf = epochSecondsAt("2025-06-20T12:00:00")

        // 基準の30日前の日付（境界の1日前・境界日）に解答しておく
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-05-21T08:00:00"))
        drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = epochSecondsAt("2025-05-22T08:00:00"))

        assertEquals(1, drillRepo.getDrillAttemptActiveDayCount(windowDays = 30, asOfEpochSeconds = asOf))
    }

    @Test
    fun `基準時刻より後の日付は端末時計のズレ等で紛れ込んでも数えない`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()
        val asOf = epochSecondsAt("2025-06-20T00:00:00")

        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-15T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "3c3d", false, 0.1, attemptedAt = epochSecondsAt("2025-06-25T10:00:00"))

        assertEquals(1, drillRepo.getDrillAttemptActiveDayCount(windowDays = 30, asOfEpochSeconds = asOf))
    }

    private fun saveAttemptsOnDates(drillRepo: DrillRepository, blunderReportId: Long, vararg tokyoDates: String) {
        tokyoDates.forEachIndexed { i, tokyoDate ->
            drillRepo.saveDrillAttempt(blunderReportId, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("${tokyoDate}T10:00:00") + i)
        }
    }

    @Test
    fun `7日未満の連続取組は7日ストリークとして数えない`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        saveAttemptsOnDates(
            drillRepo, target.id,
            "2025-06-01", "2025-06-02", "2025-06-03", "2025-06-04", "2025-06-05", "2025-06-06",
        )

        assertEquals(0, drillRepo.getDrillAttemptWeekStreakCount())
    }

    @Test
    fun `7日連続で1回、14日連続で2回、途切れた別区間は合算しない`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        // 6/1〜6/7 の7日連続（1回目）
        saveAttemptsOnDates(
            drillRepo, target.id,
            "2025-06-01", "2025-06-02", "2025-06-03", "2025-06-04", "2025-06-05", "2025-06-06", "2025-06-07",
        )
        assertEquals(1, drillRepo.getDrillAttemptWeekStreakCount())

        // 間隔（6/8を抜かす）を空けて 6/9〜6/22 の14日連続（2回加算）
        saveAttemptsOnDates(
            drillRepo, target.id,
            "2025-06-09", "2025-06-10", "2025-06-11", "2025-06-12", "2025-06-13", "2025-06-14", "2025-06-15",
            "2025-06-16", "2025-06-17", "2025-06-18", "2025-06-19", "2025-06-20", "2025-06-21", "2025-06-22",
        )
        assertEquals(3, drillRepo.getDrillAttemptWeekStreakCount())
    }

    @Test
    fun `深夜0時から4時までの猶予は7日ストリークの連続判定にも及ぶ`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-01T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-02T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-03T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-04T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-05T10:00:00"))
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-06T10:00:00"))
        // 6/7は日付として記録されず、6/8の深夜0時10分（猶予内＝6/7の続き扱い）に解答
        drillRepo.saveDrillAttempt(target.id, "7g7f", true, 0.0, attemptedAt = epochSecondsAt("2025-06-08T00:10:00"))

        assertEquals(1, drillRepo.getDrillAttemptWeekStreakCount())
    }
}
