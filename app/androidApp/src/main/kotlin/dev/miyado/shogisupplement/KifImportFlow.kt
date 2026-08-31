package dev.miyado.shogisupplement

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.rememberCoroutineScope
import dev.miyado.shogisupplement.kifu.KifImportController
import dev.miyado.shogisupplement.kifu.KifOrigin
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.common.UserSideDialog
import dev.miyado.shogisupplement.ui.settings.RatingSettingsDialog
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ファイル・クリップボード・手入力のKIFを保存する取込フロー。手順は[MainViewModel.kifImport]が持つ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KifImportFlow(
    vm: MainViewModel,
    showKifSourceSheet: Boolean,
    onShowKifSourceSheetChange: (Boolean) -> Unit,
    onStartManualKifu: () -> Unit,
    showRatingSettingsDialog: Boolean,
    onShowRatingSettingsDialogChange: (Boolean) -> Unit,
    /** 手動棋譜の保存が完了したときに呼ぶ。 */
    onManualKifuHandled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val step by vm.kifImport.step.collectAsState()
    val manualRequest by vm.manualKifRequest.collectAsState()

    LaunchedEffect(manualRequest) {
        val request = manualRequest ?: return@LaunchedEffect
        vm.consumeManualKifRequest()
        // Why not このLaunchedEffectの中で続ける: 消費でキー(manualRequest)がnullに変わるため、
        // 中断点で取り消されて取込が始まらないことがある。
        scope.launch {
            withContext(Dispatchers.IO) { vm.kifImport.beginManual(request.text, request.fileName) }
            val awaitingInput = vm.kifImport.step.value.let {
                it is KifImportController.Step.SideConfirm || it is KifImportController.Step.Failed
            }
            // 確認を挟まず保存へ入ったなら（アカウント名一致＋省略設定ON）入力画面を閉じる。
            if (!awaitingInput) onManualKifuHandled()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val (fileName, text) = vm.readKifFromUri(uri)
                withContext(Dispatchers.IO) { vm.kifImport.beginFromFile(fileName, text) }
            }
        }
    }

    // KIFのパースと設定の読み出しが入るため、取込の開始はIOで行う。
    fun handleClipboardKif() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        scope.launch { withContext(Dispatchers.IO) { vm.kifImport.beginFromClipboard(text) } }
    }

    // 棋力設定ダイアログ（強さカードの「変更」タップ or 設定画面から）
    if (showRatingSettingsDialog) {
        val settings = vm.getSavedRatingSettings()
        RatingSettingsDialog(
            savedService = settings.service,
            savedRatingRaw = settings.ratingRaw,
            savedRatingRule = settings.ratingRule,
            savedServiceAccounts = vm.getAllServiceAccounts(),
            savedServiceRanks = vm.getAllServiceRanks(),
            onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                vm.saveRatingSettings(service, ratingRaw, ratingRule, serviceAccountsNew, ranks)
                onShowRatingSettingsDialogChange(false)
                if (vm.state.value is MainUiState.StrengthDetail) {
                    // 推定棋力詳細画面の「編集」から開いた場合は、保存直後にその場で再ロードして
                    // 対局サービス一覧・最高段級位を最新化する（画面遷移はしない）。
                    vm.openStrengthDetail()
                }
            },
            onDismiss = { onShowRatingSettingsDialogChange(false) },
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
                KifSourceRow(AppStrings.KIF_SOURCE_FILE) {
                    onShowKifSourceSheetChange(false)
                    filePicker.launch(arrayOf("*/*"))
                }
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
                KifSourceRow(AppStrings.KIF_SOURCE_CLIPBOARD) {
                    onShowKifSourceSheetChange(false)
                    handleClipboardKif()
                }
                HorizontalDivider(color = MaterialTheme.shogiColors.line)
                KifSourceRow(AppStrings.KIF_SOURCE_MANUAL) {
                    onShowKifSourceSheetChange(false)
                    onStartManualKifu()
                }
            }
        }
    }

    when (val current = step) {
        is KifImportController.Step.RatingSetup -> {
            // アカウント名未設定の初回取込: 先に棋力設定。キャンセルは取込フローごと中止する。
            val settings = vm.getSavedRatingSettings()
            RatingSettingsDialog(
                savedService = settings.service,
                savedRatingRaw = settings.ratingRaw,
                savedRatingRule = settings.ratingRule,
                savedServiceAccounts = vm.getAllServiceAccounts(),
                savedServiceRanks = vm.getAllServiceRanks(),
                onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                    vm.kifImport.completeRatingSetup(service, ratingRaw, ratingRule, serviceAccountsNew, ranks)
                },
                onDismiss = { vm.kifImport.dismiss() },
            )
        }
        is KifImportController.Step.SideConfirm -> {
            UserSideDialog(
                senteName = current.kif.senteName,
                goteName = current.kif.goteName,
                savedUserSide = current.suggestion.side,
                // アカウント名一致時のみ「次回から省略」チェックボックスを表示
                showSkipOption = current.suggestion.matchedByAccount,
                onConfirm = { userSide, skipNext ->
                    val fromManualKifu = current.kif.origin == KifOrigin.MANUAL
                    vm.kifImport.confirmSide(userSide, skipNext)
                    if (fromManualKifu) onManualKifuHandled()
                },
                // 取消では手入力の下書き画面を閉じない（閉じると入力し直すしかなくなる）。
                onDismiss = { vm.kifImport.dismiss() },
            )
        }
        is KifImportController.Step.Failed -> {
            AlertDialog(
                onDismissRequest = { vm.kifImport.dismiss() },
                title = {
                    Text(
                        if (current.origin == KifOrigin.FILE) AppStrings.KIF_SOURCE_FILE
                        else AppStrings.KIF_SOURCE_CLIPBOARD,
                    )
                },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(onClick = { vm.kifImport.dismiss() }) {
                        Text(AppStrings.CANCEL)
                    }
                },
            )
        }
        else -> Unit
    }
}

@Composable
private fun KifSourceRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
