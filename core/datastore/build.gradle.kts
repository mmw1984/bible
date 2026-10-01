import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.protobuf)
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

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            // javalite is enough: the settings message has no `Any`, no extensions and no maps,
            // and it keeps the generated `Settings` class small.
            // `maybeCreate` rather than `named`: the plugin adds the `java` builtin itself while
            // the task is being configured, and `all()` can run before that happens.
            task.builtins.maybeCreate("java").option("lite")
        }
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
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
