plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.muxy.sdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.directories += "Installed/kotlin"
            jniLibs.directories += "Installed/jniLibs"
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude("uniffi/**")
    }
}

dependencies {
    api(variantOf(libs.jna) { artifactType("aar") })
    ktlintRuleset(project(":ktlint-rules")) { isTransitive = false }
}

val checkMuxySdk =
    tasks.register("checkMuxySdk") {
        val nativeLibraries =
            listOf("arm64-v8a", "armeabi-v7a", "x86_64").map { "Installed/jniLibs/$it/libmuxy_mobile.so" }
        val installedFiles =
            (listOf("Installed/REVISION", "Installed/kotlin/uniffi/muxy_mobile/muxy_mobile.kt") + nativeLibraries)
                .map { layout.projectDirectory.file(it).asFile }
        doLast {
            if (installedFiles.all { it.isFile }) return@doLast
            throw GradleException("The Muxy SDK is missing. Install it with: scripts/sdk.sh install")
        }
    }

tasks.named("preBuild") {
    dependsOn(checkMuxySdk)
}
