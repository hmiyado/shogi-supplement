import SharedUi
import UIKit
import UniformTypeIdentifiers

/// 「ファイルから」KIF取込のUIKit側実装。
///
/// Compose（:ui commonMain の `IosMainController` / `MainViewController.kt`）は
/// UIDocumentPickerViewController を直接扱えない（ComposeUIViewControllerのsubtreeから
/// 別のUIViewControllerをpresentする素朴な方法が無い）ため、UIKit実装はSwift側に置き、
/// `SharedUi` の `IosFileImportBridge`（Kotlin object）経由でCompose側へブリッジする。
///
/// 境界はプレーンな関数呼び出し／クロージャ代入のみ:
/// - 起動時（init）に `IosFileImportBridge.shared.presentPickerHandler` へ
///   「ピッカーを提示する」クロージャを代入する。
/// - Compose側が「ファイルから」タップで `IosFileImportBridge.shared.requestOpenFilePicker()`
///   を呼ぶと、上記クロージャ経由でこのクラスの `presentPicker()` が呼ばれる。
/// - ユーザーがファイルを選ぶと `documentPicker(_:didPickDocumentsAt:)` がテキストを
///   デコードし、`IosFileImportBridge.shared.onFilePicked(fileName:text:)` へ渡す。
///   デコード失敗時は `onFilePickFailed(fileName:)`。キャンセル時は何も呼ばない
///   （Compose側のダイアログは選択前に既に閉じているため通知不要）。
final class KifFilePickerCoordinator: NSObject, UIDocumentPickerDelegate {
    static let shared = KifFilePickerCoordinator()

    private override init() {
        super.init()
        IosFileImportBridge.shared.presentPickerHandler = { [weak self] in
            self?.presentPicker()
        }
    }

    private func presentPicker() {
        // .kif は独自拡張子でシステムのUTTypeデータベースには無いため、
        // ファイル名拡張子から都度生成する（失敗時は .plainText にフォールバック）。
        let kifType = UTType(filenameExtension: "kif") ?? UTType.plainText
        let types: [UTType] = [kifType, .plainText, .text]

        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false

        guard let presenter = Self.topViewController() else { return }
        presenter.present(picker, animated: true)
    }

    private static func topViewController() -> UIViewController? {
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow })?.rootViewController
        else {
            return nil
        }
        var top = root
        while let presented = top.presentedViewController {
            top = presented
        }
        return top
    }

    // MARK: - UIDocumentPickerDelegate

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        let fileName = url.lastPathComponent

        let didStartAccess = url.startAccessingSecurityScopedResource()
        defer {
            if didStartAccess { url.stopAccessingSecurityScopedResource() }
        }

        guard let data = try? Data(contentsOf: url), let text = Self.decodeKifText(data: data) else {
            IosFileImportBridge.shared.onFilePickFailed(fileName: fileName)
            return
        }
        IosFileImportBridge.shared.onFilePicked(fileName: fileName, text: text)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        // no-op: Compose側の選択ダイアログは既に閉じているため何も通知しない。
    }

    /// UTF-8優先、失敗時はShift_JISへフォールバックする（KIFはShift_JIS収録のものが多い）。
    private static func decodeKifText(data: Data) -> String? {
        if let utf8Text = String(data: data, encoding: .utf8) {
            return utf8Text
        }
        let shiftJisEncoding = String.Encoding(
            rawValue: CFStringConvertEncodingToNSStringEncoding(
                CFStringEncoding(CFStringEncodings.shiftJIS.rawValue),
            ),
        )
        return String(data: data, encoding: shiftJisEncoding)
    }
}
