import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.marcow.bible.core.datastore"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
    }

    // `InMemorySettingsDataStore` lives here rather than in `src/test` so the feature modules that
    // write settings can build the same DataStore the file-backed one parses.
    testFixtures {
        enable = true
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
    // The proto message is generated in its own module: KSP analyses this module's Kotlin with
    // the Analysis API, which cannot see the Java sources the protobuf plugin generates into this
    // module, so `DataStore<Settings>` in a @Provides signature failed to resolve. Compiling the
    // message in core:datastore-proto puts real classes on the KSP classpath instead, and the
    // `com.marcow.bible.core.datastore.proto.Settings` name every caller imports stays the same.
    api(project(":core:datastore-proto"))
    api(project(":core:model"))
    implementation(project(":core:common"))
    // Proto DataStore: `datastore-core` for DataStore/Serializer, `datastore` for the Android
    // file-backed factory. The Preferences flavour is only needed to read the legacy
    // SharedPreferences file, which core:legacy-migration owns.
    api(libs.androidx.datastore.core)
    api(libs.protobuf.javalite)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}
