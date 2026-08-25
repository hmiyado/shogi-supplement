package dev.miyado.shogisupplement.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEYSTORE_ALIAS = "shogisup_transfer_secret_kek"
private const val PREFS_NAME = "shogisup_transfer_secret"
private const val PREF_KEY_CIPHERTEXT = "s_enc"
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128

/**
 * Sの永続化＝Android Keystore生成のAES鍵で包んだ暗号文を通常のSharedPreferencesへ保存する
 * （EncryptedSharedPreferences相当。androidx.security-crypto依存を増やさず最小構成にする判断
 * ＝タスク指示「androidx.security不使用ならKeystore+暗号化ファイル」に対応）。
 *
 * Keystore鍵自体はハードウェア/TEE側に留まりエクスポート不可なため、SharedPreferences側の
 * ファイルには暗号文のみが載る（鍵が漏れない限り復号できない）。
 */
class AndroidTransferSecretStore(private val context: Context) : TransferSecretStore {

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun load(): ByteArray? {
        val encoded = prefs.getString(PREF_KEY_CIPHERTEXT, null) ?: return null
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        if (blob.size <= GCM_IV_BYTES) return null
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    override suspend fun save(secret: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val ciphertext = cipher.doFinal(secret)
        val blob = cipher.iv + ciphertext
        prefs.edit().putString(PREF_KEY_CIPHERTEXT, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    override suspend fun clear() {
        prefs.edit().remove(PREF_KEY_CIPHERTEXT).apply()
        runCatching {
            keyStore().deleteEntry(KEYSTORE_ALIAS)
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keystoreKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
