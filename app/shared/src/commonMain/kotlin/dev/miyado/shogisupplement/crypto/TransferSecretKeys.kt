package dev.miyado.shogisupplement.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * マスターシークレットからK_authとK_encをHKDF-SHA256で導出する。
 * Why not salt指定: RFC 5869の既定と仕様を一致させるため、salt=nullとする。
 */
object TransferSecretKeys {

    private const val AUTH_INFO = "shogisup/auth/v1"
    private const val ENC_INFO = "shogisup/enc/v1"
    private val DERIVED_KEY_SIZE = 256.bits

    suspend fun deriveAuthKey(secret: ByteArray): ByteArray = derive(secret, AUTH_INFO)

    suspend fun deriveEncKey(secret: ByteArray): ByteArray = derive(secret, ENC_INFO)

    /**
     * サーバーに送る唯一の派生値（K_auth自体は送らない・ハッシュのみ）。
     * 登録は [dev.miyado.shogisupplement.upload.SupabaseTransferSecretRegistrar] が担う。
     * 照合（引き継ぎコード入力による復元フロー）はS4で別途実装する。
     */
    suspend fun authKeyHash(authKey: ByteArray): ByteArray =
        CryptographyProvider.Default.get(SHA256).hasher().hash(authKey)

    private suspend fun derive(secret: ByteArray, info: String): ByteArray {
        val hkdf = CryptographyProvider.Default.get(HKDF)
        val derivation = hkdf.secretDerivation(
            digest = SHA256,
            outputSize = DERIVED_KEY_SIZE,
            salt = null,
            info = info.encodeToByteArray(),
        )
        return derivation.deriveSecretToByteArray(secret)
    }
}
