package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlin.test.Test
import kotlin.test.assertEquals

/** version 1の実スキーマから最新までの移行と公開APIの往復を検証する。 */
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
            CREATE TABLE user_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                rating INTEGER NOT NULL DEFAULT 1750,
                consent_accepted_at INTEGER,
                auto_upload INTEGER NOT NULL DEFAULT 0,
                rating_service TEXT NOT NULL DEFAULT 'lishogi',
                rating_raw INTEGER NOT NULL DEFAULT 1750,
                last_user_side TEXT,
                service_account_name TEXT,
                rating_rule TEXT,
                theme_mode TEXT NOT NULL DEFAULT 'system',
                eval_display TEXT NOT NULL DEFAULT 'cp',
                skip_side_confirm INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO game (
                file_name, content_hash, move_count, analyzed_at, rating, coef_version
            ) VALUES ('existing.kif', 'existing-hash', 2, 1, 1750, 'hao_v1')
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
        driver.execute(
            null,
            """
            CREATE TABLE drill_attempt (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                blunder_report_id INTEGER NOT NULL REFERENCES blunder_report(id),
                user_move_usi TEXT NOT NULL,
                is_correct INTEGER NOT NULL,
                loss_wp REAL,
                attempted_at INTEGER NOT NULL
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

        ShogiSupplementDatabase.Schema.migrate(
            driver,
            oldVersion = 1,
            newVersion = ShogiSupplementDatabase.Schema.version,
        )

        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(GameAnalysisStatus.COMPLETED, repo.getGameById(1)!!.analysisStatus)
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
