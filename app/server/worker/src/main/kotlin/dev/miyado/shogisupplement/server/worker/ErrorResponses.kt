package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.ErrorJson
import org.slf4j.Logger
import java.util.UUID

/**
 * 原因をサーバーログにだけ残し、応答には相関IDだけを載せたエラーを返す。
 * DB・エンジン・上流サービス由来の文言をクライアントへ出さないための変換。
 */
fun maskedError(log: Logger, context: String, cause: Throwable? = null): ErrorJson {
    val reference = UUID.randomUUID().toString().take(8)
    if (cause != null) log.error("$context (ref=$reference)", cause) else log.error("$context (ref=$reference)")
    return ErrorJson("internal error (ref: $reference)")
}
