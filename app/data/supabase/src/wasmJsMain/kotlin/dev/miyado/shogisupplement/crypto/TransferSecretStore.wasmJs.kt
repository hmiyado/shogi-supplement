package dev.miyado.shogisupplement.crypto

import kotlinx.browser.localStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Android=Keystore・iOS=Keychainのような機密性は提供しない
 * （ブラウザのlocalStorageは同一オリジンの他スクリプトから読める。XSS対策は別レイヤーの責務）。
 * JVM実装（平文ファイル保存）と同程度の妥協として、localStorageへBase64で保存する。
 */
@OptIn(ExperimentalEncodingApi::class)
class WasmJsTransferSecretStore : TransferSecretStore {

    override suspend fun load(): ByteArray? =
        localStorage.getItem(KEY)?.let { Base64.decode(it) }

    override suspend fun save(secret: ByteArray) {
        localStorage.setItem(KEY, Base64.encode(secret))
    }

    override suspend fun clear() {
        localStorage.removeItem(KEY)
    }

    companion object {
        private const val KEY = "shogisupplement_transfer_secret"
    }
}
