package dev.miyado.shogisupplement

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.SupabaseAuthRepository
import dev.miyado.shogisupplement.crypto.AndroidTransferSecretStore
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.policy.AppPolicyRepository
import dev.miyado.shogisupplement.policy.ForceUpdateJudge
import dev.miyado.shogisupplement.policy.ForceUpdatePolicyChecker
import dev.miyado.shogisupplement.policy.SupabasePolicyRepository
import dev.miyado.shogisupplement.policy.currentBuildNumber
import dev.miyado.shogisupplement.policy.resolvePolicyPlatform
import dev.miyado.shogisupplement.upload.SupabaseUploadRepository
import dev.miyado.shogisupplement.upload.UploadOrchestrator
import dev.miyado.shogisupplement.upload.UploadRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /**
     * private_enc暗号化用のK_enc導出元（端末シークレットSの永続化。Android=Keystoreで
     * 包んだSharedPreferences。crypto/TransferSecretStore.android.kt参照）。
     */
    private val transferSecretStore: TransferSecretStore by lazy {
        AndroidTransferSecretStore(this)
    }

    /** アップロードリポジトリのシングルトン。 */
    val uploadRepository: UploadRepository by lazy {
        SupabaseUploadRepository(supabaseClient, transferSecretStore)
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

    /** 強制アップデートポリシー（`app_policy`）のanon SELECT。 */
    private val appPolicyRepository: AppPolicyRepository by lazy {
        SupabasePolicyRepository(supabaseClient)
    }

    /**
     * 起動時・フォアグラウンド復帰時に呼ぶ強制アップデート判定の調停役
     * （[checkForceUpdate] から呼ぶ。取得失敗時のフォールバックは
     * [ForceUpdatePolicyChecker] のKDoc参照）。
     */
    private val forceUpdatePolicyChecker: ForceUpdatePolicyChecker by lazy {
        ForceUpdatePolicyChecker(
            policyRepository = appPolicyRepository,
            settingsRepository = AppDatabase.settingsRepository(this),
            // Debugビルドは検証用のdev行（android-dev）を読み、本番行（android）を動かさずに
            // 動作確認できるようにする（resolvePolicyPlatform参照。BuildConfigはandroidApp自身の
            // 生成物＝実際にインストールされるAPKのビルドタイプと必ず一致する）。
            platform = resolvePolicyPlatform("android", BuildConfig.DEBUG),
            currentBuild = ::currentBuildNumber,
        )
    }

    /**
     * Applicationのプロセス生存期間で使い回すスコープ。強制アップデート判定は
     * Activity再生成（画面回転等）をまたいで状態を保ちたいため、Activity/ViewModelスコープ
     * ではなくここで持つ（ForceUpdateHost.kt はこのStateFlowをcollectAsStateで購読するだけ）。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _forceUpdateDecision = MutableStateFlow<ForceUpdateJudge.Decision?>(null)

    /** null = 未チェック。[ForceUpdateHost] はnullの間は通常どおりの画面を出す。 */
    val forceUpdateDecision: StateFlow<ForceUpdateJudge.Decision?> = _forceUpdateDecision.asStateFlow()

    /** [MainActivity.onResume] から呼ぶ（起動直後の初回resumeも含めて「起動時＋フォアグラウンド復帰時」を満たす）。 */
    fun checkForceUpdate() {
        appScope.launch { _forceUpdateDecision.value = forceUpdatePolicyChecker.check() }
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
