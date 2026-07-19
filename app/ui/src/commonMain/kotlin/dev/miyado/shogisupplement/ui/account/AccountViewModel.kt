package dev.miyado.shogisupplement.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.miyado.shogisupplement.auth.AuthErrorMapper
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.upload.UploadOrchestrator
import dev.miyado.shogisupplement.upload.UploadResult
import dev.miyado.shogisupplement.util.Logger
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// androidx.lifecycle.ViewModel / ViewModelProvider / viewModelScope / viewmodel.initializer /
// viewmodel.viewModelFactory は org.jetbrains.androidx.lifecycle:lifecycle-viewmodel（KMP版）が
// 同一パッケージ名（androidx.lifecycle.*）で提供する。

/**
 * アカウント画面の ViewModel（匿名認証 v1）。
 * constructor injection でテスト可能（fake を注入できる）。
 * @param ioDispatcher DB操作用ディスパッチャ（テスト時はUnconfinedを注入）
 */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val uploadOrchestrator: UploadOrchestrator? = null,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val uiState: StateFlow<AccountUiState> = _uiState

    init {
        // 初期状態: 現在の認証状態から決定
        val initial = authRepository.currentUser.value
        _uiState.value = if (initial != null) {
            AccountUiState.LoggedIn()
        } else {
            AccountUiState.NotLoggedIn()
        }

        // ログイン中なら auto_upload と件数を非同期で読み込む
        if (initial != null) {
            viewModelScope.launch {
                val autoUpload = withContext(ioDispatcher) { settingsRepository.getAutoUpload() }
                val uploadedCount = withContext(ioDispatcher) { gameRepository.getUploadedGameCount() }
                val pendingCount = withContext(ioDispatcher) { gameRepository.getNotUploadedGames().size }
                (_uiState.value as? AccountUiState.LoggedIn)?.let {
                    _uiState.value = it.copy(
                        autoUpload = autoUpload,
                        uploadedCount = uploadedCount,
                        pendingCount = pendingCount,
                    )
                }
            }
        }

        // 認証状態の変化を監視（signInAnonymously/signOut/deleteAccount 後に自動反映）
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                // Loading 中は上書きしない（signInAnonymously 処理中）
                if (_uiState.value !is AccountUiState.Loading) {
                    if (user != null) {
                        val autoUpload = withContext(ioDispatcher) { settingsRepository.getAutoUpload() }
                        val uploadedCount = withContext(ioDispatcher) { gameRepository.getUploadedGameCount() }
                        val pendingCount = withContext(ioDispatcher) { gameRepository.getNotUploadedGames().size }
                        _uiState.value = AccountUiState.LoggedIn(
                            uploadedCount = uploadedCount,
                            pendingCount = pendingCount,
                            autoUpload = autoUpload,
                        )
                    } else {
                        _uiState.value = AccountUiState.NotLoggedIn()
                    }
                }
            }
        }
    }

    /**
     * 匿名ユーザーとしてサインインしてデータ提供を有効にする。
     * 成功時は LoggedIn 状態に遷移する。
     */
    fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.value = AccountUiState.Loading
            val result = authRepository.signInAnonymously()
            result.fold(
                onSuccess = {
                    // currentUser flow が LoggedIn に自動更新するが、
                    // Loading 中の上書きガードがあるため明示的に更新する
                    val autoUpload = withContext(ioDispatcher) { settingsRepository.getAutoUpload() }
                    val uploadedCount = withContext(ioDispatcher) { gameRepository.getUploadedGameCount() }
                    val pendingCount = withContext(ioDispatcher) { gameRepository.getNotUploadedGames().size }
                    _uiState.value = AccountUiState.LoggedIn(
                        uploadedCount = uploadedCount,
                        pendingCount = pendingCount,
                        autoUpload = autoUpload,
                    )
                },
                onFailure = { e ->
                    Logger.e("AccountViewModel", "signInAnonymously error", e)
                    _uiState.value = AccountUiState.NotLoggedIn(
                        error = AuthErrorMapper.mapSignInAnonymouslyError(e),
                    )
                },
            )
        }
    }

    /** ログアウトする（提供をやめるがデータは残す場合には使わない。通常は deleteAccount を使う）。 */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AccountUiState.NotLoggedIn()
        }
    }

    /**
     * 提供をやめてサーバー上のデータを削除する（確認ダイアログで承認済みの前提で呼ぶ）。
     * 成功時: サーバー側データは cascade で消えているため、端末DBの uploaded_at を
     * 全リセットして再アップロード可能な状態に戻し、未提供状態にする。
     * 失敗時: 提供中状態を維持し、エラーメッセージを表示する。
     */
    fun deleteAccount() {
        val prev = _uiState.value as? AccountUiState.LoggedIn ?: return
        viewModelScope.launch {
            _uiState.value = AccountUiState.Loading
            val result = authRepository.deleteAccount()
            result.fold(
                onSuccess = {
                    // サーバー側の棋譜が消えたため、端末側のアップロード記録をリセット
                    withContext(ioDispatcher) {
                        runCatching { gameRepository.resetAllUploadedAt() }
                    }
                    _uiState.value = AccountUiState.NotLoggedIn()
                },
                onFailure = { e ->
                    Logger.e("AccountViewModel", "deleteAccount error", e)
                    _uiState.value = prev.copy(
                        error = AuthErrorMapper.mapDeleteAccountError(e),
                    )
                },
            )
        }
    }

    /**
     * 未アップロードゲームを手動でまとめてアップロードする。
     * uploadOrchestrator が注入されていない場合は何もしない。
     */
    fun manualUpload() {
        val s = _uiState.value as? AccountUiState.LoggedIn ?: return
        val orchestrator = uploadOrchestrator ?: return
        viewModelScope.launch {
            _uiState.value = s.copy(isUploading = true, uploadResultMessage = null)
            val results = withContext(ioDispatcher) { orchestrator.uploadAll() }
            val success = results.values.count { it is UploadResult.Success || it is UploadResult.Duplicate }
            val failed = results.values.count { it is UploadResult.Failure }
            val uploadedCount = withContext(ioDispatcher) { gameRepository.getUploadedGameCount() }
            val pendingCount = withContext(ioDispatcher) { gameRepository.getNotUploadedGames().size }
            val current = _uiState.value as? AccountUiState.LoggedIn ?: return@launch
            _uiState.value = current.copy(
                isUploading = false,
                uploadedCount = uploadedCount,
                pendingCount = pendingCount,
                uploadResultMessage = dev.miyado.shogisupplement.text.AppStrings
                    .accountUploadResult(success, failed),
            )
        }
    }

    /**
     * 解析後自動アップロード設定を切り替える。
     * 提供中のみ有効（状態が LoggedIn のときに呼ぶ）。
     */
    fun setAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                settingsRepository.saveAutoUpload(enabled)
            }
            val s = _uiState.value
            if (s is AccountUiState.LoggedIn) {
                _uiState.value = s.copy(autoUpload = enabled)
            }
        }
    }

    companion object {
        /** ViewModelProvider.Factory を作成する（コンポーザブルからの注入に使用）。 */
        fun factory(
            authRepository: AuthRepository,
            gameRepository: GameRepository,
            settingsRepository: SettingsRepository,
            uploadOrchestrator: UploadOrchestrator? = null,
            ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AccountViewModel(authRepository, gameRepository, settingsRepository, uploadOrchestrator, ioDispatcher)
            }
        }
    }
}
