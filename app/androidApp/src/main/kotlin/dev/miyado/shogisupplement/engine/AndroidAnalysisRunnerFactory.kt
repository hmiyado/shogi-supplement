package dev.miyado.shogisupplement.engine

import android.content.pm.ApplicationInfo
import dev.miyado.shogisupplement.crash.CrashReporter
import dev.miyado.shogisupplement.crash.SentryCrashReporter
import java.io.File

/** Android用AnalysisRunnerを構築する。各局でEngineを起動し、終了後に破棄する。 @param appInfo nativeLibraryDir取得用情報。 @param evalDir EvalDirのパス。 */
fun createAndroidAnalysisRunner(
    appInfo: ApplicationInfo,
    evalDir: File,
    workers: Int = 4,
    crashReporter: CrashReporter = SentryCrashReporter(),
): AnalysisRunner = AnalysisRunner(
    workers = workers,
    crashReporter = crashReporter,
    engineFactory = { IsolatedEngine(UsiEngineProcess.create(appInfo, evalDir)) },
)
