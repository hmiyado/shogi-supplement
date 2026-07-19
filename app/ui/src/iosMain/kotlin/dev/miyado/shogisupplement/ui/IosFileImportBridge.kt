package dev.miyado.shogisupplement.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 「ファイルから」KIF取込のSwift⇄Kotlin橋渡し。
 *
 * UIDocumentPickerViewController の提示・デリゲート処理は Swift 側
 * （iosApp/iosApp/KifFilePickerCoordinator.swift）が担う。Compose側（[IosMainController]・
 * [MainViewController]）は本オブジェクトを介して「ピッカーを開いてほしい」を伝え、
 * 選択結果（テキスト or 失敗）を受け取る。
 *
 * Kotlin/Native ⇄ Swift の境界は「プレーンな関数呼び出し」と「クロージャ型プロパティへの代入」
 * のみで構成している（Kotlin Flow を直接 Swift へ橋渡しする層は無い。SKIE 等の追加変換は
 * 未導入のため、Flow の collect は Compose 側=Kotlinのみで完結させる）。
 *
 * 起動時の配線: [KifFilePickerCoordinator] の init で [presentPickerHandler] に
 * 「ピッカーを提示するSwiftクロージャ」を代入する。Compose 側は [MainViewController.kt] の
 * `DemoApp` が [result] を `LaunchedEffect` で collect し、[IosMainController.handleFileImport]
 * へ渡す。
 */
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
