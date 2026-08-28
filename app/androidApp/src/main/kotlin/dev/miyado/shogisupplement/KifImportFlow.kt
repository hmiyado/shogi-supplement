package dev.miyado.shogisupplement

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import dev.miyado.shogisupplement.kifu.ClipboardKifValidator
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.common.UserSideDialog
import dev.miyado.shogisupplement.ui.settings.RatingSettingsDialog
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ファイルまたはクリップボードのKIFを保存する取込フロー。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KifImportFlow(
    vm: MainViewModel,
    showKifSourceSheet: Boolean,
    onShowKifSourceSheetChange: (Boolean) -> Unit,
    onStartManualKifu: () -> Unit,
    showRatingSettingsDialog: Boolean,
    onShowRatingSettingsDialogChange: (Boolean) -> Unit,
    /** 手動棋譜の保存（または省略確定）が完了したときに呼ぶ。 */
    onManualKifuHandled: () -> Unit,
) {
    // ファイルピッカー
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingManualKif by remember { mutableStateOf<String?>(null) }
    var pendingManualFileName by remember { mutableStateOf<String?>(null) }
    // KIFパース済みの対局者名と推定サイド（ダイアログ表示用）
    var kifSenteName by remember { mutableStateOf<String?>(null) }
    var kifGoteName by remember { mutableStateOf<String?>(null) }
    var suggestedSide by remember { mutableStateOf<String?>(null) }
    // 推定がアカウント名一致によるものか（一致時のみ省略チェックボックスを表示）
    var suggestedByAccount by remember { mutableStateOf(false) }

    // ダイアログ表示状態
    // showRatingSettingsDialog: 棋力設定ダイアログ（強さカード「変更」タップ or KIFフロー初回）。
    // Settings画面（SettingsHost.kt）の「変更」タップからも開くため MainApp 側にホイストしている
    // （showKifSourceSheet と同じ理由）。
    // ratingSettingsFromKifFlow: 棋力設定ダイアログがKIFフローから開かれたとき true
    var ratingSettingsFromKifFlow by remember { mutableStateOf(false) }
    // showUserSideDialog: 自分の側選択ダイアログ（KIFフロー後半）
    var showUserSideDialog by remember { mutableStateOf(false) }

    // クリップボードKIFが不正だった場合のエラーメッセージ
    var clipboardErrorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manualRequest by vm.manualKifRequest.collectAsState()

    /** 側が確定しているとき、ダイアログなしで棋譜を保存する。 */
    fun importDirectly(userSide: String?) {
        val savedSettings = vm.getSavedRatingSettings()
        val text = pendingManualKif
        if (text != null) {
            val fileName = pendingManualFileName ?: "manual.kif"
            pendingManualKif = null
            pendingManualFileName = null
            vm.importKifText(text, fileName, savedSettings.service, savedSettings.ratingRaw, userSide, savedSettings.ratingRule)
            onManualKifuHandled()
        } else {
            val uri = pickedUri ?: return
            pickedUri = null
            vm.importKif(uri, savedSettings.service, savedSettings.ratingRaw, userSide, savedSettings.ratingRule)
        }
    }

    fun startKifTextFlow(text: String, fileName: String) {
        pendingManualKif = text
        pendingManualFileName = fileName
        scope.launch {
            val parsed = runCatching { dev.miyado.shogisupplement.kifu.KifParser().parse(text) }
                .getOrNull()
            val resolved = parsed?.let {
                dev.miyado.shogisupplement.kifu.KifuDecomposer.resolvePlayerNames(text, it.headers)
            }
            kifSenteName = resolved?.first
            kifGoteName = resolved?.second
            val suggestion = vm.suggestUserSideWithMatch(kifSenteName, kifGoteName)
            suggestedSide = suggestion.side
            suggestedByAccount = suggestion.matchedByAccount
            if (vm.shouldSkipSideConfirm(suggestion)) importDirectly(suggestion.side) else showUserSideDialog = true
        }
    }

    LaunchedEffect(manualRequest) {
        manualRequest?.let {
            vm.consumeManualKifRequest()
            startKifTextFlow(it.text, it.fileName)
        }
    }

    /** KIF URI のパース→ダイアログフロー共通ヘルパー。 */
    fun startKifFlow(uri: android.net.Uri) {
        pickedUri = uri
        scope.launch {
            val (sente, gote) = vm.parseKifPlayers(uri)
            kifSenteName = sente
            kifGoteName = gote
            // 全サービスのアカウント名のいずれかを確認
            val hasAccount = vm.hasAnyServiceAccount()
            if (!hasAccount) {
                // アカウント名未設定の場合は先に棋力設定ダイアログを出す
                ratingSettingsFromKifFlow = true
                onShowRatingSettingsDialogChange(true)
            } else {
                val suggestion = vm.suggestUserSideWithMatch(sente, gote)
                suggestedSide = suggestion.side
                suggestedByAccount = suggestion.matchedByAccount
                if (vm.shouldSkipSideConfirm(suggestion)) {
                    // アカウント名一致 + 省略設定ON → 確認なしで即保存
                    importDirectly(suggestion.side)
                } else {
                    showUserSideDialog = true
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            startKifFlow(uri)
        }
    }

    // クリップボードから KIF テキストを取得してフローへ流す
    fun handleClipboardKif() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
        when {
            text.isNullOrBlank() -> {
                clipboardErrorMessage = AppStrings.KIF_CLIPBOARD_EMPTY
            }
            !ClipboardKifValidator.isValidKif(text) -> {
                clipboardErrorMessage = AppStrings.KIF_CLIPBOARD_INVALID
            }
            else -> {
                scope.launch {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date())
                    val displayName = AppStrings.clipboardFileName(dateStr)
                    val tempFile = withContext(Dispatchers.IO) {
                        java.io.File(context.cacheDir, displayName).also { it.writeText(text) }
                    }
                    startKifFlow(android.net.Uri.fromFile(tempFile))
                }
            }
        }
    }

    // 棋力設定ダイアログ（強さカードの「変更」タップ or KIFフロー初回）
    if (showRatingSettingsDialog) {
        val settings = vm.getSavedRatingSettings()
        val serviceRanks = vm.getAllServiceRanks()
        // サービスごとのアカウント名を取得（service_account テーブル）
        val serviceAccounts = vm.getAllServiceAccounts()
        RatingSettingsDialog(
            savedService = settings.service,
            savedRatingRaw = settings.ratingRaw,
            savedRatingRule = settings.ratingRule,
            savedServiceAccounts = serviceAccounts,
            savedServiceRanks = serviceRanks,
            onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                vm.saveRatingSettings(service, ratingRaw, ratingRule, serviceAccountsNew, ranks)
                onShowRatingSettingsDialogChange(false)
                if (ratingSettingsFromKifFlow) {
                    ratingSettingsFromKifFlow = false
                    // 棋力設定が完了したので側選択ダイアログへ進む
                    scope.launch {
                        val suggestion = vm.suggestUserSideWithMatch(kifSenteName, kifGoteName)
                        suggestedSide = suggestion.side
                        suggestedByAccount = suggestion.matchedByAccount
                        if (vm.shouldSkipSideConfirm(suggestion)) {
                            importDirectly(suggestion.side)
                        } else {
                            showUserSideDialog = true
                        }
                    }
                } else if (vm.state.value is MainUiState.StrengthDetail) {
                    // 推定棋力詳細画面の「編集」から開いた場合は、保存直後にその場で再ロードして
                    // 対局サービス一覧・最高段級位を最新化する（画面遷移はしない）。
                    vm.openStrengthDetail()
                }
            },
            onDismiss = {
                onShowRatingSettingsDialogChange(false)
                if (ratingSettingsFromKifFlow) {
                    ratingSettingsFromKifFlow = false
                    pickedUri = null // KIFフローをキャンセル
                    pendingManualKif = null
                    pendingManualFileName = null
                }
            },
        )
    }

    // 棋譜追加ソース選択シート（ファイル vs クリップボード）
    if (showKifSourceSheet) {
        ModalBottomSheet(onDismissRequest = { onShowKifSourceSheetChange(false) }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    AppStrings.KIF_SOURCE_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
                Text(
                    AppStrings.KIF_SOURCE_FILE,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onShowKifSourceSheetChange(false)
                            filePicker.launch(arrayOf("*/*"))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
                Text(
                    AppStrings.KIF_SOURCE_CLIPBOARD,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onShowKifSourceSheetChange(false)
                            handleClipboardKif()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
                Text(
                    AppStrings.KIF_SOURCE_MANUAL,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onShowKifSourceSheetChange(false)
                            onStartManualKifu()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }

    // クリップボードエラーダイアログ
    if (clipboardErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { clipboardErrorMessage = null },
            title = { Text(AppStrings.KIF_SOURCE_CLIPBOARD) },
            text = { Text(clipboardErrorMessage!!) },
            confirmButton = {
                TextButton(onClick = { clipboardErrorMessage = null }) {
                    Text(AppStrings.CANCEL)
                }
            },
        )
    }

    // 自分の側選択ダイアログ（KIFフロー後半）
    if (showUserSideDialog && (pickedUri != null || pendingManualKif != null)) {
        val savedSettings = vm.getSavedRatingSettings()
        UserSideDialog(
            senteName = kifSenteName,
            goteName = kifGoteName,
            savedUserSide = suggestedSide,
            // アカウント名一致時のみ「次回から省略」チェックボックスを表示
            showSkipOption = suggestedByAccount,
            onConfirm = { userSide, skipNext ->
                showUserSideDialog = false
                if (suggestedByAccount) vm.saveSkipSideConfirm(skipNext)
                val text = pendingManualKif
                if (text != null) {
                    val fileName = pendingManualFileName ?: "manual.kif"
                    pendingManualKif = null
                    pendingManualFileName = null
                    vm.importKifText(text, fileName, savedSettings.service, savedSettings.ratingRaw, userSide, savedSettings.ratingRule)
                    onManualKifuHandled()
                } else {
                    val uri = pickedUri!!
                    pickedUri = null
                    vm.importKif(uri, savedSettings.service, savedSettings.ratingRaw, userSide, savedSettings.ratingRule)
                }
            },
            onDismiss = {
                // ここでは draft を破棄するだけにとどめる
                // （onManualKifuHandled を呼ぶと画面ごと閉じ、入力し直すしかなくなるため）。
                showUserSideDialog = false
                pickedUri = null
                pendingManualKif = null
                pendingManualFileName = null
            },
        )
    }
}
