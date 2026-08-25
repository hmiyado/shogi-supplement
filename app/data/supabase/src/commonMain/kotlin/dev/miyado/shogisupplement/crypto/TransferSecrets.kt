package dev.miyado.shogisupplement.crypto

import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * 引き継ぎコードが運ぶ2つのシークレット。
 *
 * Why not 1つのシークレットから両方を導出し続ける: 導出元を変えると復号鍵も変わり、
 * アップロード済みの棋譜の秘匿項目を読めなくなる。認証用だけを回せる形にする。
 *
 * 再生成前に発行されたコードは1つのシークレットしか持たない。この場合は両方に同じ値を
 * 使う（＝これまでと同じ導出）ため、既存のコードは何も変えずに通る。
 */
class TransferSecrets(val encSecret: ByteArray, val authSecret: ByteArray) {

    /** 認証用だけを引き直す。復号鍵の導出元は変えない。 */
    fun rotateAuth(): TransferSecrets =
        TransferSecrets(encSecret, CryptographyRandom.Default.nextBytes(TRANSFER_SECRET_BYTES))

    /** 永続化する表現。長さで版が判るため、版番号のフィールドを持たない。 */
    fun toStored(): ByteArray =
        if (encSecret.contentEquals(authSecret)) encSecret else encSecret + authSecret

    companion object {
        /** 保存値の解釈。長さが想定外ならnull（壊れた保存値を鍵として使わない）。 */
        fun fromStored(stored: ByteArray): TransferSecrets? = when (stored.size) {
            TRANSFER_SECRET_BYTES -> TransferSecrets(stored, stored)
            TRANSFER_SECRET_BYTES * 2 -> TransferSecrets(
                encSecret = stored.copyOfRange(0, TRANSFER_SECRET_BYTES),
                authSecret = stored.copyOfRange(TRANSFER_SECRET_BYTES, stored.size),
            )
            else -> null
        }
    }
}
