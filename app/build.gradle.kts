import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Signing is copied verbatim from the Flutter build (`legacy/flutter/android/app/build.gradle.kts`)
// so the native app can be installed over the Flutter app without uninstalling: same
// applicationId (`com.marcow.bible`) plus the same keystore means the same signature
// (NATIVE_PLAN.md §6 R2). CI supplies the keystore through ANDROID_KEYSTORE_* secrets, which is
// what keeps the certificate identical to every release published so far.
val keystoreEnv: Map<String, String> = System.getenv()

// The Flutter build's own alias; ANDROID_KEY_ALIAS only overrides it, and a blank secret is
// treated as "not set" so an empty env var cannot turn into an empty alias.
val DEFAULT_KEY_ALIAS = "bible"

fun envSigningAvailable(): Boolean = !keystoreEnv["ANDROID_KEYSTORE_BASE64"].isNullOrBlank() &&
    !keystoreEnv["ANDROID_KEYSTORE_PASSWORD"].isNullOrBlank()

// A missing keystore is fine locally — you get the debug key and a build you can sideload. It
// is not fine anywhere that publishes: the release below would fall through to the debug key,
// the build would still succeed, and Android would then refuse the upgrade because the
// certificate differs, which is precisely what R2 exists to prevent. The release workflow sets
// REQUIRE_RELEASE_SIGNING=true, so there the build stops instead of quietly signing wrong.
// Only the presence of the variables is checked; their values never reach a log or an exception.
if (keystoreEnv["REQUIRE_RELEASE_SIGNING"] == "true" && !envSigningAvailable()) {
    throw GradleException(
        "REQUIRE_RELEASE_SIGNING is set but ANDROID_KEYSTORE_BASE64/ANDROID_KEYSTORE_PASSWORD are " +
            "missing; refusing to sign the release with the debug key (NATIVE_PLAN.md §6 R2).",
    )
}

android {
    namespace = "com.marcow.bible"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        applicationId = "com.marcow.bible"
        // minSdk 26 is required for variable fonts (FontVariation), see NATIVE_PLAN.md §6 R3.
        minSdk = 26
        targetSdk = 36
        // Must stay above the Flutter build's 203 (pubspec `1.6.3+203`) or Play/the OS
        // refuses the in-place upgrade.
        versionCode = 300
        versionName = "2.0.0-beta"
    }

    signingConfigs {
        if (envSigningAvailable()) {
            create("release") {
                val decoded = Base64.getDecoder().decode(keystoreEnv["ANDROID_KEYSTORE_BASE64"])
                // build/ is ignored, so the decoded keystore never lands in git. It is written at
                // configuration time because System.getenv() is all this module has to go on.
                storeFile = File(
                    layout.buildDirectory.dir("tmp/keystore").get().asFile.apply { mkdirs() },
                    "release.jks",
                ).also { it.writeBytes(decoded) }
                storePassword = keystoreEnv["ANDROID_KEYSTORE_PASSWORD"]
                keyAlias = keystoreEnv["ANDROID_KEY_ALIAS"]?.takeUnless { it.isBlank() } ?: DEFAULT_KEY_ALIAS
                keyPassword = keystoreEnv["ANDROID_KEY_PASSWORD"]?.takeUnless { it.isBlank() }
                    ?: keystoreEnv["ANDROID_KEYSTORE_PASSWORD"]
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = null
            versionNameSuffix = null
        }
        release {
            // Fall back to the debug key so a local build without secrets still installs. The
            // REQUIRE_RELEASE_SIGNING guard above makes this branch unreachable in the release
            // workflow, which is the only place where picking the wrong key goes unnoticed.
            signingConfig = if (envSigningAvailable()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.all { test ->
            test.useJUnitPlatform()
            test.testLogging { events("failed") }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

hilt {
    // Hilt's AGP aggregating task relies on ScopedArtifact.POST_COMPILATION_CLASSES, which no
    // longer exists in current AGP, and it is incompatible with KSP anyway: KSP already does the
    // aggregating work for us.
    enableAggregatingTask = false
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:design-system"))
    implementation(project(":core:legacy-migration"))
    implementation(project(":core:navigation"))
    implementation(project(":core:network"))

    implementation(project(":feature:aichat"))
    implementation(project(":feature:devotion"))
    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
