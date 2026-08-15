package dev.miyado.shogisupplement.engine

/**
 * WASMバイナリのローカルキャッシュの版管理・完全性判定。
 *
 * I/Oとハッシュ計算はこの型の外が担う。判断だけを純粋関数で持つのは、iOSに
 * ユニットテスト基盤が無く検証をJVMテストで完結させるため（CLAUDE.md参照）。
 */
object KentoAssetCachePolicy {

    /** ローカルに保存済みのWASMバイナリの状態。[version] は保存済みディレクトリ名（無ければnull）。 */
    data class LocalState(val version: String?, val isComplete: Boolean)

    /** [decide] の判断結果。 */
    sealed class Decision {
        /** [version] のローカルWASMバイナリをそのまま使ってよい（再取得不要）。 */
        data class UseLocal(val version: String) : Decision()

        /** [version] を新規取得（または再取得）する必要がある。 */
        data class Fetch(val version: String) : Decision()
    }

    /** 同じバージョンが完全に揃っているときだけ [Decision.UseLocal]、それ以外は [Decision.Fetch]。 */
    fun decide(remoteVersion: String, local: LocalState): Decision =
        if (local.version == remoteVersion && local.isComplete) {
            Decision.UseLocal(remoteVersion)
        } else {
            Decision.Fetch(remoteVersion)
        }

    /**
     * 取得した1ファイルのサイズが宣言（Content-Length・不明ならnull）と一致するか。
     * ハッシュ照合（[KentoAssetManifest.matches]）の前段の安価な早期失敗で、最終判断ではない。
     */
    fun isFileComplete(declaredContentLength: Long?, actualBytes: Long): Boolean =
        if (declaredContentLength == null) actualBytes > 0 else actualBytes == declaredContentLength

    /** 1バージョン分が揃っているか。空リストは「揃っていない」（取得対象ゼロを完全と誤判定しない）。 */
    fun isVersionComplete(perFileComplete: List<Boolean>): Boolean =
        perFileComplete.isNotEmpty() && perFileComplete.all { it }
}
