package dev.miyado.shogisupplement.supabase

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.SupabaseAuthRepository
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
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
 */
class SupabaseServices(
    supabaseUrl: String,
    supabaseKey: String,
    gameRepository: GameRepository,
    settingsRepository: SettingsRepository,
    transferSecretStore: TransferSecretStore,
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
}
