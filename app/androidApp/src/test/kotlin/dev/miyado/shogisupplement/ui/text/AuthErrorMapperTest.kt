package dev.miyado.shogisupplement.auth

import org.junit.Test
import kotlin.test.assertEquals

@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class AuthErrorMapperTest {


    @Test
    fun mapSignInAnonymouslyError_network_returnsJapanese() = kotlinx.coroutines.test.runTest {
        val e = RuntimeException("Unable to resolve host")
        assertEquals("ネットワークに接続できません。接続を確認してお試しください", AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    @Test
    fun mapSignInAnonymouslyError_networkByMessage_returnsJapanese() = kotlinx.coroutines.test.runTest {
        val e = RuntimeException("network error occurred")
        assertEquals("ネットワークに接続できません。接続を確認してお試しください", AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    @Test
    fun mapSignInAnonymouslyError_timeout_returnsNetwork() = kotlinx.coroutines.test.runTest {
        val e = RuntimeException("connection timeout")
        assertEquals("ネットワークに接続できません。接続を確認してお試しください", AuthErrorMapper.mapSignInAnonymouslyError(e))
    }

    @Test
    fun mapSignInAnonymouslyError_unknown_returnsGeneric() = kotlinx.coroutines.test.runTest {
        val e = RuntimeException("Some unexpected error from Supabase")
        assertEquals("データ提供の開始に失敗しました。時間をおいてお試しください", AuthErrorMapper.mapSignInAnonymouslyError(e))
    }


    @Test
    fun mapDeleteAccountError_network_returnsJapanese() = kotlinx.coroutines.test.runTest {
        val e = RuntimeException("connection refused")
        assertEquals("ネットワークに接続できません。接続を確認してお試しください", AuthErrorMapper.mapDeleteAccountError(e))
    }

    @Test
    fun mapDeleteAccountError_unknown_returnsGeneric() = kotlinx.coroutines.test.runTest {
        val e = RuntimeException("server error")
        assertEquals("データの削除に失敗しました。時間をおいてお試しください", AuthErrorMapper.mapDeleteAccountError(e))
    }
}
