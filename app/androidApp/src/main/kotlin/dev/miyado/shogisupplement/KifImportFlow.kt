package dev.miyado.shogisupplement

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.content.ContextCompat
import dev.miyado.shogisupplement.kifu.ClipboardKifValidator
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.settings.RatingSettingsDialog
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// showKifSourceSheet のみ MainActivity.kt（MainApp）側の remember 状態をホイストして
// 受け取る（HomeScreen/ErrorScreen の onOpenKif がこのシートを開くトリガーのため、
// MainApp 側の when(state) 分岐からも書き込む必要がある）。それ以外のダイアログ/
// フロー内部状態（pickedUri・kifSenteName 等）はこの Composable の subtree に閉じているため
// ローカル remember で持つ。

/**
 * KIF 棋譜取込フロー（ファイルピッカー/クリップボード → 棋力設定 → 先後選択 → 解析開始）。
 * MainActivity.kt の MainApp から常時コンポジションに含める（表示/非表示は内部の
 * remember 状態と showKifSourceSheet パラメータで制御する）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KifImportFlow(
    vm: MainViewModel,
    showKifSourceSheet: Boolean,
    onShowKifSourceSheetChange: (Boolean) -> Unit,
    showRatingSettingsDialog: Boolean,
    onShowRatingSettingsDialogChange: (Boolean) -> Unit,
) {
    // 通知権限（Android 13+）: 起動時ではなく初回の解析開始時に文脈つきで求める。
    // 拒否されても解析は動く（進捗・完了通知が見えなくなるだけ）ので、結果に関わらず開始する
    val notifPermissionContext = LocalContext.current
    var pendingAnalysisStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingAnalysisStart?.invoke()
        pendingAnalysisStart = null
    }
    fun startAnalysisWithNotifPermission(start: () -> Unit) {
        val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                notifPermissionContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            pendingAnalysisStart = start
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    // ファイルピッカー
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
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

    /** 側が確定しているとき、ダイアログなしで解析を開始する。 */
    fun startAnalysisDirect(userSide: String?) {
        val uri = pickedUri ?: return
        pickedUri = null
        val savedSettings = vm.getSavedRatingSettings()
        startAnalysisWithNotifPermission {
            vm.startAnalysis(
                uri,
                savedSettings.service,
                savedSettings.ratingRaw,
                userSide,
                savedSettings.ratingRule,
            )
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
                    // アカウント名一致 + 省略設定ON → 確認なしで即解析
                    startAnalysisDirect(suggestion.side)
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
                            startAnalysisDirect(suggestion.side)
                        } else {
                            showUserSideDialog = true
                        }
                    }
                }
                // 設定画面からの変更は保存のみ（画面遷移しない。ホーム復帰時に loadHome で再計算される）
            },
            onDismiss = {
                onShowRatingSettingsDialogChange(false)
                if (ratingSettingsFromKifFlow) {
                    ratingSettingsFromKifFlow = false
                    pickedUri = null // KIFフローをキャンセル
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
    if (showUserSideDialog && pickedUri != null) {
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
                val uri = pickedUri!!
                pickedUri = null
                startAnalysisWithNotifPermission {
                    vm.startAnalysis(
                        uri,
                        savedSettings.service,
                        savedSettings.ratingRaw,
                        userSide,
                        savedSettings.ratingRule,
                    )
                }
            },
            onDismiss = {
                showUserSideDialog = false
                pickedUri = null
            },
        )
    }
}

/**
 * 自分の側選択ダイアログ（KIF読込フロー）。
 * 棋力設定が済んでいる場合にのみ呼ばれ、「自分の側」だけを確認する。
 */
@Composable
fun UserSideDialog(
    senteName: String?,
    goteName: String?,
    savedUserSide: String?,
    onConfirm: (userSide: String?, skipNext: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** アカウント名一致時のみ true（「次回から省略」チェックボックスを表示）。 */
    showSkipOption: Boolean = false,
) {
    // 初期値が null（未選択）の場合は解析開始を無効化する
    var userSide by remember { mutableStateOf(savedUserSide) }
    var skipNext by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.SIDE_DIALOG_TITLE) },
        text = {
            Column {
                if (senteName != null || goteName != null) {
                    Text(
                        AppStrings.playersLine(senteName, goteName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { userSide = "sente" },
                ) {
                    RadioButton(selected = userSide == "sente", onClick = { userSide = "sente" })
                    Text(AppStrings.sideSente(senteName))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { userSide = "gote" },
                ) {
                    RadioButton(selected = userSide == "gote", onClick = { userSide = "gote" })
                    Text(AppStrings.sideGote(goteName))
                }
                if (showSkipOption) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { skipNext = !skipNext },
                    ) {
                        Checkbox(checked = skipNext, onCheckedChange = { skipNext = it })
                        Text(
                            AppStrings.SKIP_SIDE_CONFIRM_CHECKBOX,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(userSide, skipNext) },
                enabled = userSide != null,
            ) {
                Text(AppStrings.START_ANALYSIS)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}
