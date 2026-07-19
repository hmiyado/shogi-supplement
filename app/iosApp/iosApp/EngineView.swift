import SharedUi
import SwiftUI

/// YaneuraOu in-process化をSharedUi（:shared）経由のKotlin実装で叩くスモークUI。
///
/// このViewはSharedUi（UsiEngineInProcess、shared/src/iosMain）経由でのみエンジンと
/// やり取りする。engine_wrapper.h のC APIはKotlin cinterop経由でのみ使われ、
/// Swiftから直接呼ぶことはない。
struct EngineView: View {
    @State private var handshakeStatus: String = "未実行"
    @State private var bestmoveText: String = "-"
    @State private var lastInfoText: String = "-"
    @State private var elapsedMsText: String = "-"
    @State private var isRunning = false

    // DESIGN.md Light トークンの最低限。
    private let bgColor = Color(red: 0xF7 / 255.0, green: 0xF3 / 255.0, blue: 0xEA / 255.0)
    private let inkColor = Color(red: 0x21 / 255.0, green: 0x1E / 255.0, blue: 0x1A / 255.0)
    private let accentColor = Color(red: 0x3A / 255.0, green: 0x4B / 255.0, blue: 0x7C / 255.0)

    var body: some View {
        ZStack {
            bgColor.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 20) {
                Text("エンジンin-process化（Kotlin実装）")
                    .font(.title2.bold())
                    .foregroundColor(accentColor)

                row(title: "USIハンドシェイク", value: handshakeStatus)
                row(title: "bestmove（best PVの先頭手）", value: bestmoveText)
                row(title: "最終info（best PV）", value: lastInfoText, monospacedSmall: true)
                row(title: "go nodes 400000 所要時間", value: elapsedMsText)

                Button(action: runSmoke) {
                    Text(isRunning ? "実行中…" : "エンジンで解析")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(isRunning ? Color.gray : accentColor)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .disabled(isRunning)

                Spacer()
            }
            .padding(24)
        }
        .onAppear {
            // タブ表示時に自動実行する（ボタンは再実行用＝解析のみ再送）。
            if !isRunning && handshakeStatus == "未実行" {
                runSmoke()
            }
        }
    }

    @ViewBuilder
    private func row(title: String, value: String, monospacedSmall: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.headline)
                .foregroundColor(inkColor)
            Text(value)
                .font(.system(monospacedSmall ? .footnote : .body, design: .monospaced))
                .foregroundColor(inkColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func runSmoke() {
        isRunning = true
        handshakeStatus = "起動中…"
        DispatchQueue.global(qos: .userInitiated).async {
            EngineSmokeRunner.run(
                onHandshake: { status in
                    DispatchQueue.main.async { handshakeStatus = status }
                },
                onResult: { bestmove, lastInfo, elapsedMs in
                    DispatchQueue.main.async {
                        bestmoveText = bestmove
                        lastInfoText = lastInfo
                        elapsedMsText = elapsedMs
                        isRunning = false
                    }
                }
            )
        }
    }
}

/// SharedUi（Kotlin）の UsiEngineInProcess をUSIプロトコル手順に沿って叩く薄いドライバ。
/// 不変条件（Threads=1/USI_Hash=128/MultiPV=2/FV_SCALE=20）はKotlin実装
/// （UsiEngineInProcess.create）側で保証される。
enum EngineSmokeRunner {
    // UsiEngineInProcessはプロセス内で一度しか起動できないため、
    // 生成した1インスタンスを使い回す（再実行ボタンは同一エンジンへの再解析）。
    private static var engine: (any Engine)?
    private static let startLock = NSLock()

    static func run(
        onHandshake: @escaping (String) -> Void,
        onResult: @escaping (String, String, String) -> Void
    ) {
        let evalDir = bundledEvalDir() ?? ""

        startLock.lock()
        if engine == nil {
            engine = UsiEngineInProcess.companion.create(evalDir: evalDir)
        }
        let currentEngine = engine
        startLock.unlock()

        guard let currentEngine else {
            onHandshake("エンジン起動に失敗（EvalDir=\(evalDir)）")
            onResult("-", "-", "-")
            return
        }

        onHandshake("usiok ✓ / readyok ✓（EvalDir=\(evalDir)）")

        let t0 = DispatchTime.now()
        let pvInfos = currentEngine.analyze(moves: [], nodes: 400_000)
        let t1 = DispatchTime.now()
        let ms = (t1.uptimeNanoseconds - t0.uptimeNanoseconds) / 1_000_000

        // bestmove相当 = MultiPV 1（最善PV）の先頭手。Engineインターフェースは
        // USIの生"bestmove"行を露出しない設計（Android版 UsiEngineProcess と同一）。
        let best = pvInfos.first(where: { $0.multipv == 1 }) ?? pvInfos.first
        let bestmoveText = best.flatMap { $0.pv.first }.map { "bestmove \($0)" } ?? "-"
        let lastInfoText = best.map(summarize) ?? "-"

        onResult(bestmoveText, lastInfoText, "\(ms) ms")
    }

    /// PvInfoからscoreとpv先頭数手だけを要約する。
    private static func summarize(_ info: PvInfo) -> String {
        let scoreText: String
        switch info.score {
        case let cp as ScoreCp:
            scoreText = "cp \(cp.value)"
        case let mate as ScoreMate:
            scoreText = "mate \(mate.plies)"
        default:
            scoreText = "?"
        }
        let pvText = info.pv.prefix(4).joined(separator: " ")
        return "score \(scoreText) / pv \(pvText)"
    }

    private static func bundledEvalDir() -> String? {
        Bundle.main.url(forResource: "eval", withExtension: nil)?.path
    }
}
