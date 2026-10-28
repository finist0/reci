// swift-tools-version:5.9
// The learning logic of the iPhone app, without UIKit, so it builds and is tested on Linux too.
import PackageDescription

let package = Package(
    name: "ReciCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "ReciCore", targets: ["ReciCore"])],
    targets: [
        .target(name: "ReciCore"),
        .testTarget(name: "ReciCoreTests", dependencies: ["ReciCore"]),
    ]
)
