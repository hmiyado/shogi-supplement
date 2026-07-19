import XCTest

/// 実機スモーク: クリップボード経由のKIF取込フローをE2Eで検証する。
///
/// フロー: launchEnvironmentでKIFを渡す → アプリ起動時にアプリ自身がクリップボードへ
/// セット（実機ではランナーからのUIPasteboard書き込みが拒否されるため・iosAppApp.swift参照）→
/// 「棋譜を追加する」→「クリップボードから貼り付け」→ ペースト許可アラート（springboard）→
/// 先後選択（後手）→「解析開始」→ 解析完了（or 重複検出）でホーム復帰 →
/// 対局カードをタップ → レポート画面（悪手カード or 推定棋力行）を確認。
///
/// 既に同一棋譜が取込済みの場合、AnalysisOrchestrator はほぼ即座に
/// `Outcome.Completed(alreadyExisted = true)` を返してホームへ復帰する（専用の「重複」ダイアログは
/// 存在しない・IosMainController.confirmSideAndAnalyze 参照）。本テストは「ホームに
/// 対局カード（場所=将棋ウォーズ）が現れる」ことだけを見るため、新規解析・重複のどちらでも
/// 同じアサーションで通る。
final class PasteboardImportSmokeTests: XCTestCase {

    private var app: XCUIApplication!
    private var pasteInterruptionMonitor: NSObjectProtocol?

    /// ペースト許可アラートの文言候補（iOSバージョン差・言語差を吸収）。
    private static let allowPasteLabels = [
        "ペーストを許可", "許可", "貼り付けを許可", "ペースト",
        "Allow Paste", "Allow", "OK",
    ]

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        if let monitor = pasteInterruptionMonitor {
            removeUIInterruptionMonitor(monitor)
            pasteInterruptionMonitor = nil
        }
    }

    func testPasteboardImportSmoke() throws {
        let kifText = try loadFixtureKifText()

        app = XCUIApplication()

        // addUIInterruptionMonitor: XCTestがapp要素を問い合わせるたびに、springboardの
        // システムアラートを自動検出してこのハンドラを呼ぶ（Appleの標準機構）。
        pasteInterruptionMonitor = addUIInterruptionMonitor(withDescription: "ペースト許可アラート") { alert in
            for label in Self.allowPasteLabels {
                let button = alert.buttons[label]
                if button.exists {
                    button.tap()
                    return true
                }
            }
            return false
        }

        // 2. アプリ起動 → 既定タブはCMP（iosAppApp.swift参照）。
        // KIFはlaunchEnvironmentで渡し、アプリ自身にクリップボードへ書き込ませる。
        app.launchEnvironment["UITEST_PASTEBOARD_KIF_BASE64"] = Data(kifText.utf8).base64EncodedString()
        app.launch()
        attachScreenshot(named: "01_launched")

        // 3. 「棋譜を追加する」→「クリップボードから貼り付け」
        let addKifButton = element(labeled: "棋譜を追加する", timeout: 20)
        XCTAssertTrue(addKifButton.exists, "「棋譜を追加する」ボタンが見つかりません")
        addKifButton.tap()

        let clipboardOption = element(labeled: "クリップボードから貼り付け", timeout: 10)
        XCTAssertTrue(clipboardOption.exists, "「クリップボードから貼り付け」が見つかりません")
        clipboardOption.tap()

        // 4. ペースト許可アラート（iOS 16+）。interruption monitor が拾わなかった場合に備え、
        //    springboardを直接クエリしても処理する（両対応）。
        handlePastePermissionAlertIfNeeded()
        attachScreenshot(named: "02_after_paste_permission")

        // 5. 先後選択ダイアログ →「後手」ラジオをタップ → 「解析開始」で確定。
        // ダイアログ見出し「先手: … 後手: …」も「後手」を含むため、ラジオ行の
        // ラベル「後手（プレイヤー名）」に前方一致で絞る。
        let goteOption = element(labeledStartsWith: "後手（", timeout: 15)
        XCTAssertTrue(goteOption.exists, "先後選択ダイアログ（後手）が見つかりません")
        goteOption.tap()

        let startButton = element(labeled: "解析開始", timeout: 5)
        XCTAssertTrue(startButton.exists, "「解析開始」ボタンが見つかりません")
        startButton.tap()
        attachScreenshot(named: "03_analysis_started")

        // 先後未選択のまま「解析開始」を押しても何も起きないため、ダイアログが
        // 閉じたことを確認して選択ミスを早期に検出する。
        let dialogTitle = element(labeled: "自分の側", timeout: 2)
        XCTAssertTrue(
            waitForDisappearance(of: dialogTitle, timeout: 10),
            "先後選択ダイアログが閉じません（ラジオ選択が反映されていない可能性）",
        )

        // 6. 解析完了（または重複検出）でホームに戻るまで待つ（A12実機の解析は数分かかる・上限10分）。
        let addKifButtonAgain = element(labeled: "棋譜を追加する", timeout: 600)
        XCTAssertTrue(addKifButtonAgain.exists, "解析完了後にホーム画面へ戻りませんでした（10分待機）")
        attachScreenshot(named: "04_home_after_analysis")

        // 7. 対局カード（場所=将棋ウォーズ）をタップ → レポート画面。
        let gameCard = element(labeledContains: "将棋ウォーズ", timeout: 15)
        XCTAssertTrue(gameCard.exists, "対局カード（将棋ウォーズ）が見つかりません")
        gameCard.tap()

        // 8. レポート画面: 悪手カード or 推定棋力行のいずれかが存在することを確認する。
        let reportIndicator = element(
            labeledContainsAny: ["この一局からの推定棋力", "悪手は見つかりませんでした"],
            timeout: 20,
        )
        XCTAssertTrue(reportIndicator.exists, "レポート画面の内容（推定棋力/悪手カード）が確認できません")
        attachScreenshot(named: "05_report")
    }

    // MARK: - Helpers

    private func loadFixtureKifText() throws -> String {
        let bundle = Bundle(for: Self.self)
        guard let url = bundle.url(forResource: "wars_game3", withExtension: "kif") else {
            throw XCTSkip("フィクスチャ wars_game3.kif がUIテストバンドルに見つかりません")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// springboardが提示するペースト許可アラートを直接クエリして処理する
    /// （addUIInterruptionMonitor が何らかの理由で発火しなかった場合の保険）。
    private func handlePastePermissionAlertIfNeeded(timeout: TimeInterval = 8) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            for label in Self.allowPasteLabels {
                let springboardButton = springboard.buttons[label]
                if springboardButton.exists {
                    springboardButton.tap()
                    return
                }
                let appButton = app.buttons[label]
                if appButton.exists {
                    appButton.tap()
                    return
                }
            }
            usleep(300_000)
        }
    }

    /// ラベル完全一致で要素を探す（Compose Multiplatform iOSのアクセシビリティ同期は
    /// 要素種別がbutton/staticText/otherに揺れるため、種別を問わずdescendantsから探す）。
    private func element(labeled label: String, timeout: TimeInterval) -> XCUIElement {
        let predicate = NSPredicate(format: "label == %@", label)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func element(labeledStartsWith prefix: String, timeout: TimeInterval) -> XCUIElement {
        let predicate = NSPredicate(format: "label BEGINSWITH %@", prefix)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func waitForDisappearance(of element: XCUIElement, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "exists == false")
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }

    private func element(labeledContains substring: String, timeout: TimeInterval) -> XCUIElement {
        let predicate = NSPredicate(format: "label CONTAINS %@", substring)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func element(labeledContainsAny substrings: [String], timeout: TimeInterval) -> XCUIElement {
        let subpredicates = substrings.map { NSPredicate(format: "label CONTAINS %@", $0) }
        let predicate = NSCompoundPredicate(orPredicateWithSubpredicates: subpredicates)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func attachScreenshot(named name: String) {
        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
