plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Static analysis runs for every module, so a single `./gradlew ktlintCheck detekt`
// covers the whole repository (CI runs exactly those two tasks).
// Per-rule behaviour lives in `.editorconfig` (ktlint) and `config/detekt/detekt.yml`.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("$rootDir/config/detekt/detekt.yml"))
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
