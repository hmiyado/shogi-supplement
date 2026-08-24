package dev.miyado.shogisupplement.ui.common

import dev.miyado.shogisupplement.db.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画面をまたいで効く表示設定を保持し、保存と同時に反映する。
 *
 * @param scope 読み込みと保存を回すスコープ。ホストの生存期間に合わせる。
 */
class AppSettingsController(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    /** 形勢の表示単位（"cp" or "wp"）。 */
    private val _evalDisplay = MutableStateFlow("cp")
    val evalDisplay: StateFlow<String> = _evalDisplay.asStateFlow()

    private val _skipSideConfirm = MutableStateFlow(false)
    val skipSideConfirm: StateFlow<Boolean> = _skipSideConfirm.asStateFlow()

    init {
        scope.launch {
            withContext(ioDispatcher) {
                Triple(
                    settingsRepository.getThemeMode(),
                    settingsRepository.getEvalDisplay(),
                    settingsRepository.getSkipSideConfirm(),
                )
            }.let { (theme, eval, skip) ->
                _themeMode.value = theme
                _evalDisplay.value = eval
                _skipSideConfirm.value = skip
            }
        }
    }

    fun saveThemeMode(mode: String) = save(mode, _themeMode, settingsRepository::saveThemeMode)

    fun saveEvalDisplay(mode: String) = save(mode, _evalDisplay, settingsRepository::saveEvalDisplay)

    fun saveSkipSideConfirm(skip: Boolean) =
        save(skip, _skipSideConfirm, settingsRepository::saveSkipSideConfirm)

    private fun <T> save(value: T, state: MutableStateFlow<T>, persist: (T) -> Unit) {
        scope.launch {
            withContext(ioDispatcher) { persist(value) }
            state.value = value
        }
    }
}
