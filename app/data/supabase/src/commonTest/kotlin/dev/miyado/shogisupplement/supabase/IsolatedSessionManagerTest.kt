package dev.miyado.shogisupplement.supabase

import io.github.jan.supabase.auth.user.UserSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class IsolatedSessionManagerTest {
    private class Storage : AuthSessionStorage {
        var value: String? = null
        override fun load() = value
        override fun save(value: String) { this.value = value }
        override fun clear() { value = null }
    }

    private fun session(token: String) = Json.decodeFromString<UserSession>(
        """{"access_token":"$token","refresh_token":"refresh-$token","expires_in":3600,"token_type":"bearer","user":{"id":"test-owner","aud":"authenticated"}}""",
    )

    @Test
    fun independentStorageNeverImportsAnotherBackendSession() = runTest {
        val productionStorage = Storage()
        val lanStorage = Storage()
        val production = IsolatedSessionManager(productionStorage)
        val lan = IsolatedSessionManager(lanStorage)
        production.saveSession(session("production"))
        assertNull(lan.loadSessionOrNull())
        lan.saveSession(session("lan"))
        assertEquals("production", production.loadSession().accessToken)
        assertEquals("lan", IsolatedSessionManager(lanStorage).loadSession().accessToken)
        lan.deleteSession()
        assertNull(lan.loadSessionOrNull())
        assertEquals("production", production.loadSession().accessToken)
    }

    @Test
    fun corruptedLocalSessionDoesNotRestoreAnyOtherSession() = runTest {
        val storage = Storage().apply { value = "invalid" }
        assertNull(IsolatedSessionManager(storage).loadSessionOrNull())
    }
}
