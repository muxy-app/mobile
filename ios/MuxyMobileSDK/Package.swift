// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "MuxyMobileSDK",
    platforms: [.iOS("26.2")],
    products: [
        .library(name: "MuxyMobile", targets: ["MuxyMobile"]),
    ],
    targets: [
        .binaryTarget(name: "muxy_mobileFFI", path: "Build/MuxyMobile.xcframework"),
        .target(
            name: "MuxyMobile",
            dependencies: ["muxy_mobileFFI"],
            path: "Build/swift"
        ),
    ]
)
