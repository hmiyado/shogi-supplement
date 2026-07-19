package dev.miyado.shogisupplement.engine

import android.content.pm.ApplicationInfo
import dev.miyado.shogisupplement.crash.CrashReporter
import dev.miyado.shogisupplement.crash.SentryCrashReporter
import java.io.File

/**
 * Android本番用の [AnalysisRunner] 構築ヘルパー。
 *
 * AnalysisRunner 本体は shared/commonMain にあり、android.content.pm.ApplicationInfo・
 * java.io.File は iOS からは使えないため、Android専用の構築ロジックをこの独立した
 * Factory 関数へ切り出している。
 *
 * nativeLibraryDir の [UsiEngineProcess] を毎局新規プロセスとして起動し、局の解析が
 * 終わったら [UsiEngineProcess.quit] でプロセスを終了する（disposeEngine 既定値のまま）。
 *
 * @param appInfo ApplicationInfo（nativeLibraryDir 取得用）
 * @param evalDir EvalDir（filesDir/eval）
 */
fun createAndroidAnalysisRunner(
    appInfo: ApplicationInfo,
    evalDir: File,
    workers: Int = 4,
    crashReporter: CrashReporter = SentryCrashReporter(),
): AnalysisRunner = AnalysisRunner(
    workers = workers,
    crashReporter = crashReporter,
    engineFactory = { UsiEngineProcess.create(appInfo, evalDir) },
)
