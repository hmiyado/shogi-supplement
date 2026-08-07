package dev.miyado.shogisupplement.api.transfer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// `POST /v1/transfer`（server/worker）のワイヤ形式。[dev.miyado.shogisupplement.api.analysis]の
// AnalysisRequest等と同じ理由でこの共通DTOを:sharedに置く（サーバー・クライアント双方から参照）。
// サーバー側の実装は app/server/worker の TransferRoutes.kt。

/**
 * `POST /v1/transfer` のリクエストボディ。JWT認証なし（未ログイン端末から叩く前提）で、
 * このk_auth自体が認可のすべてを担う。
 */
@Serializable
data class TransferRequest(
    @SerialName("k_auth") val kAuth: String,
)

/** 成功時のレスポンス（旧user_idのSupabaseセッション）。 */
@Serializable
data class TransferSessionJson(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)
