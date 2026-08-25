package dev.miyado.shogisupplement.crypto

/**
 * K_authのSHA-256ハッシュをサーバー（user_transfer_secrets）へ登録するインターフェース
 * （設計書 付録「引き継ぎコードの詳細仕様」節。サーバーはK_auth自体を保存せずハッシュのみ持つ）。
 *
 * 実装: [dev.miyado.shogisupplement.upload.SupabaseTransferSecretRegistrar]（supabase-kt Postgrest）。
 * このインターフェース自体は supabase-kt へ依存しないため、呼び出し元
 * （[dev.miyado.shogisupplement.consent.ConsentOrchestrator]）をfakeで単体テストできる。
 */
interface TransferSecretRegistrar {
    /** 未登録なら登録する。失敗は例外にせず [Result.failure]（登録失敗は致命にしない）。 */
    suspend fun registerIfNeeded(userId: String): Result<Unit>

    /**
     * 認証用シークレットを引き直してハッシュを差し替える。
     * 失敗時は端末側も巻き戻す（コードとサーバーを食い違わせない）。
     */
    suspend fun rotate(): Result<Unit>
}
