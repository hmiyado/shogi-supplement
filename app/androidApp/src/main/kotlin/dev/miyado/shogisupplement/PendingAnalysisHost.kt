package dev.miyado.shogisupplement

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.report.PendingAnalysisScreen

@Composable
fun PendingAnalysisHost(vm: MainViewModel, state: MainUiState.PendingAnalysis) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.analyzeStoredGame(state.game)
    }
    PendingAnalysisScreen(
        game = state.game,
        onBack = { vm.loadHome() },
        onAnalyze = {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.analyzeStoredGame(state.game)
            }
        },
    )
}
