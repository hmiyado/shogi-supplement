package dev.miyado.shogisupplement.consent

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.crypto.TransferSecretRegistrar
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.util.currentEpochSeconds

/**
 * オンボーディングでアカウントを作る側を選んだときの確定処理。
 *
 * Why not サインイン失敗を致命にする: 解析実行時に自前で再試行する経路があり、
 * ここで止めるとオンボーディングがリトライループになる。
 */
class ConsentOrchestrator(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val transferSecretRegistrar: TransferSecretRegistrar,
) {
    /** アカウントを作らずに始める。サーバーへは何も送らないため、登録も同意フラグも持たない。 */
    fun declineAccount() {
        settingsRepository.saveAccountDeclined(true)
        settingsRepository.saveAutoUpload(false)
    }

    suspend fun acceptConsent() {
        settingsRepository.saveAccountDeclined(false)
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
