package dev.miyado.shogisupplement.consent

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.crypto.TransferSecretRegistrar
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.util.currentEpochSeconds

/**
 * 同意オンボーディング（iOS専用・初回起動必須）の完了処理。
 *
 * 設計書「認証・同意モデルの変更（iOS＝同意必須）」節が仕様の正。順序は次のとおり:
 * 1. 同意フラグを user_settings へ保存（`consent_accepted_at`）
 * 2. 匿名サインイン（未ログインの場合のみ）
 * 3. 自動アップロード設定をON（iOS版は同意済み=全員ONという設計のため常時ONにする）
 * 4. 引き継ぎシークレットSの生成（未生成なら）＋K_authハッシュの登録
 *
 * Why not 匿名サインイン失敗を致命的に扱う: 取込フロー（IosMainController.confirmSideAndAnalyze）
 * が未ログイン時に自前でサインインを再試行するため、オンボーディング時点の失敗は
 * 致命ではない（次の解析実行時に自然に回復する）。オンボーディング画面をリトライループに
 * せず一度で先へ進めることを優先する（[TransferSecretRegistrar.registerIfNeeded] と同じ
 * 「登録/サインイン失敗は致命にしない」という設計方針）。
 */
class ConsentOrchestrator(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val transferSecretRegistrar: TransferSecretRegistrar,
) {
    /**
     * 同意を確定する（オンボーディング画面の「同意して始める」タップで呼ぶ）。
     * 例外を投げない（内部の各ステップはそれぞれ失敗を吸収する。同意フラグの保存自体が
     * 失敗した場合のみ SettingsRepository 側の例外がそのまま伝播する）。
     */
    suspend fun acceptConsent() {
        settingsRepository.saveConsentAcceptedAt(currentEpochSeconds())

        if (authRepository.currentUser.value == null) {
            authRepository.signInAnonymously()
        }

        settingsRepository.saveAutoUpload(true)

        authRepository.currentUser.value?.let { user ->
            transferSecretRegistrar.registerIfNeeded(user.id)
        }
    }
}
