import SwiftUI
import SharedUi

/// アプリの画面。Debug/Releaseとも本体UI（Compose Multiplatform）が画面全体を占める。
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

/// :ui の ComposeUIViewController（MainViewController）を埋め込む UIViewControllerRepresentable。
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

#Preview {
    ContentView()
}
