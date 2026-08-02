package dev.miyado.shogisupplement.auth

import dev.miyado.shogisupplement.text.AppStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AuthErrorMapper の単体テスト（匿名認証）。
 * 代表エラー → 日本語文言のマッピングを検証する。
 *
 * 本テストは commonTest（iOSでも実行）に置くため、`java.net.UnknownHostException` /
 * `SocketTimeoutException` のようなJVM専用の例外クラスは使えない。AuthErrorMapper の判定は
 * メッセージ文字列ベースなので、`RuntimeException` に同一メッセージを与えて代用している
 * （判定ロジックへの影響はない）。
 */
class AuthErrorMapperTest {

    // ─── 匿名サインイン ───────────────────────────────────────────────────────

    @Test
    fun mapSignInAnonymouslyError_network_returnsJapanese() {
        val e = RuntimeException("Unable to resolve host")
        assertEquals(AppStrings.AUTH_ERROR_NETWORK, AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    @Test
    fun mapSignInAnonymouslyError_networkByMessage_returnsJapanese() {
        val e = RuntimeException("network error occurred")
        assertEquals(AppStrings.AUTH_ERROR_NETWORK, AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    @Test
    fun mapSignInAnonymouslyError_timeout_returnsNetwork() {
        val e = RuntimeException("connection timeout")
        assertEquals(AppStrings.AUTH_ERROR_NETWORK, AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    @Test
    fun mapSignInAnonymouslyError_unknown_returnsGeneric() {
        val e = RuntimeException("Some unexpected error from Supabase")
        assertEquals(AppStrings.AUTH_ERROR_ANON_SIGN_IN_GENERIC, AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    // ─── アカウント削除 ───────────────────────────────────────────────────────

    @Test
    fun mapDeleteAccountError_network_returnsJapanese() {
        val e = RuntimeException("connection refused")
        assertEquals(AppStrings.AUTH_ERROR_NETWORK, AuthErrorMapper.mapDeleteAccountError(e))
    }

    @Test
    fun mapDeleteAccountError_unknown_returnsGeneric() {
        val e = RuntimeException("server error")
        assertEquals(AppStrings.AUTH_ERROR_DELETE_GENERIC, AuthErrorMapper.mapDeleteAccountError(e))
    }
}
