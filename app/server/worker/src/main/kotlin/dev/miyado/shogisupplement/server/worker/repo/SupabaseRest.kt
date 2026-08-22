package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

// service_role キーは apikey / Authorization の両方に渡す（Supabaseの慣例。片方だけだと
// RLSバイパスが効かずクエリが失敗する）。
internal fun HttpRequestBuilder.supabaseServiceRoleHeaders(serviceRoleKey: String) {
    header("apikey", serviceRoleKey)
    header(HttpHeaders.Authorization, "Bearer $serviceRoleKey")
}

internal fun restUrl(supabaseUrl: String, table: String): String =
    "${supabaseUrl.trimEnd('/')}/rest/v1/$table"

// PostgREST用JsonはencodeDefaults=trueとする。既定値のフィールドもPATCH/POSTへ送るため。
// decode側の欠損補完には影響しない。
val supabaseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
