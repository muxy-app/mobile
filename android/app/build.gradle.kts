plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

val signingEnvironment =
    listOf(
        "ANDROID_SIGNING_KEY_PATH",
        "ANDROID_KEY_STORE_PASSWORD",
        "ANDROID_KEY_ALIAS",
        "ANDROID_KEY_PASSWORD",
    ).associateWith { providers.environmentVariable(it).orNull.orEmpty() }

val missingSigningEnvironment = signingEnvironment.filterValues(String::isBlank).keys

check(missingSigningEnvironment.isEmpty() || missingSigningEnvironment.size == signingEnvironment.size) {
    "Release signing needs all of ${signingEnvironment.keys.joinToString()}. " +
        "Missing: ${missingSigningEnvironment.joinToString()}"
}

android {
    namespace = "com.muxy.app"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.muxy.app"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("1.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (missingSigningEnvironment.isEmpty()) {
            create("release") {
                storeFile = rootDir.parentFile.resolve(signingEnvironment.getValue("ANDROID_SIGNING_KEY_PATH"))
                storePassword = signingEnvironment.getValue("ANDROID_KEY_STORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("ANDROID_KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "boolean",
                "BILLING_ENFORCED",
                providers
                    .gradleProperty("muxyBillingEnforced")
                    .orNull
                    .toBoolean()
                    .toString(),
            )
            buildConfigField(
                "long",
                "TRIAL_MINUTES",
                "${providers
                    .gradleProperty("muxyTrialMinutes")
                    .orNull
                    ?.toLongOrNull()
                    ?.coerceIn(0, 4320) ?: 0}L",
            )
        }
        release {
            buildConfigField("boolean", "BILLING_ENFORCED", "true")
            buildConfigField("long", "TRIAL_MINUTES", "0L")
            ndk.debugSymbolLevel = "FULL"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "ttf"
    }

    packaging {
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
        disable += setOf("OldTargetApi", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
}

dependencies {
    implementation(project(":MuxyMobileSDK"))
    implementation(project(":terminal-emulator"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.billing)
    implementation(libs.sshj)
    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.slf4j.nop)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    ktlintRuleset(project(":ktlint-rules")) { isTransitive = false }
}
