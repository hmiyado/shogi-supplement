package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS側のDB初期化。DBインスタンスと3リポジトリをそれぞれシングルトンで保持する。
 *
 * androidApp/src/main/kotlin/dev/miyado/shogisupplement/db/AppDatabase.kt のiOS版。
 * Androidと違い Context 相当の引数は不要（NativeSqliteDriverはアプリの
 * ドキュメントディレクトリ配下に自動でファイルを作る）。
 */
object DatabaseFactory {

    private var database: ShogiSupplementDatabase? = null
    private var gameRepositoryInstance: GameRepository? = null
    private var drillRepositoryInstance: DrillRepository? = null
    private var settingsRepositoryInstance: SettingsRepository? = null

    fun gameRepository(): GameRepository =
        gameRepositoryInstance ?: GameRepository(getDatabase()).also { gameRepositoryInstance = it }

    fun drillRepository(): DrillRepository =
        drillRepositoryInstance ?: DrillRepository(getDatabase()).also { drillRepositoryInstance = it }

    fun settingsRepository(): SettingsRepository =
        settingsRepositoryInstance ?: SettingsRepository(getDatabase()).also { settingsRepositoryInstance = it }

    private fun getDatabase(): ShogiSupplementDatabase {
        return database ?: createDatabase().also { database = it }
    }

    private fun createDatabase(): ShogiSupplementDatabase {
        val driver = NativeSqliteDriver(
            schema = ShogiSupplementDatabase.Schema,
            name = "shogi_supplement.db",
        )
        return ShogiSupplementDatabase(driver)
    }
}
