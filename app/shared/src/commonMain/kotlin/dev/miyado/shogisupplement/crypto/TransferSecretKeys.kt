package dev.miyado.shogisupplement.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * マスターシークレット S から K_auth / K_enc を導出する（付録「引き継ぎコードの詳細仕様」）。
 *
 * ```
 * K_auth = HKDF-SHA256(S, info="shogisup/auth/v1", 32B)  // サーバーはこのハッシュのみ保存
 * K_enc  = HKDF-SHA256(S, info="shogisup/enc/v1", 32B)   // サーバーへ送らない
 * ```
 *
 * Why not salt指定: 設計書はHKDFのsalt引数に触れていない。RFC 5869はsalt省略時に
 * ハッシュ長ぶんのゼロ埋めを既定とするため、それと同じ意味になる `salt = null` を渡す
 * （cryptography-kotlinのHKDF実装がRFC既定に従う前提。salt自体を秘密にする設計ではなく、
 * 入力Sそのものが128bitの高エントロピー値のため、salt省略の安全性は損なわれない）。
 */
object TransferSecretKeys {

    private const val AUTH_INFO = "shogisup/auth/v1"
    private const val ENC_INFO = "shogisup/enc/v1"
    private val DERIVED_KEY_SIZE = 256.bits

    suspend fun deriveAuthKey(secret: ByteArray): ByteArray = derive(secret, AUTH_INFO)

    suspend fun deriveEncKey(secret: ByteArray): ByteArray = derive(secret, ENC_INFO)

    /**
     * サーバーに送る唯一の派生値（K_auth自体は送らない・ハッシュのみ）。
     * 引き継ぎコード復元フロー（S4）で使う想定で、このタスクでは呼び出し元を持たない
     * （登録・照合APIの実装は別タスク）。
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
