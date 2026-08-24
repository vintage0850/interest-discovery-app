import UIKit
import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // App(driverFactory:) はKotlin側の @Composable。
        // IosDatabaseDriverFactory は shared/src/iosMain の実装。
        MainViewControllerKt.MainViewController(driverFactory: IosDatabaseDriverFactory())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
    }
}
