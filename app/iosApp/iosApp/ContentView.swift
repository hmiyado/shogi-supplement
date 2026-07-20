import SwiftUI
import SharedUi

/// iOS移植の縦切り検証画面。
///
/// 実演する3点（発注仕様どおり）:
///  1. バンドル同梱のKIF（sample.kif）を `KifParser`（shared）でパースし、対局者名・手数を表示
///  2. `DatabaseFactory`（iosMain）でDBを開き、`DatabaseRepository` でゲーム1件保存→読み出し
///  3. `StrengthEstimate(...).toDisplayString()` の結果表示（Kotlin→Swift境界の確認）
///
/// 見た目は DESIGN.md（リポジトリルート）の最低限のみ反映（背景・文字・アクセント色）。
///
/// リリースビルドは本体（Compose Multiplatform の MainViewController）のみを表示する。
/// デバッグビルドだけ、開発用の「Spike」（SwiftUI縦切り検証）と「Engine」
/// （エンジンin-process化のスモークUI）を下部タブとして併設する。
struct ContentView: View {
    #if DEBUG
    // 既定タブは CMP（本実装）。Spike/Engineタブは開発用
    // （EngineタブはUsiEngineInProcessがプロセス内で一度しか起動できない制約と衝突するため、
    // 実動作確認は「CMP」タブのみで行うこと）。
    @State private var selection = 1
    #endif

    var body: some View {
        #if DEBUG
        TabView(selection: $selection) {
            SpikeView()
                .tabItem { Label("Spike", systemImage: "checkmark.seal") }
                .tag(0)

            ComposeView()
                .ignoresSafeArea()
                .tabItem { Label("CMP", systemImage: "square.grid.3x3") }
                .tag(1)

            EngineView()
                .tabItem { Label("Engine", systemImage: "cpu") }
                .tag(2)
        }
        #else
        ComposeView()
            .ignoresSafeArea()
        #endif
    }
}

/// :ui の ComposeUIViewController（MainViewController）を埋め込む UIViewControllerRepresentable。
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

#if DEBUG
/// SwiftUIスパイク本体（KIFパース・DB永続化・推定棋力表示の3点を確認する）。
/// リリースビルドには含まれない（ContentView.body の #if DEBUG からしか
/// 参照されないが、型定義自体も #if で囲んでおかないとリリース構成でも
/// コンパイル対象になり、Kotlin側API変更に追随できず壊れる）。
struct SpikeView: View {
    @State private var kifSummary: String = "読み込み中…"
    @State private var dbSummary: String = "DB確認中…"
    @State private var strengthSummary: String = "計算中…"

    // DESIGN.md Light トークンの最低限（bg / ink / primary）。
    private let bgColor = Color(hex: 0xF7F3EA)
    private let inkColor = Color(hex: 0x211E1A)
    private let accentColor = Color(hex: 0x3A4B7C)

    var body: some View {
        ZStack {
            bgColor.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 24) {
                Text("iOS移植スパイク")
                    .font(.title2.bold())
                    .foregroundColor(accentColor)

                section(title: "1. KIFパース（KifParser）", body: kifSummary)
                section(title: "2. DB永続化（DatabaseFactory / DatabaseRepository）", body: dbSummary)
                section(title: "3. 推定棋力の表示（StrengthEstimate.toDisplayString）", body: strengthSummary)

                Spacer()
            }
            .padding(24)
        }
        .onAppear(perform: runChecks)
    }

    @ViewBuilder
    private func section(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
                .foregroundColor(inkColor)
            Text(body)
                .font(.system(.body, design: .monospaced))
                .foregroundColor(inkColor)
        }
    }

    private func runChecks() {
        guard let sampleText = loadSampleKif() else {
            kifSummary = "sample.kif が見つかりません"
            dbSummary = "sample.kif が見つかりません"
            return
        }

        let game = KifParser().parse(text: sampleText)
        runKifParse(game: game)
        runDatabaseCheck(game: game, kifText: sampleText)
        runStrengthDisplay()
    }

    private func loadSampleKif() -> String? {
        guard let url = Bundle.main.url(forResource: "sample", withExtension: "kif") else {
            return nil
        }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    private func runKifParse(game: KifuGame) {
        let sente = game.senteName ?? "?"
        let gote = game.goteName ?? "?"
        kifSummary = "\(sente) vs \(gote) ・ \(game.moves.count)手"
    }

    private func runDatabaseCheck(game: KifuGame, kifText: String) {
        let repo = DatabaseFactory.shared.gameRepository()
        // サンプルKIF固定のコンテンツハッシュ。毎起動で重複保存しないようにgetByHashで確認。
        let contentHash = "ios-spike-sample-kif-v1"

        if repo.getByHash(contentHash: contentHash) == nil {
            _ = repo.saveAnalysis(
                fileName: "sample.kif",
                contentHash: contentHash,
                moves: game.moves,
                headers: game.headers,
                reports: [],
                rating: 1682,
                ratingSampleMoves: nil,
                coefVersion: "ios-spike",
                analyzedAt: Int64(Date().timeIntervalSince1970),
                kifText: kifText,
                userSide: nil,
                ratingService: nil,
                ratingRaw: nil,
                ratingRule: nil,
                sourcePlace: nil,
                gameWinner: nil,
                endReason: nil
            )
        }

        let count = repo.getAllGames().count
        dbSummary = "DB: OK（\(count)件）"
    }

    private func runStrengthDisplay() {
        let estimate = StrengthEstimate(rating: 1682, clamped: .none, errorMargin: 650, totalMoves: 800)
        strengthSummary = estimate.toDisplayString()
    }
}
#endif

private extension Color {
    init(hex: UInt32) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}

#Preview {
    ContentView()
}
