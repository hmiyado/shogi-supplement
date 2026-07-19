package dev.miyado.shogisupplement

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.SupabaseAuthRepository
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.upload.SupabaseUploadRepository
import dev.miyado.shogisupplement.upload.UploadOrchestrator
import dev.miyado.shogisupplement.upload.UploadRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.sentry.android.core.SentryAndroid

class ShogiApp : Application() {

    /** Auth + Postgrest を持つ共有 Supabase クライアント。 */
    val supabaseClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    /** 認証リポジトリのシングルトン。 */
    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(supabaseClient)
    }

    /** アップロードリポジトリのシングルトン。 */
    val uploadRepository: UploadRepository by lazy {
        SupabaseUploadRepository(supabaseClient)
    }

    /** アップロードオーケストレーターのシングルトン。 */
    val uploadOrchestrator: UploadOrchestrator by lazy {
        UploadOrchestrator(
            authRepository = authRepository,
            uploadRepository = uploadRepository,
            dbRepository = AppDatabase.gameRepository(this),
            settingsRepository = AppDatabase.settingsRepository(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        initSentry()
        createNotificationChannels()
    }

    private fun initSentry() {
        // Robolectric（VRT/unitテスト）ではShogiAppが本物同様にonCreateされるため、
        // ガードしないとテストのクラッシュ（テストJVMのOOM等）が本番プロジェクトへ
        // 送信される（2026-07-16実害: OutOfMemoryError×10件のテストノイズ）
        if (Build.FINGERPRINT == "robolectric") return
        // DSN未設定（local.propertiesにSENTRY_DSNが無いビルド）ではクラッシュレポートを
        // 無効化する（SupabaseのURL/KEYと同じgraceful degradationの方針）
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            // デバッグビルドとリリースビルドを区別してフィルタリングできるようにする
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = BuildConfig.VERSION_NAME
            // ユーザーのIPアドレス・ユーザー名等のデフォルトPIIを送信しない
            options.isSendDefaultPii = false
        }
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_ANALYSIS,
            "解析進捗",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "KIF解析の進捗を表示します"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ANALYSIS = "analysis_progress"
    }
}
