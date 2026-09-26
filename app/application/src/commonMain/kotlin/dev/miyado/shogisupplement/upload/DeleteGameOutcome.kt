package dev.miyado.shogisupplement.upload

/** 棋譜削除（ローカル＋任意でサーバー）の結果。 */
sealed class DeleteGameOutcome {
    /** 削除成功（サーバー削除を含む場合はサーバーも成功）。 */
    object Success : DeleteGameOutcome()

    /** サーバー削除に失敗した（ローカルは削除していない）。 */
    object ServerFailed : DeleteGameOutcome()

    /** 端末内の削除失敗・保存内容の競合。 */
    object LocalFailed : DeleteGameOutcome()
}
