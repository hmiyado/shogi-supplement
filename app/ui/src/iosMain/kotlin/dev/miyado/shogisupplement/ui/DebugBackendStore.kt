@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.supabase.AuthSessionStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.native.Platform
import platform.Foundation.NSUserDefaults

/** 起動時にだけ接続先を確定し、保存後も稼働中のクライアントを切り替えない。 */
internal object DebugBackendStore {
    private const val KEY = "debug.backend.profile"
    private val defaults get() = NSUserDefaults.standardUserDefaults
    private fun entries(text: String) = text.lineSequence().mapNotNull {
        val i = it.indexOf('=')
        if (i > 0) it.take(i).trim() to it.drop(i + 1).trim() else null
    }.toMap()

    val active: Map<String, String>? by lazy {
        if (Platform.isDebugBinary) defaults.stringForKey(KEY)?.let(::entries) else null
    }
    val namespace: String? get() = active?.get("SUPABASE_URL")?.encodeToByteArray()?.joinToString("") {
        it.toUByte().toString(16).padStart(2, '0')
    }
    fun savedHost(): String = defaults.stringForKey(KEY)?.let(::entries)?.get("WEB_BASE_URL").orEmpty()
    fun clear() { if (Platform.isDebugBinary) defaults.removeObjectForKey(KEY) }

    suspend fun save(input: String): Boolean {
        if (!Platform.isDebugBinary) return false
        return runCatching {
            val base = Url(input.trim().trimEnd('/'))
            require(base.protocol.name == "http" && base.encodedPath in listOf("", "/") && base.parameters.isEmpty())
            require(base.user.isNullOrEmpty() && base.password.isNullOrEmpty() && base.fragment.isEmpty())
            val parts = base.host.split('.').map { it.toIntOrNull() ?: -1 }
            require(parts.size == 4 && parts.all { it in 0..255 } &&
                (parts[0] == 10 || parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1] in 16..31))
            val client = HttpClient(Darwin) {
                followRedirects = false
                install(HttpTimeout) { requestTimeoutMillis = 5_000 }
            }
            val text = try {
                val response = client.get("${base.toString().trimEnd('/')}/development-config.properties")
                require(response.status.value == 200)
                response.bodyAsText().also { require(it.length <= 16_384) }
            } finally { client.close() }
            val config = entries(text)
            for (key in listOf("SUPABASE_URL", "GAME_SHARE_API_BASE_URL", "GAME_SHARE_WEB_URL")) {
                val url = Url(requireNotNull(config[key]))
                require(url.protocol.name == "http" && url.host == base.host && url.user.isNullOrEmpty() && url.password.isNullOrEmpty())
                require(url.parameters.isEmpty() && url.fragment.isEmpty())
                val expectedPath = if (key == "GAME_SHARE_WEB_URL") "/kento.html" else "/"
                require(url.encodedPath == expectedPath || expectedPath == "/" && url.encodedPath.isEmpty())
            }
            require(config["ANALYSIS_BASE_URL"].isNullOrEmpty())
            // ローカル配信ファイルには公開用anonキーだけを置く。
            val token = requireNotNull(config["SUPABASE_KEY"])
            val payload = kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(token.split('.')[1]).decodeToString()
            require(Json.parseToJsonElement(payload).jsonObject["role"]?.jsonPrimitive?.content == "anon")
            val allowed = listOf("SUPABASE_URL", "SUPABASE_KEY", "GAME_SHARE_API_BASE_URL", "GAME_SHARE_WEB_URL")
            defaults.setObject((allowed.map { "$it=${config.getValue(it)}" } + "WEB_BASE_URL=${base.toString().trimEnd('/')}").joinToString("\n"), forKey = KEY)
        }.isSuccess
    }

    fun authStorage(): AuthSessionStorage? = namespace?.let { ns ->
        object : AuthSessionStorage {
            private val key = "debug.backend.session.$ns"
            override fun load() = defaults.stringForKey(key)
            override fun save(value: String) = defaults.setObject(value, forKey = key)
            override fun clear() = defaults.removeObjectForKey(key)
        }
    }
}
