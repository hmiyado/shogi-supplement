package dev.miyado.shogisupplement.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** SwiftのUIDocumentPickerとKotlinのKIF取込を、関数とクロージャで橋渡しする。選択結果をFlowで公開する。 */
object IosFileImportBridge {

    /** ファイルピッカーの結果。[text] が null はデコード失敗（UTF-8/Shift_JISとも不可等）。 */
    data class PickResult(val fileName: String, val text: String?)

    /**
     * Swift側（KifFilePickerCoordinator の init）が起動時に代入する、
     * 「UIDocumentPickerViewController を提示する」実処理へのクロージャ。
     * Compose側から直接 UIKit を叩かず、常にこのハンドラ経由でSwiftへ委譲する。
     */
    var presentPickerHandler: (() -> Unit)? = null

    private val _result = MutableSharedFlow<PickResult>(extraBufferCapacity = 1)

    /** Compose側（DemoApp の LaunchedEffect）がcollectする。 */
    val result: SharedFlow<PickResult> = _result.asSharedFlow()

    /** Compose側:「ファイルから」タップ時にピッカー提示をSwiftへ要求する。 */
    fun requestOpenFilePicker() {
        presentPickerHandler?.invoke()
    }

    /** Swift側: ユーザーがファイルを選び、テキスト取得（デコード）に成功した場合。 */
    fun onFilePicked(fileName: String, text: String) {
        _result.tryEmit(PickResult(fileName, text))
    }

    /** Swift側: 読み込み/デコードに失敗した場合（UTF-8/Shift_JISいずれも不可・読み取り不可等）。 */
    fun onFilePickFailed(fileName: String) {
        _result.tryEmit(PickResult(fileName, null))
    }

    // ユーザーがピッカーをキャンセルした場合は何も通知しない
    // （Swift側 documentPickerWasCancelled は no-op。ダイアログは既にComposeが閉じている）。
}
