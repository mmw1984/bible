plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.marcow.bible.core.datastore.proto"
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
    // `api` because every generated message extends a `com.google.protobuf` type, so consumers
    // that call `Settings.parseFrom` need the runtime on their compile classpath too.
    api(libs.protobuf.javalite)
}
