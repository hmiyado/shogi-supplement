package dev.miyado.shogisupplement.ui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.core.app.NotificationCompat
import dev.miyado.shogisupplement.MainActivity
import dev.miyado.shogisupplement.ShogiApp
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * デバッグ用固定局面を確認する画面（BuildConfig.DEBUG のみ表示）。
 * DebugPositions に登録された3局面を ShogiBoardView で描画し、
 * flip トグルで後手視点に切り替えできる。
 */
object DebugPositions {
    /** 全成り駒（先手・後手の両方の成り駒が盤上に並ぶ）。 */
    const val ALL_PROMOTED = "+P+L+N+S+B+R3/+p+l+n+s+b+r3/9/9/9/9/9/3K5/8k b - 1"

    /** 先手持駒最大: 盤上は玉2枚のみ、先手手駒 2R2B4G4S4N4L18P。 */
    const val BLACK_HAND_MAX = "9/9/9/9/9/9/9/4K4/5k3 b 2R2B4G4S4N4L18P 1"

    /** 後手持駒最大: 盤上は玉2枚のみ、後手手駒 2r2b4g4s4n4l18p。 */
    const val WHITE_HAND_MAX = "9/9/9/9/9/9/9/4K4/5k3 w 2r2b4g4s4n4l18p 1"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit) {
    var currentSfen by remember { mutableStateOf(DebugPositions.ALL_PROMOTED) }
    var currentLabel by remember { mutableStateOf("全成り駒") }
    var flip by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("デバッグ: 局面確認") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        currentSfen = DebugPositions.ALL_PROMOTED
                        currentLabel = "全成り駒"
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("全成り駒", maxLines = 1) }
                OutlinedButton(
                    onClick = {
                        currentSfen = DebugPositions.BLACK_HAND_MAX
                        currentLabel = "先手持駒最大"
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("先手持駒最大", maxLines = 1) }
                OutlinedButton(
                    onClick = {
                        currentSfen = DebugPositions.WHITE_HAND_MAX
                        currentLabel = "後手持駒最大"
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("後手持駒最大", maxLines = 1) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("後手視点 (flip):", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = flip, onCheckedChange = { flip = it })
            }

            Text("局面: $currentLabel", style = MaterialTheme.typography.labelSmall)
            Text("SFEN: $currentSfen", style = MaterialTheme.typography.labelSmall)

            ShogiBoardView(sfen = currentSfen, flip = flip)

            // 完了通知テスト（実物と同じチャンネル・PendingIntent）
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val db = AppDatabase.gameRepository(context)
                        val latestGame = db.getAllGames().firstOrNull()
                        val gameId = latestGame?.id ?: 0L
                        sendDebugCompletionNotification(context, gameId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(AppStrings.DEBUG_SEND_NOTIFICATION)
            }
        }
    }
}

/**
 * 解析完了通知をデバッグ用に即時送付する。
 * AnalysisService.showCompletionNotification() と同じチャンネル・PendingIntent 構成。
 * gameId = 最新ゲームの ID（なければ 0L のダミー）。
 */
private fun sendDebugCompletionNotification(context: android.content.Context, gameId: Long) {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_GAME_ID, gameId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notif = NotificationCompat.Builder(context, ShogiApp.CHANNEL_ANALYSIS)
        .setContentTitle(AppStrings.NOTIF_DONE_TITLE)
        .setContentText(AppStrings.NOTIF_DONE_TEXT)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.notify(DEBUG_NOTIF_ID, notif)
}

private const val DEBUG_NOTIF_ID = 9001
