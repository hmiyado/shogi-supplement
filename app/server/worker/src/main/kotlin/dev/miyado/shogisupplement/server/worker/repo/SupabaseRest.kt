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

// PostgREST向けHTTPクライアント（Application.kt の restClient）専用のJson。
// encodeDefaults=true が必須: この用途のペイロード（MarkDonePayload等）はデフォルト値付きの
// プロパティを持つが、kotlinx.serializationは既定でデフォルト値のプロパティを
// エンコード時に省略するため、encodeDefaults=falseのままだとPATCH/POSTのJSON本文から
// 該当フィールドが丸ごと消え、DB側の値が更新されない（例: statusが送られずrunningのまま残る）。
// decodeにはencodeDefaultsは影響しない（decode時の欠損フィールド補完は各プロパティの
// デフォルト値そのものによる）ため、レスポンスのパース（JobRow等）への影響はない。
val supabaseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
