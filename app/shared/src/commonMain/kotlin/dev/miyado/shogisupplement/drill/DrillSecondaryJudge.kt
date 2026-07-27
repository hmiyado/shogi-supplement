package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.db.BlunderRecord

/**
 * ドリルの二次判定（一次判定 [DrillJudge.judgePrimary] が Ambiguous/Unavailable を
 * 返した場合のみ呼ばれる）の注入界面。
 *
 * プラットフォームごとに実装を差し替える:
 * - [EngineDrillSecondaryJudge]: 端末エンジン（Android常時・iOSはサーバー未設定時）
 * - [RemoteDrillSecondaryJudge]: サーバー単発局面解析（iOSでbaseUrl設定時のみ）
 *
 * suspend にしているのはサーバー版がネットワークI/Oを行うため。端末エンジン版は
 * 内部で同期呼び出しをそのまま実行する（ホスト側が既にIOディスパッチャ上で呼ぶ前提は
 * 従来と変わらない）。
 */
fun interface DrillSecondaryJudge {
    suspend fun judge(blunder: BlunderRecord, userMoveUsi: String): DrillJudge.DrillResult
}
