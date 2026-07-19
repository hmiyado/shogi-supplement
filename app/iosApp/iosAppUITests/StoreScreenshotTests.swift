import XCTest

/// App Store提出用スクリーンショットの撮影バッチ。
///
/// フローは PasteboardImportSmokeTests.swift と同一（クリップボード取込→先後選択→
/// 解析完了待ち→レポート自動遷移）を土台にし、レポート→ホーム→ドリルの5画面を
/// 巡回して XCTAttachment（lifetime = .keepAlways）としてスクリーンショットを添付する。
/// 実行後は xcresulttool で xcresult から PNG を抽出して tmp/store-screenshots/ に置く
/// （抽出スクリプト側の作業。本ファイルは撮影のみを担う）。
///
/// 撮影する5画面:
///   1. 01_home        — ホーム（推定棋力カード＋今日の1問＋解析済み棋譜）
///   2. 02_report       — レポート（悪手カードが見える状態・選択前）
///   3. 03_report_pv    — 悪手カードをタップ→「最善の変化」タブで読み筋・盤面を表示
///   4. 04_drill_question — ドリル出題画面（盤面＋降参ボタン）
///   5. 05_drill_result   — 降参して答えを見た結果画面
final class StoreScreenshotTests: XCTestCase {

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

    func testCaptureStoreScreenshots() throws {
        let kifText = try loadFixtureKifText()

        app = XCUIApplication()

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

        // KIFはlaunchEnvironmentで渡し、アプリ自身にクリップボードへ書き込ませる
        // （iosAppApp.swift の seedPasteboardForUITestIfNeeded 参照）。
        app.launchEnvironment["UITEST_PASTEBOARD_KIF_BASE64"] = Data(kifText.utf8).base64EncodedString()
        app.launch()

        // ── 1. 棋譜取込フロー（PasteboardImportSmokeTestsと同一） ──────────────
        let addKifButton = element(labeled: "棋譜を追加する", timeout: 20)
        XCTAssertTrue(addKifButton.exists, "「棋譜を追加する」ボタンが見つかりません")
        addKifButton.tap()

        let clipboardOption = element(labeled: "クリップボードから貼り付け", timeout: 10)
        XCTAssertTrue(clipboardOption.exists, "「クリップボードから貼り付け」が見つかりません")
        clipboardOption.tap()

        handlePastePermissionAlertIfNeeded()

        // フレッシュな端末（アカウント名未設定）では先後選択の前に棋力設定ダイアログ
        // （ImportState.RatingSetup → RatingSettingsDialog）が先に表示される。
        // 重要: アカウント名を空のまま「保存」すると hasAnyServiceAccount() が false のままで
        // completeRatingSetup → proceedAfterKifValidated が RatingSetup を再表示し、
        // ダイアログが閉じないように見える（IosMainController.kt:235）。フィクスチャの
        // 後手名 "miyado" をアカウント名として入力してから保存する（先後自動サジェストも
        // 後手側に効く）。設定済みの端末（再実行時）では表示されないため、出現は条件付き。
        let ratingDialogTitle = element(labeled: "棋力設定", timeout: 8)
        if ratingDialogTitle.exists {
            // アカウント名フィールド: ラベル「アカウント名（…）」のStaticTextを内包するTextView
            let accountFieldPredicate = NSPredicate(format: "label CONTAINS %@", "アカウント名")
            let accountField = app.textViews
                .containing(accountFieldPredicate)
                .firstMatch
            XCTAssertTrue(
                accountField.waitForExistence(timeout: 5),
                "棋力設定ダイアログのアカウント名フィールドが見つかりません",
            )
            accountField.tap()
            accountField.typeText("miyado")

            // 保存 → ダイアログが閉じるまで確認（閉じなければ再タップ・最大3回）
            var ratingDialogClosed = false
            for _ in 0..<3 {
                let saveButton = app.buttons["保存"].firstMatch
                XCTAssertTrue(saveButton.waitForExistence(timeout: 5), "棋力設定ダイアログの「保存」が見つかりません")
                saveButton.tap()
                if waitForDisappearance(of: ratingDialogTitle, timeout: 5) {
                    ratingDialogClosed = true
                    break
                }
            }
            XCTAssertTrue(ratingDialogClosed, "棋力設定ダイアログが閉じません（アカウント名の入力に失敗した可能性）")
        }

        let goteOption = element(labeledStartsWith: "後手（", timeout: 15)
        XCTAssertTrue(goteOption.exists, "先後選択ダイアログ（後手）が見つかりません")
        goteOption.tap()

        let startButton = element(labeled: "解析開始", timeout: 5)
        XCTAssertTrue(startButton.exists, "「解析開始」ボタンが見つかりません")
        startButton.tap()

        let dialogTitle = element(labeled: "自分の側", timeout: 2)
        XCTAssertTrue(
            waitForDisappearance(of: dialogTitle, timeout: 10),
            "先後選択ダイアログが閉じません（ラジオ選択が反映されていない可能性）",
        )

        // ── 2. 解析完了 → レポート画面へ自動遷移（最大10分待機） ─────────────────
        let reportIndicator = element(
            labeledContainsAny: ["この一局からの推定棋力", "悪手は見つかりませんでした"],
            timeout: 600,
        )
        XCTAssertTrue(reportIndicator.exists, "レポート画面へ自動遷移しませんでした（10分待機）")

        // 描画安定化のための小休止（初回フレームのちらつき対策）。
        Thread.sleep(forTimeInterval: 1.5)
        attachScreenshot(named: "02_report")

        // ── 3. 悪手カードをタップ →「最善の変化」タブで読み筋・盤面を表示 ──────────
        // 悪手カードは「実戦」ラベル（BLUNDER_CARD_ACTUAL）を目印にする（GameCardと同じ
        // 座標ヒット方式・PasteboardImportSmokeTestsのgameCard探索を踏襲）。
        // 先頭カードの bestPv が未保存（null）で「最善の変化」タブが無効なケースに備え、
        // 有効になるカードが見つかるまで最大5枚まで順に試す。
        var pvTabTapped = false
        let actualLabelPredicate = NSPredicate(format: "label CONTAINS %@", "実戦")
        let actualLabelElements = app.descendants(matching: .any).matching(actualLabelPredicate)
        let candidateCount = min(actualLabelElements.count, 5)
        for idx in 0..<max(candidateCount, 1) {
            let card = actualLabelElements.element(boundBy: idx)
            if !card.waitForExistence(timeout: 5) { continue }
            card.tap()
            let bestPvTab = element(labeled: "最善の変化", timeout: 3)
            if bestPvTab.exists && bestPvTab.isEnabled {
                bestPvTab.tap()
                Thread.sleep(forTimeInterval: 0.8)
                pvTabTapped = true
                break
            }
        }
        XCTAssertTrue(pvTabTapped, "「最善の変化」タブが有効な悪手カードが見つかりませんでした")
        attachScreenshot(named: "03_report_pv")

        // ── 4. ホームへ戻る ──────────────────────────────────────────────────
        let backButton = element(labeled: "戻る", timeout: 5)
        XCTAssertTrue(backButton.exists, "レポート画面の「戻る」ボタンが見つかりません")
        backButton.tap()

        let homeAgain = element(labeled: "棋譜を追加する", timeout: 15)
        XCTAssertTrue(homeAgain.exists, "ホームへ戻れませんでした")
        Thread.sleep(forTimeInterval: 1.0)
        attachScreenshot(named: "01_home")

        // ── 5. 「今日の1問」→ ドリル出題画面 ───────────────────────────────────
        let todaysDrill = element(labeledContainsAny: ["今日の1問", "ドリル"], timeout: 10)
        XCTAssertTrue(todaysDrill.exists, "「今日の1問」（またはドリル導線）が見つかりません")
        todaysDrill.tap()

        let drillGiveUp = element(labeled: "答えを見る", timeout: 15)
        XCTAssertTrue(drillGiveUp.exists, "ドリル出題画面に到達しませんでした")
        Thread.sleep(forTimeInterval: 0.8)
        attachScreenshot(named: "04_drill_question")

        // ── 6. 降参 → ドリル結果画面 ─────────────────────────────────────────
        drillGiveUp.tap()

        let drillResult = element(labeledContainsAny: ["正解。", "不正解"], timeout: 15)
        XCTAssertTrue(drillResult.exists, "ドリル結果画面に到達しませんでした")
        Thread.sleep(forTimeInterval: 0.8)
        attachScreenshot(named: "05_drill_result")
    }

    // MARK: - Helpers（PasteboardImportSmokeTests.swiftと同一実装。詳細コメントは同ファイル参照）

    private func loadFixtureKifText() throws -> String {
        let bundle = Bundle(for: Self.self)
        guard let url = bundle.url(forResource: "wars_game3", withExtension: "kif") else {
            throw XCTSkip("フィクスチャ wars_game3.kif がUIテストバンドルに見つかりません")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

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

    private func element(labeledContainsAny substrings: [String], timeout: TimeInterval) -> XCUIElement {
        let subpredicates = substrings.map { NSPredicate(format: "label CONTAINS %@", $0) }
        let predicate = NSCompoundPredicate(orPredicateWithSubpredicates: subpredicates)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func attachScreenshot(named name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
