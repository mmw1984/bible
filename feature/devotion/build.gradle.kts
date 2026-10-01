import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.marcow.bible.feature.devotion"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
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
    }

    testOptions {
        unitTests.all { test ->
            test.useJUnitPlatform()
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
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    api(project(":core:design-system"))
    api(project(":core:navigation"))
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    // The interface language, so a date and a copied article change with the rest of the screen.
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.android)
    // The cache stores raw WordPress post JSON, the same shape the Flutter cache stored in
    // SharedPreferences; reading it back is the tree API, not a serializer per projection.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // No media dependency: the SoundCloud embed is a page in a `<iframe>`, so it plays in a WebView
    // exactly as it did in Dart. `media3-exoplayer` would only reach it through its HLS manifest,
    // which drops the track artwork and the share sheet the widget is built around. The YouTube
    // player is the same story — a WebView, because its controls are the point.

    testImplementation(platform(libs.junit.bom))

    testImplementation(libs.junit.jupiter)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:datastore")))
}
