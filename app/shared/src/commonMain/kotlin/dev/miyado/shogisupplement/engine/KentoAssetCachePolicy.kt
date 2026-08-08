package dev.miyado.shogisupplement.engine

/**
 * iOS端末内WASM解析（検討モード・ドリル等のオンデマンド単発局面解析）向け、
 * ローカル資産キャッシュ（docs/kento/・docs/kento-assets/一式）の版管理・完全性判定を担う
 * 純粋関数群。
 *
 * ネットワークI/O・ファイルI/Oはこの型の外（iOS側の実行部）が担い、ここでは
 * 「取得すべきか」「揃っているか」の判断だけを行う。判断ロジックをKotlin側に置くのは、
 * iOSにXCTest等のユニットテスト基盤が無く検証方針がJVMテスト完結（CLAUDE.md参照）のため、
 * jvmTestで判定を検証できるようにする狙い（Swift側は判断結果を実行するだけの薄い層にする）。
 *
 * Why not SHA-256等のハッシュ検証: 配信元（本番Pages。docs/copy-kento-assets.sh参照）は
 * 検証用ハッシュファイルを生成していない。代わりにHTTPレスポンスの宣言サイズ
 * （Content-Length）と実際に書き込んだバイト数の一致で完全性を判定する（サイズ照合への妥協）。
 */
object KentoAssetCachePolicy {

    /** ローカルに保存済みの資産の状態。[version] は保存済みディレクトリ名（無ければnull）。 */
    data class LocalState(val version: String?, val isComplete: Boolean)

    /** [decide] の判断結果。 */
    sealed class Decision {
        /** [version] のローカル資産をそのまま使ってよい（再取得不要）。 */
        data class UseLocal(val version: String) : Decision()

        /** [version] を新規取得（または再取得）する必要がある。 */
        data class Fetch(val version: String) : Decision()
    }

    /**
     * リモートの最新バージョンとローカル状態から、取得要否を判断する。
     *
     * ローカル版がリモートと同じバージョンで、かつ全ファイルが完全なときだけ [Decision.UseLocal]。
     * バージョンが違う・ローカルが存在しない・一部ファイルが欠けている（前回ダウンロード中断等）
     * のいずれでも [Decision.Fetch] を返す（呼び出し側は取得完了まで対話的解析をサーバーへ
     * 向ける。IosMainController KDoc参照）。
     */
    fun decide(remoteVersion: String, local: LocalState): Decision =
        if (local.version == remoteVersion && local.isComplete) {
            Decision.UseLocal(remoteVersion)
        } else {
            Decision.Fetch(remoteVersion)
        }

    /**
     * ダウンロードした1ファイルが完全か（サイズ照合）。
     *
     * @param declaredContentLength HTTPレスポンスの Content-Length（取得できなければnull）
     * @param actualBytes 実際にディスクへ書き込んだバイト数
     */
    fun isFileComplete(declaredContentLength: Long?, actualBytes: Long): Boolean =
        if (declaredContentLength == null) actualBytes > 0 else actualBytes == declaredContentLength

    /**
     * 1バージョン分の全ファイルが揃っているか。
     * 空リスト（1ファイルも対象がない）は「揃っていない」扱いにする
     * （呼び出し漏れ・取得対象リストの取り違えを「完全」と誤判定させないため）。
     */
    fun isVersionComplete(perFileComplete: List<Boolean>): Boolean =
        perFileComplete.isNotEmpty() && perFileComplete.all { it }
}
