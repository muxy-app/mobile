import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

ktlint {
    version.set(libs.versions.ktlint)
}

dependencies {
    compileOnly(libs.ktlint.rule.engine.core)
    compileOnly(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4j.nop)
    ktlintRuleset(project(":ktlint-rules")) { isTransitive = false }
}
