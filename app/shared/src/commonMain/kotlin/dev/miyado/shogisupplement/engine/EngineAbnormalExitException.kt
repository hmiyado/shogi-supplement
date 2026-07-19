package dev.miyado.shogisupplement.engine

/**
 * エンジンプロセス/インスタンスが異常終了したことを示す例外。
 *
 * Android の [dev.miyado.shogisupplement.engine.UsiEngineProcess]（androidApp）が
 * EOF / 予期しないプロセス終了を検知したときにスローされる。呼び出し元（[AnalysisRunner]）が
 * この情報を CrashReporter に付加して送信する。
 *
 * @property exitCode プロセス終了コード（プロセスがまだ終了していなかった場合 null）
 * @property lastCommandName 直前に送信した USI コマンド名（"go"/"position" など。内容は含まない）
 */
class EngineAbnormalExitException(
    message: String,
    val exitCode: Int?,
    val lastCommandName: String,
) : RuntimeException(message)
