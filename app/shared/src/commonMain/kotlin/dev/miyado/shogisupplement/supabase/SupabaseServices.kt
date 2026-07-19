package dev.miyado.shogisupplement.supabase

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.SupabaseAuthRepository
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
 */
class SupabaseServices(
    supabaseUrl: String,
    supabaseKey: String,
    gameRepository: GameRepository,
    settingsRepository: SettingsRepository,
) {
    private val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Auth)
        install(Postgrest)
    }

    val authRepository: AuthRepository = SupabaseAuthRepository(client)
    val uploadRepository: UploadRepository = SupabaseUploadRepository(client)
    val uploadOrchestrator: UploadOrchestrator = UploadOrchestrator(
        authRepository = authRepository,
        uploadRepository = uploadRepository,
        dbRepository = gameRepository,
        settingsRepository = settingsRepository,
    )
}
