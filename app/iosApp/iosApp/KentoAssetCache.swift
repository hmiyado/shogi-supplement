import SharedUi
import Foundation

/// WASM解析のバイナリ・ページ一式（ホストページ・ブリッジ/Worker JS・エンジンwasm・評価関数）を
/// 本番Pagesからダウンロードし、Application Support配下へバージョン別ディレクトリで
/// 保存するキャッシュ。
///
/// 版管理・完全性判定の判断ロジック（何を取得すべきか・揃っているか）は
/// `KentoAssetCachePolicy`（Kotlin）に置き、ここではネットワーク取得・ファイル配置の
/// 実行だけを行う（判断をJVMテスト可能な層へ寄せるため）。
///
/// 取得は常時実行する（Wi-Fi等の条件を付けない。1回あたり約65MB・初回のみ）。
/// 進捗UIは持たず、準備状態は [state]（`.notReady`/`.downloading`/`.ready`）で公開する。
///
/// 完全性の判定にSHA-256等のハッシュを使わない理由: 配信元（docs/copy-kento-assets.sh）が
/// 検証用ハッシュファイルを生成していないため。HTTPレスポンスの宣言サイズ
/// （Content-Length）と実際に書き込んだバイト数の一致で妥協する
/// （[KentoAssetCachePolicy.isFileComplete] 参照）。
final class KentoAssetCache {
    static let shared = KentoAssetCache()

    enum State: Equatable {
        case notReady
        case downloading
        /// [rootURL] は `docs/kento/` 相当のローカルディレクトリ（WKURLSchemeHandlerのroot）。
        case ready(rootURL: URL, version: String)
    }

    private let lock = NSLock()
    private var _state: State = .notReady
    private var startedRefresh = false

    /// 現在の状態（どのスレッドからでも読める。書き込みは [lock] 経由のみ）。
    var state: State {
        lock.lock()
        defer { lock.unlock() }
        return _state
    }

    private init() {}

    /// 2回目以降の呼び出しは無視する（多重ダウンロード防止）。
    func start() {
        lock.lock()
        if startedRefresh {
            lock.unlock()
            return
        }
        startedRefresh = true
        lock.unlock()

        Task.detached(priority: .utility) { [weak self] in
            await self?.refresh()
        }
    }

    private func setState(_ s: State) {
        lock.lock()
        _state = s
        lock.unlock()
    }

    private func refresh() async {
        setState(.downloading)

        let remoteVersion: String
        do {
            remoteVersion = try await Self.fetchText(Self.kentoAssetsBaseURL.appendingPathComponent("VERSION"))
        } catch {
            // バージョン確認自体が失敗（オフライン等）: 既にローカルへ完全に保存済みの版が
            // あればそれをそのまま使う（オフライン検討はこの経路で機能する。ローカルWASMバイナリが
            // 無ければサーバーへ委ねるほかない）。
            if let existing = Self.findAnyComplete() {
                setState(.ready(rootURL: existing.rootURL, version: existing.version))
            } else {
                setState(.notReady)
            }
            return
        }

        let local = Self.localState(forVersion: remoteVersion)
        let decision = KentoAssetCachePolicy.shared.decide(remoteVersion: remoteVersion, local: local)

        if let useLocal = decision as? KentoAssetCachePolicy.DecisionUseLocal {
            setState(.ready(rootURL: Self.finalDir(version: useLocal.version), version: useLocal.version))
            return
        }
        guard let fetch = decision as? KentoAssetCachePolicy.DecisionFetch else {
            setState(.notReady)
            return
        }

        do {
            let rootURL = try await downloadVersion(fetch.version)
            Self.deleteOtherVersions(keeping: fetch.version)
            setState(.ready(rootURL: rootURL, version: fetch.version))
        } catch {
            // 失敗時はWASMバイナリ未準備のまま（次回起動時に再試行する）。
            setState(.notReady)
        }
    }

    /// [version] の全ファイルを一時ディレクトリへ取得し、全ファイルの完全性を確認できたら
    /// 最終ディレクトリへ原子的に移動する（中断ダウンロードが「完全」な最終ディレクトリとして
    /// 残らないようにするため）。
    private func downloadVersion(_ version: String) async throws -> URL {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("kento-assets-download-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        var completeFlags: [Bool] = []

        for name in Self.kentoFiles {
            let ok = try await Self.downloadFile(
                from: Self.kentoBaseURL.appendingPathComponent(name),
                to: tempDir.appendingPathComponent("kento", isDirectory: true).appendingPathComponent(name)
            )
            completeFlags.append(ok)
        }

        // kento-assets/VERSION（マーカー。webapp-bridge.js の resolveAssetDirUrl が
        // ベースURL直下のこのファイルを読んでバージョン付きサブディレクトリを解決する）。
        let versionMarkerOk = try await Self.downloadFile(
            from: Self.kentoAssetsBaseURL.appendingPathComponent("VERSION"),
            to: tempDir.appendingPathComponent("kento-assets", isDirectory: true).appendingPathComponent("VERSION")
        )
        completeFlags.append(versionMarkerOk)

        for name in Self.engineFiles {
            let ok = try await Self.downloadFile(
                from: Self.kentoAssetsBaseURL.appendingPathComponent(version).appendingPathComponent(name),
                to: tempDir.appendingPathComponent("kento-assets", isDirectory: true)
                    .appendingPathComponent(version, isDirectory: true).appendingPathComponent(name)
            )
            completeFlags.append(ok)
        }

        let boxedFlags = completeFlags.map { KotlinBoolean(bool: $0) }
        guard KentoAssetCachePolicy.shared.isVersionComplete(perFileComplete: boxedFlags) else {
            throw KentoAssetCacheError.incompleteDownload
        }

        let finalDir = Self.finalDir(version: version)
        try? FileManager.default.removeItem(at: finalDir)
        try FileManager.default.createDirectory(
            at: finalDir.deletingLastPathComponent(), withIntermediateDirectories: true
        )
        try FileManager.default.moveItem(at: tempDir, to: finalDir)
        return finalDir
    }

    /// 1ファイルを取得し [destination] へ書き込む。戻り値は
    /// [KentoAssetCachePolicy.isFileComplete] によるサイズ照合の結果。
    ///
    /// HTTPステータスを明示的に確認する: `URLSession.data(from:)` は404等の非2xxでも
    /// エラーを投げず「本文（例えばPagesの404 HTMLページ）」をそのまま返してしまう。
    /// ステータスを見ずにサイズ照合だけに頼ると、404ページ自身のContent-Lengthと
    /// 実バイト数は一致してしまうため「完全なファイル」と誤判定する。
    private static func downloadFile(from url: URL, to destination: URL) async throws -> Bool {
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw KentoAssetCacheError.httpError(url: url)
        }
        let declaredLength = http.expectedContentLength
        let declared: Int64? = declaredLength >= 0 ? declaredLength : nil

        try FileManager.default.createDirectory(
            at: destination.deletingLastPathComponent(), withIntermediateDirectories: true
        )
        try data.write(to: destination, options: .atomic)

        return KentoAssetCachePolicy.shared.isFileComplete(
            declaredContentLength: declared.map { KotlinLong(longLong: $0) },
            actualBytes: Int64(data.count)
        )
    }

    private static func fetchText(_ url: URL) async throws -> String {
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw KentoAssetCacheError.httpError(url: url)
        }
        guard let text = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty else {
            throw KentoAssetCacheError.emptyVersion
        }
        return text
    }

    // MARK: - ローカルファイルシステム

    private static var rootDir: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("KentoAssets", isDirectory: true)
    }

    private static func finalDir(version: String) -> URL {
        rootDir.appendingPathComponent(version, isDirectory: true)
    }

    /// [version] のローカル保存状態。最終ディレクトリへの移動は全ファイル確認後にのみ行う
    /// （[downloadVersion] 参照）ため、期待ファイルが全て存在すれば完全とみなせる
    /// （バイト単位の再照合は初回ダウンロード時にのみ行い、以後の起動では期待サイズを
    /// 保持していないため再照合しない。ディスク破損等の極端なケースまでは救わない）。
    private static func localState(forVersion version: String) -> KentoAssetCachePolicy.LocalState {
        let dir = finalDir(version: version)
        guard FileManager.default.fileExists(atPath: dir.path) else {
            return KentoAssetCachePolicy.LocalState(version: nil, isComplete: false)
        }
        let allExist = expectedRelativePaths(version: version).allSatisfy {
            FileManager.default.fileExists(atPath: dir.appendingPathComponent($0).path)
        }
        return KentoAssetCachePolicy.LocalState(version: allExist ? version : nil, isComplete: allExist)
    }

    /// バージョン確認自体ができない（オフライン等）ときのための、既存の完全な版の探索。
    /// 通常は高々1バージョンしか残らない（[deleteOtherVersions] が旧版を消すため）。
    private static func findAnyComplete() -> (rootURL: URL, version: String)? {
        guard let entries = try? FileManager.default.contentsOfDirectory(atPath: rootDir.path) else { return nil }
        for version in entries {
            let state = localState(forVersion: version)
            if state.isComplete {
                return (finalDir(version: version), version)
            }
        }
        return nil
    }

    private static func deleteOtherVersions(keeping version: String) {
        guard let entries = try? FileManager.default.contentsOfDirectory(atPath: rootDir.path) else { return }
        for entry in entries where entry != version {
            try? FileManager.default.removeItem(at: rootDir.appendingPathComponent(entry, isDirectory: true))
        }
    }

    private static func expectedRelativePaths(version: String) -> [String] {
        kentoFiles.map { "kento/\($0)" }
            + ["kento-assets/VERSION"]
            + engineFiles.map { "kento-assets/\(version)/\($0)" }
    }

    // MARK: - 配信元・対象ファイル一覧

    /// 配信元サイトのルートURL。DEBUGビルドに限り環境変数・デバッグ画面の保存値で
    /// 差し替え可能（未公開のWASMバイナリをローカル配信で検証するためのフック）。
    /// 優先順位: 環境変数 > 保存値 > 本番。識別子と既定値は `KentoSiteOverride` が単一の源。
    ///
    /// Why not この分岐をDEBUG外にも残す: `#if DEBUG` の外側に置くとRelease配布物にも
    /// UserDefaultsの読み取りコード自体は含まれてしまい、意図せず保存値を拾うリスクを
    /// 完全には排除できない。ここではRelease版バイナリにこの読み取りコードそのものを
    /// 含めない（コンパイル時に除去する）ことで「保存値を一切読まない」を担保する。
    private static var siteBaseURL: URL {
        #if DEBUG
        if let override = ProcessInfo.processInfo.environment[KentoSiteOverride.shared.environmentKey],
           let url = URL(string: override) {
            return url
        }
        if let saved = UserDefaults.standard.string(forKey: KentoSiteOverride.shared.defaultsKey),
           let url = URL(string: saved) {
            return url
        }
        #endif
        return URL(string: KentoSiteOverride.shared.productionUrl)!
    }

    private static var kentoBaseURL: URL {
        siteBaseURL.appendingPathComponent("kento", isDirectory: true)
    }
    private static var kentoAssetsBaseURL: URL {
        siteBaseURL.appendingPathComponent("kento-assets", isDirectory: true)
    }

    /// docs/kento/ 直下のホストページ・ブリッジ/Worker JS（バージョン概念なし。
    /// エンジンVERSIONの切替と同じタイミングでまとめて再取得する簡略化）。
    private static let kentoFiles = [
        "wasm-analysis-host.html", "wasm-analysis-host.js", "webapp-bridge.js", "analysis-worker.js",
        "study-worker.js",
    ]

    /// docs/kento-assets/<VERSION>/ 配下のエンジンWASMバイナリ（docs/copy-kento-assets.sh参照）。
    private static let engineFiles = [
        "yaneuraou-simd.js", "yaneuraou-simd.wasm",
        "yaneuraou-nosimd.js", "yaneuraou-nosimd.wasm",
        "nn.bin",
    ]
}

enum KentoAssetCacheError: Error {
    case incompleteDownload
    case emptyVersion
    case httpError(url: URL)
}
