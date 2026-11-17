import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mealsnap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mealsnap"
        minSdk = 31 // Pixel 9 Pro ships with Android 15+, so Android 12+ is plenty
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"

        // Pixel phones are arm64; leaving the other CPU types out keeps the APK much smaller.
        ndk { abiFilters += "arm64-v8a" }
    }

    androidResources {
        // The food model is memory-mapped straight from the APK, so it must not be compressed.
        noCompress += "tflite"
    }

    // A fixed debug keystore (committed on purpose, it is the standard public "android"
    // debug key) so every build, local or CI, is signed identically and can be installed
    // over the previous one without losing your meal log.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Needed so Robolectric can render the real Compose screens in the screenshot tests
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Optional: run Robolectric from a folder of pre-downloaded android-all jars (offline machines):
//   ./gradlew testDebugUnitTest -ProbolectricDependencyDir=/path/to/jars
tasks.withType<Test>().configureEach {
    providers.gradleProperty("robolectricDependencyDir").orNull?.let { dir ->
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", dir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // On-device food recognition (no account, key or internet needed)
    implementation(libs.litert)
    implementation(libs.mlkit.genai.prompt) // optional Gemini Nano, used only where the phone supports it

    // Reads steps, heart rate, sleep and weight that a tracker such as the Fitbit Air shares with the phone
    implementation(libs.androidx.health.connect)

    // Optional: Claude, used only if the person adds their own API key in Settings
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
