// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "MuxyPush",
    platforms: [.iOS("26.2")],
    products: [
        .library(name: "MuxyPush", targets: ["MuxyPush"]),
    ],
    targets: [
        .target(name: "MuxyPush"),
    ]
)
