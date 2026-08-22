package dev.miyado.shogisupplement.ui.transfercode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.transfer.TransferRestoreResult
import dev.miyado.shogisupplement.transfer.TransferRestoreService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 引き継ぎコード入力ダイアログの状態。 */
sealed class TransferCodeInputUiState {
    data object Idle : TransferCodeInputUiState()

    /** 送信前に確認ダイアログを挟む必要がある（既にこの端末にログイン中のアカウントがある）。 */
    data class NeedsConfirmation(val code: String) : TransferCodeInputUiState()

    data object Restoring : TransferCodeInputUiState()
    data object Success : TransferCodeInputUiState()
    data class Error(val message: String) : TransferCodeInputUiState()
}

/** 引き継ぎコード復元の状態を管理する。ログイン中は確認を経てから復元し、未ログイン時は直行する。 */
class TransferCodeInputViewModel(
    private val authRepository: AuthRepository,
    private val transferRestoreService: TransferRestoreService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransferCodeInputUiState>(TransferCodeInputUiState.Idle)
    val uiState: StateFlow<TransferCodeInputUiState> = _uiState

    fun submit(code: String) {
        if (authRepository.currentUser.value != null) {
            _uiState.value = TransferCodeInputUiState.NeedsConfirmation(code)
        } else {
            restore(code)
        }
    }

    fun confirmRestore() {
        val pending = _uiState.value as? TransferCodeInputUiState.NeedsConfirmation ?: return
        restore(pending.code)
    }

    fun cancelConfirmation() {
        _uiState.value = TransferCodeInputUiState.Idle
    }

    fun dismissError() {
        _uiState.value = TransferCodeInputUiState.Idle
    }

    private fun restore(code: String) {
        viewModelScope.launch {
            _uiState.value = TransferCodeInputUiState.Restoring
            _uiState.value = when (val result = transferRestoreService.restore(code)) {
                TransferRestoreResult.Success -> TransferCodeInputUiState.Success
                TransferRestoreResult.InvalidCode ->
                    TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_INVALID)
                TransferRestoreResult.NotFound ->
                    TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_NOT_FOUND)
                TransferRestoreResult.RateLimited ->
                    TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_RATE_LIMITED)
                TransferRestoreResult.UpgradeRequired ->
                    TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_UPGRADE_REQUIRED)
                is TransferRestoreResult.SessionImportFailed ->
                    TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_GENERIC)
                is TransferRestoreResult.NetworkError ->
                    TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_GENERIC)
            }
        }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            transferRestoreService: TransferRestoreService,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransferCodeInputViewModel(authRepository, transferRestoreService) }
        }
    }
}
