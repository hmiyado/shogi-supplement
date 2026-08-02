package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SQLDelight正式マイグレーション（1.sqm）の単体テスト。
 *
 * 実DB（TestFlight初回配布分等。version=1）を手動DDLで再現し、
 * Schema.migrate(driver, 1, 2) 適用後にGameRepositoryの公開APIでINSERT/SELECTの
 * 往復が成功することを確認する（スキーマ構造の一致は別途機械検証されるため、
 * ここでは「実際にmigrateを呼んだときアプリの実利用経路が壊れないか」を確認する）。
 *
 * v1のblunder_reportは既にsecond_usi/second_cpを持つ一方、position_evalのみが
 * 移行対象という非対称な状態がある。これを正確に再現しないと、テストが実機と
 * 異なる前提で「たまたま成功する」だけの検証になってしまう。
 */
class SchemaMigrationTest {

    private fun createV1Schema(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE game (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                file_name TEXT NOT NULL,
                content_hash TEXT NOT NULL,
                move_count INTEGER NOT NULL,
                sente_name TEXT,
                gote_name TEXT,
                analyzed_at INTEGER NOT NULL,
                rating INTEGER NOT NULL,
                rating_sample_moves INTEGER,
                coef_version TEXT NOT NULL,
                kif_text TEXT,
                uploaded_at INTEGER,
                moves_usi TEXT,
                user_side TEXT,
                rating_service TEXT,
                rating_raw INTEGER,
                rating_rule TEXT,
                source_place TEXT,
                game_winner TEXT,
                end_reason TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE blunder_report (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                game_id INTEGER NOT NULL REFERENCES game(id),
                ply INTEGER NOT NULL,
                side TEXT NOT NULL,
                move_usi TEXT NOT NULL,
                best_usi TEXT,
                loss_wp REAL NOT NULL,
                sfen_before TEXT NOT NULL,
                category TEXT NOT NULL,
                diff_material INTEGER NOT NULL,
                punish_checks INTEGER NOT NULL,
                took_moved_piece INTEGER NOT NULL,
                missed_mate_in INTEGER,
                verdict TEXT NOT NULL,
                note TEXT NOT NULL,
                problem_type TEXT NOT NULL,
                priority REAL NOT NULL,
                best_pv TEXT,
                punish_pv TEXT,
                cp_before INTEGER,
                cp_after INTEGER,
                second_usi TEXT,
                second_cp INTEGER
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE position_eval (
                game_id INTEGER NOT NULL REFERENCES game(id),
                ply INTEGER NOT NULL,
                score_cp INTEGER,
                mate_in INTEGER,
                best_usi TEXT,
                second_score_cp INTEGER,
                second_mate_in INTEGER,
                PRIMARY KEY(game_id, ply)
            )
            """.trimIndent(),
            0,
        )
        // AndroidSqliteDriver/NativeSqliteDriverはPRAGMA user_versionでスキーマ版数を
        // 管理し、これが実際のschema.versionより小さければmigrateを呼ぶ。ここでは
        // その状態（version=1のDBが既に存在する）を手動で再現する。
        driver.execute(null, "PRAGMA user_version = 1", 0)
    }

    private fun sampleReport() = BlunderReport(
        ply = 2,
        side = "gote",
        moveUsi = "3c3d",
        bestUsi = "8c8d",
        lossWp = 0.2,
        classification = ClassificationResult(
            category = "駒損（タクティクス）",
            diffMaterial = -5,
            punishChecks = 0,
            tookMovedPiece = false,
            missedMateIn = null,
        ),
        judgement = Judgement(
            kind = VerdictKind.TARGET,
            verdict = "○ 出題対象",
            note = "テスト用note",
            problem = "テスト用problem",
            priority = 1.0,
        ),
        secondUsi = "6i7h",
        secondCp = 100,
    )

    @Test
    fun `v1スキーマのDBにSchema_migrateを適用するとINSERT_SELECTが成功する`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)

        ShogiSupplementDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val gameId = repo.saveAnalysis(
            fileName = "legacy.kif",
            contentHash = "legacy-hash",
            moves = listOf("7g7f", "3c3d"),
            headers = emptyMap(),
            reports = listOf(sampleReport()),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        val restoredReports = repo.getReports(gameId)
        assertEquals(1, restoredReports.size)
        assertEquals("6i7h", restoredReports[0].secondUsi)

        repo.savePositionEvals(
            gameId,
            listOf(
                PositionEvalRow(ply = 0, scoreCp = 50, mateIn = null, bestUsi = "7g7f", secondUsi = "2g2f"),
            ),
        )
        val restoredEvals = repo.getPositionEvals(gameId)
        assertEquals(1, restoredEvals.size)
        assertEquals("2g2f", restoredEvals[0].secondUsi)
    }
}
