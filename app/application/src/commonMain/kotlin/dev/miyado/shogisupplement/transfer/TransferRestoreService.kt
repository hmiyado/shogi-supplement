package dev.miyado.shogisupplement.transfer

import dev.miyado.shogisupplement.auth.AuthRepository

/** `POST /v1/transfer`（app/server/worker）を叩いて引き継ぎコードから旧アカウントを復元する結果。 */
sealed class TransferRestoreResult {
    data object Success : TransferRestoreResult()

    /** コードのチェックサム不一致・文字種/長さ不正（Crockford Base32デコードが失敗した）。 */
    data object InvalidCode : TransferRestoreResult()

    /** サーバーに一致するコードが無い（HTTP 404。理由は出し分けない）。 */
    data object NotFound : TransferRestoreResult()

    /** IPレート制限超過（HTTP 429）。 */
    data object RateLimited : TransferRestoreResult()

    /** アプリの強制アップデートが必要（HTTP 426）。 */
    data object UpgradeRequired : TransferRestoreResult()

    /** サーバーはセッションを発行したが、この端末への取り込み（[AuthRepository.importSession]）に失敗。 */
    data class SessionImportFailed(val message: String) : TransferRestoreResult()

    /** 通信断・想定外のHTTPステータス・レスポンス本文の形式不正等。 */
    data class NetworkError(val message: String) : TransferRestoreResult()
}

/**
 * 実装: RemoteTransferRestoreService（:shared・ktorでPOST /v1/transferを呼ぶ）。
 * ktor-client等プラットフォーム依存を持つ実装を:shared側に置き、この抽象だけを
 * wasmJsも含む全ターゲットが参照できる:analysisに置く（[AuthRepository] と同じ分割方針）。
 */
interface TransferRestoreService {
    /** [code] は引き継ぎコードの表記（ハイフン・空白・大文字小文字は問わない）。 */
    suspend fun restore(code: String): TransferRestoreResult
}
