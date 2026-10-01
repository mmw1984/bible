import java.util.Base64
import java.io.ByteArrayInputStream

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// CI (and any machine that exports these variables) signs releases with the
// persistent keystore so every published APK keeps the same signature. Without
// the variables — e.g. a plain local `flutter build apk --release` — fall back
// to the debug key exactly as before.
val keystoreEnv: Map<String, String> = System.getenv()
fun envSigningAvailable(): Boolean =
    !keystoreEnv["ANDROID_KEYSTORE_BASE64"].isNullOrBlank() &&
        !keystoreEnv["ANDROID_KEYSTORE_PASSWORD"].isNullOrBlank()

android {
    namespace = "com.marcow.bible"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        applicationId = "com.marcow.bible"
        // ML Kit's on-device GenAI Prompt API (com.google.mlkit:genai-prompt)
        // declares minSdkVersion 26, so the app floor has to match. Devices
        // without AICore are still handled at runtime: BibleAiController falls
        // back to OpenRouter when Gemini Nano reports unsupported.
        minSdk = maxOf(flutter.minSdkVersion, 26)
        targetSdk = 36
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (envSigningAvailable()) {
            create("release") {
                val decoded = Base64.getDecoder().decode(keystoreEnv["ANDROID_KEYSTORE_BASE64"])
                storeFile = File(
                    layout.buildDirectory.dir("tmp/keystore").get().asFile.apply { mkdirs() },
                    "release.jks",
                ).also { it.writeBytes(decoded) }
                storePassword = keystoreEnv["ANDROID_KEYSTORE_PASSWORD"]
                keyAlias = keystoreEnv["ANDROID_KEY_ALIAS"] ?: "bible"
                keyPassword = keystoreEnv["ANDROID_KEY_PASSWORD"]
                    ?: keystoreEnv["ANDROID_KEYSTORE_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (envSigningAvailable()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    // Gemini Nano runs fully on-device through the AICore system service: no
    // API key and no network access. This is the ML Kit GenAI Prompt API; the
    // older Google AI Edge SDK (com.google.ai.edge.aicore:aicore) is still
    // pinned at 0.0.1-exp02 and has no streaming API, so only this one is used.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    // genai-prompt only pulls kotlinx-coroutines-core, so Dispatchers.Main needs
    // the Android artifact added explicitly. Pinned to the version its POM uses.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

flutter {
    source = "../.."
}
