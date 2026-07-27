package dev.miyado.shogisupplement.crypto

import java.io.File

private fun defaultJvmStorageDir(): File =
    File(System.getProperty("java.io.tmpdir"), "shogisupplement-transfer-secret")

/**
 * JVM実装は開発・テスト用途限定。`:server:worker`（Cloud Runワーカー）は
 * ユーザーのS/K_encを扱わない設計（JWT検証・クォータ判定のみ）のため、
 * この実装は本番のワーカーコードパスからは呼ばれない。
 *
 * Android=Keystore・iOS=Keychainのような機密性は提供しない
 * （ベアJVMにはOSのセキュアストレージ相当が無い）。平文ファイル保存に留め、
 * `:shared:jvmTest` からS本体・鍵導出・暗号化の往復を検証できることだけを目的にする。
 */
class JvmTransferSecretStore(private val storageDir: File = defaultJvmStorageDir()) : TransferSecretStore {

    private val file: File
        get() = File(storageDir, "secret.bin")

    override suspend fun load(): ByteArray? =
        if (file.exists()) file.readBytes() else null

    override suspend fun save(secret: ByteArray) {
        storageDir.mkdirs()
        file.writeBytes(secret)
    }

    override suspend fun clear() {
        file.delete()
    }
}
