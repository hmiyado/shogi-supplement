package dev.miyado.shogisupplement.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android側のDB初期化。DBインスタンスと3リポジトリをそれぞれシングルトンで保持する。
 */
object AppDatabase {

    @Volatile
    private var database: ShogiSupplementDatabase? = null

    @Volatile
    private var gameRepositoryInstance: GameRepository? = null

    @Volatile
    private var drillRepositoryInstance: DrillRepository? = null

    @Volatile
    private var settingsRepositoryInstance: SettingsRepository? = null

    fun gameRepository(context: Context): GameRepository {
        return gameRepositoryInstance ?: synchronized(this) {
            gameRepositoryInstance ?: GameRepository(getDatabase(context)).also { gameRepositoryInstance = it }
        }
    }

    fun drillRepository(context: Context): DrillRepository {
        return drillRepositoryInstance ?: synchronized(this) {
            drillRepositoryInstance ?: DrillRepository(getDatabase(context)).also { drillRepositoryInstance = it }
        }
    }

    fun settingsRepository(context: Context): SettingsRepository {
        return settingsRepositoryInstance ?: synchronized(this) {
            settingsRepositoryInstance ?: SettingsRepository(getDatabase(context)).also {
                settingsRepositoryInstance = it
            }
        }
    }

    private fun getDatabase(context: Context): ShogiSupplementDatabase {
        return database ?: synchronized(this) {
            database ?: createDatabase(context).also { database = it }
        }
    }

    private fun createDatabase(context: Context): ShogiSupplementDatabase {
        val driver = AndroidSqliteDriver(
            schema = ShogiSupplementDatabase.Schema,
            context = context.applicationContext,
            name = "shogi_supplement.db",
        )
        return ShogiSupplementDatabase(driver)
    }
}
