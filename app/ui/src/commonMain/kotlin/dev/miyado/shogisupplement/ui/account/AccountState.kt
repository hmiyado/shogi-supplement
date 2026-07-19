package dev.miyado.shogisupplement.ui.account

/**
 * アカウント画面の UI 状態（匿名認証 v1: 棋譜提供の有効/無効）。
 *
 * lifecycle 依存のないプレーンな Kotlin 型。
 */
sealed class AccountUiState {
    /** 状態取得中（起動直後など）。 */
    object Loading : AccountUiState()

    /**
     * 未提供（匿名サインインしていない状態）。
     * @param error 直前のエラーメッセージ（null = エラーなし）
     */
    data class NotLoggedIn(val error: String? = null) : AccountUiState()

    /**
     * 提供中（匿名サインイン済み）。
     * uid はここでは持たない（UI に表示しない仕様）。
     * @param uploadedCount アップロード済みゲームの件数
     * @param pendingCount 未アップロードゲームの件数
     * @param autoUpload 解析後自動アップロードが ON かどうか
     * @param error 直前のエラーメッセージ（データ削除失敗など。null = エラーなし）
     * @param isUploading 手動アップロード実行中
     * @param uploadResultMessage 直前のアップロード結果（null = まだ未実施）
     */
    data class LoggedIn(
        val uploadedCount: Int = 0,
        val pendingCount: Int = 0,
        val autoUpload: Boolean = false,
        val error: String? = null,
        val isUploading: Boolean = false,
        val uploadResultMessage: String? = null,
    ) : AccountUiState()
}
