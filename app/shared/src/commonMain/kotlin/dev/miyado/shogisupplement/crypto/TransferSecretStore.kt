package dev.miyado.shogisupplement.crypto

import dev.whyoleg.cryptography.random.CryptographyRandom

/** マスターシークレット S のビット長（付録「引き継ぎコードの詳細仕様」より）。 */
const val TRANSFER_SECRET_BYTES = 16

/**
 * マスターシークレット S（128bit・端末CSPRNG生成）の永続化。
 *
 * 実体はプラットフォームごとに異なる（iOS=Keychain、Android=Keystore鍵で包んだ
 * SharedPreferences、JVM=開発/テスト用の暗号化ファイル。各実装クラス参照）。
 */
interface TransferSecretStore {
    /** 保存済みの S を返す。未生成なら null。 */
    suspend fun load(): ByteArray?

    /** S を永続化する。 */
    suspend fun save(secret: ByteArray)

    /**
     * S を消去する（アカウント削除＝同意の撤回に伴う再スタート用）。
     * 消去後は [TransferSecretManager.getOrCreateSecret] が新しい S を生成する。
     */
    suspend fun clear()
}

/**
 * S の取得・遅延生成をまとめたヘルパー。
 *
 * Why not オンボーディングUIでのみSを生成する: 引き継ぎコード表示・S生成のUIはこのタスクの
 * 範囲外（別タスク）だが、v2アップロード経路はprivate_enc暗号化にK_encを必要とし、
 * オンボーディング前でもアップロードが起こりうる（既存の自動アップロード経路は解析完了時に
 * 即座に呼ばれるため）。初回利用時に遅延生成することで、オンボーディングUIが実装されるまでの間も
 * アップロードが失敗し続けることを避ける。UI実装後は同じ関数をオンボーディング画面からも呼べば、
 * 生成タイミングの二重管理にはならない。
 */
object TransferSecretManager {
    suspend fun getOrCreateSecret(store: TransferSecretStore): ByteArray {
        store.load()?.let { return it }
        val generated = CryptographyRandom.Default.nextBytes(TRANSFER_SECRET_BYTES)
        store.save(generated)
        return generated
    }
}
