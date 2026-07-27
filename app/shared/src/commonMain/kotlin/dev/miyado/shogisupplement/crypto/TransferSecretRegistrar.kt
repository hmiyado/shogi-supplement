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
    /**
     * 未登録なら登録を試みる。
     *
     * 失敗しても例外を投げず [Result.failure] を返す（呼び出し元の同意フローは
     * 「登録失敗は致命にしない」設計方針のため、結果を無視してよい）。
     */
    suspend fun registerIfNeeded(userId: String): Result<Unit>
}
