package dev.miyado.shogisupplement.policy

import kotlinx.serialization.Serializable

/**
 * app_policyの1行。platformはandroid、ios、common、または各dev行を取る。
 * common行はmessageのみを持ち、それ以外はnullとする。
 * Serializableは端末キャッシュ用JSONのために付ける。
 */
@Serializable
data class AppPolicyRow(
    val platform: String,
    val minBuild: Int?,
    val storeUrl: String?,
    val message: String?,
)
