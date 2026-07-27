package dev.miyado.shogisupplement.crypto

import dev.miyado.shogisupplement.kifu.PrivateKifuFields
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES

/**
 * uploaded_games v2 の `private_enc` 列の暗号化/復号（設計書 付録・enc_version=1）。
 *
 * ```
 * private_enc = nonce(12B) ‖ AES-256-GCM(K_enc, JSON{sente_name, gote_name, extra_headers, comments}, AAD=content_hash)
 * ```
 *
 * 実際の格納バイト列は先頭に1バイトの形式バージョンを付与する
 * （設計書「nonce・タグの格納形式は自己記述的に（将来の形式変更に耐えるようversionバイト等）」に
 * 対応する判断）: `version(1B) ‖ nonce(12B) ‖ ciphertext ‖ tag(16B)`。
 * cryptography-kotlinのAES.GCM暗号は `encrypt()` でnonce生成～ciphertext/tag結合まで
 * `[nonce|ciphertext|tag]` の形式に揃えてくれる（プラットフォーム間で形式が一致することが
 * ライブラリの保証事項）ため、そのままversionバイトを前置するだけでよい。
 */
object PrivateEncCodec {

    /** 格納形式のバージョン（先頭1バイト）。形式を変える際はここを上げて分岐する。 */
    const val VERSION: Byte = 1

    suspend fun encrypt(kEnc: ByteArray, fields: PrivateKifuFields, aad: ByteArray): ByteArray {
        val cipher = aesGcmCipher(kEnc)
        val plaintext = fields.toJson().encodeToByteArray()
        val nonceAndCiphertext = cipher.encrypt(plaintext = plaintext, associatedData = aad)
        return byteArrayOf(VERSION) + nonceAndCiphertext
    }

    suspend fun decrypt(kEnc: ByteArray, blob: ByteArray, aad: ByteArray): PrivateKifuFields {
        require(blob.isNotEmpty()) { "private_encが空です" }
        val version = blob[0]
        require(version == VERSION) { "未対応のprivate_enc形式バージョン: $version" }
        val cipher = aesGcmCipher(kEnc)
        val nonceAndCiphertext = blob.copyOfRange(1, blob.size)
        val plaintext = cipher.decrypt(ciphertext = nonceAndCiphertext, associatedData = aad)
        return PrivateKifuFields.fromJson(plaintext.decodeToString())
    }

    /**
     * 固定nonceでの暗号化。ゴールデン形式テスト専用（[VERSION] を含む実際の格納形式を
     * プラットフォーム間で固定するため、jvmTest/iosTestの両方から同一の期待値と比較する）。
     * 本番経路（[encrypt]）は毎回ランダムnonceを使うため、この関数を本番コードから呼ばないこと。
     */
    @OptIn(DelicateCryptographyApi::class)
    internal suspend fun encryptWithFixedNonceForGoldenTest(
        kEnc: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val cipher = aesGcmCipher(kEnc)
        val ciphertext = cipher.encryptWithIv(iv = nonce, plaintext = plaintext, associatedData = aad)
        return byteArrayOf(VERSION) + nonce + ciphertext
    }

    private suspend fun aesGcmCipher(kEnc: ByteArray) =
        CryptographyProvider.Default.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, kEnc)
            .cipher()
}
