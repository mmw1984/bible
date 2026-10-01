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
        minSdk = flutter.minSdkVersion
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

flutter {
    source = "../.."
}
