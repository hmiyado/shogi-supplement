package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.server.worker.repo.SupabaseStaticGameShareRepository
import dev.miyado.shogisupplement.server.worker.repo.supabaseJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking

fun runGameShareCleanup() = runBlocking {
    val base = requireNotNull(System.getenv("SUPABASE_URL")).trimEnd('/')
    val key = requireNotNull(System.getenv("SUPABASE_SERVICE_ROLE_KEY"))
    HttpClient(CIO) {
        install(ContentNegotiation) { json(supabaseJson) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }.use { client ->
        val count = SupabaseStaticGameShareRepository(client, base, key).cleanup()
        println("Shared game objects removed: $count")
    }
}
