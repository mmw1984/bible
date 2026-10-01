import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.marcow.bible.feature.search"
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
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    // The sheet's prompts are asked in the language the user reads in, which is a setting.
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.android)
    // `jsonObjectOrNull` decodes the references answer directly, rather than through a serializer
    // that would have to survive the model's prose around the JSON.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    // The same in-memory DataStore `core/datastore` tests with, so a search can be asked for the
    // language the user reads in without a file.
    testImplementation(testFixtures(project(":core:datastore")))
    // `InMemorySettingsDataStore` takes the generated message, so the test builds one.
    testImplementation(project(":core:datastore-proto"))
}
