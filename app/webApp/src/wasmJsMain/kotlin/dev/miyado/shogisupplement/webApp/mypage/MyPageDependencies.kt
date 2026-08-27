package dev.miyado.shogisupplement.webApp.mypage

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.SupabaseAuthRepository
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.crypto.WasmJsTransferSecretStore
import dev.miyado.shogisupplement.download.GameSummaryService
import dev.miyado.shogisupplement.download.SupabaseGameSummaryService
import dev.miyado.shogisupplement.transfer.RemoteTransferRestoreService
import dev.miyado.shogisupplement.transfer.TransferRestoreService
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Web版マイページのDIハブ。モバイル版の`SupabaseServices`と異なりローカルDB
 * （GameRepository/DrillRepository）に依存しない（今回のスコープが一覧表示のみのため）。
 */
class MyPageDependencies {
    private val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }

    val transferSecretStore: TransferSecretStore = WasmJsTransferSecretStore()
    private val settingsRepository = InMemorySettingsRepository()

    val authRepository: AuthRepository = SupabaseAuthRepository(client)

    // appCheckTokenProvider未設定: 本番のCloud RunはFirebase App Checkを必須化しているが、
    // Web向けのApp Check実装（reCAPTCHA v3等）はまだ無い。本番投入はその対応が入るまで
    // 見送る前提（App Check未設定のステージング/ローカル環境でのみ疎通する）。
    val transferRestoreService: TransferRestoreService = RemoteTransferRestoreService(
        baseUrl = WORKER_BASE_URL,
        authRepository = authRepository,
        transferSecretStore = transferSecretStore,
        settingsRepository = settingsRepository,
        platform = "web",
    )

    val gameSummaryService: GameSummaryService =
        SupabaseGameSummaryService(client, transferSecretStore, authRepository)
}
