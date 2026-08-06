package dev.miyado.shogisupplement.supabase

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.SupabaseAuthRepository
import dev.miyado.shogisupplement.consent.ConsentOrchestrator
import dev.miyado.shogisupplement.crypto.TransferCode
import dev.miyado.shogisupplement.crypto.TransferSecretManager
import dev.miyado.shogisupplement.crypto.TransferSecretRegistrar
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.policy.AppPolicyRepository
import dev.miyado.shogisupplement.policy.ForceUpdatePolicyChecker
import dev.miyado.shogisupplement.policy.SupabasePolicyRepository
import dev.miyado.shogisupplement.policy.currentBuildNumber
import dev.miyado.shogisupplement.upload.SupabaseTransferSecretRegistrar
import dev.miyado.shogisupplement.upload.SupabaseUploadRepository
import dev.miyado.shogisupplement.upload.UploadOrchestrator
import dev.miyado.shogisupplement.upload.UploadRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase連携（匿名認証＋棋譜アップロード）の依存一式を組み立てるファクトリ。
 *
 * supabase-kt の型は公開APIに出さない（AuthRepository 等のインターフェースのみを公開する）。
 * これにより消費側モジュール（:ui）は supabase-kt への直接依存なしで配線できる。
 *
 * @param transferSecretStore private_enc暗号化用のK_enc導出元（端末シークレットSの永続化）。
 *   Context相当の引数が要る/要らないがプラットフォームで違う（androidApp/db/AppDatabase.kt が
 *   Context を明示的に受け取る既存パターンと同じ理由）ため、呼び出し側
 *   （:ui iosMain/MainViewController.kt）で組み立てて渡す
 * @param platform 強制アップデート判定（[forceUpdatePolicyChecker]）の対象プラットフォーム行。
 *   "android" / "ios"（app_policyテーブルのplatform列と同じ語彙。Debugビルドは呼び出し側が
 *   [dev.miyado.shogisupplement.policy.resolvePolicyPlatform] で "android-dev" / "ios-dev" に
 *   変換してから渡す）
 */
class SupabaseServices(
    supabaseUrl: String,
    supabaseKey: String,
    gameRepository: GameRepository,
    settingsRepository: SettingsRepository,
    private val transferSecretStore: TransferSecretStore,
    platform: String,
) {
    private val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Auth)
        install(Postgrest)
    }

    val authRepository: AuthRepository = SupabaseAuthRepository(client)
    val uploadRepository: UploadRepository = SupabaseUploadRepository(client, transferSecretStore)
    val uploadOrchestrator: UploadOrchestrator = UploadOrchestrator(
        authRepository = authRepository,
        uploadRepository = uploadRepository,
        dbRepository = gameRepository,
        settingsRepository = settingsRepository,
    )

    /** K_authハッシュの登録（設計書 付録「引き継ぎコードの詳細仕様」節）。 */
    val transferSecretRegistrar: TransferSecretRegistrar =
        SupabaseTransferSecretRegistrar(client, transferSecretStore)

    /** 同意オンボーディング（iOS専用・初回起動必須）の完了処理。 */
    val consentOrchestrator: ConsentOrchestrator = ConsentOrchestrator(
        settingsRepository = settingsRepository,
        authRepository = authRepository,
        transferSecretRegistrar = transferSecretRegistrar,
    )

    /**
     * 設定画面「引き継ぎコード」表示用。端末シークレットSが未生成なら遅延生成する
     * （[TransferSecretManager.getOrCreateSecret] 参照）。
     */
    suspend fun getOrCreateTransferCode(): String {
        val secret = TransferSecretManager.getOrCreateSecret(transferSecretStore)
        return TransferCode.encode(secret)
    }

    /** 強制アップデートポリシー（`app_policy`）のanon SELECT。 */
    val appPolicyRepository: AppPolicyRepository = SupabasePolicyRepository(client)

    /**
     * 起動時・フォアグラウンド復帰時に呼ぶ強制アップデート判定の調停役。
     * 取得失敗時はキャッシュ→fail-openの順にフォールバックする
     * （[ForceUpdatePolicyChecker] のKDoc参照）。
     */
    val forceUpdatePolicyChecker: ForceUpdatePolicyChecker = ForceUpdatePolicyChecker(
        policyRepository = appPolicyRepository,
        settingsRepository = settingsRepository,
        platform = platform,
        currentBuild = ::currentBuildNumber,
    )
}
